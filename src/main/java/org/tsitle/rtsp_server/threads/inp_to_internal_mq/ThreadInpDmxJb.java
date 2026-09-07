package org.tsitle.rtsp_server.threads.inp_to_internal_mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.DpConstants;
import org.tsitle.lib_ffmpeg.*;
import org.tsitle.lib_ffmpeg.demux.*;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegDecoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegEncoderNotFoundException;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.tsitle.lib_ffmpeg.tc.FfmpegTcAvPacketList;
import org.tsitle.lib_ffmpeg.tc.FfmpegTcParamsInpAudio;
import org.tsitle.lib_ffmpeg.tc.FfmpegTcSettingsOutAudio;
import org.tsitle.lib_ffmpeg.tc.FfmpegTranscoder;
import org.tsitle.lib_mq.common.MqInternalPub;
import org.tsitle.lib_mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_mq.common.mqdata.MqPacketAv;
import org.tsitle.lib_mq.common.mqdata.MqPacketCodec;
import org.tsitle.lib_mq.exceptions.MqException;
import org.tsitle.lib_xrtxp.avdata.codec_a_opus.AudioOpusInfo;
import org.tsitle.lib_xrtxp.avdata.codec_a_opus.AudioOpusParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.*;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.rtsp_server.availstreams.AsCodecSettingsChangedFromDmxJbInterface;
import org.tsitle.rtsp_server.helpers.FfCodecToMqCodecHelper;
import org.tsitle.rtsp_server.helpers.CfgTcCodecToFfCodecHelper;
import org.tsitle.rtsp_server.threads.AdaptiveScheduler;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.RunnableBase;
import org.tsitle.rtsp_server.threads.rtp.RtpConstants;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class ThreadInpDmxJb extends RunnableBase {

	private static class FfDmxPktCacheEntry {
		final FfmpegAvPktBasics ffPktObj = new FfmpegAvPktBasics();
		@NonNull TimestampMonotonic ffPktTimestamp = TimestampMonotonic.ofEmpty();
	}

	private static class DataPerMq {
		final FfmpegTcSettingsOutAudio ffTcSettingsOutAudio = new FfmpegTcSettingsOutAudio();
		final MqCodecSettings mqCodecSettings = new MqCodecSettings();
		double virtualFps = -1.0;
		@NonNull String metadataTags = "";
		boolean hasMetadataTagsChanged = false;
		long msgNr = 1;
		int counter = 0;

		final AudioOpusParser parserOpus = new AudioOpusParser();
	}

	private static class DynamicObjs {
		@Nullable MqInternalPub mqInternalPub = null;
		@Nullable FfmpegDemuxer ffDemuxerObj = null;
		@Nullable FfmpegTranscoder ffmpegTcObj = null;

		void closeMq() {
			if (mqInternalPub != null) { mqInternalPub.close(); mqInternalPub = null; }
		}
		private void closeDemuxer() {
			if (ffDemuxerObj != null) { ffDemuxerObj.close(); ffDemuxerObj = null; }
		}
		private void closeTranscoder() {
			if (ffmpegTcObj != null) { ffmpegTcObj.close(); ffmpegTcObj = null; }
		}
	}

	private static class InputFileStuff {
		@Nullable FileFolderWatcher ffwPtr = null;
		final Set<@NonNull String> inputFilePathsAsSet = new HashSet<>();
		final List<@NonNull String> inputFilePathsAsList = new ArrayList<>();
		final Set<@NonNull String> blacklistedFilePaths = new HashSet<>();
		final Set<@NonNull Integer> alreadyPlayedFpIdx = new HashSet<>();
		@NonNull String currentFilePath = "";
		int lastPlayedFpIdx = -1;
	}

	private static class Bandwidth {
		long bytesSent = 0L;
		final TimestampEpoch tsLastChecked = TimestampEpoch.ofEmpty();
	}

	private static class RuntimeData {
		final FfDmxPktCacheEntry ffDmxPktCacheEntry = new FfDmxPktCacheEntry();

		final DataPerMq dpm = new DataPerMq();
		final DynamicObjs dyn = new DynamicObjs();
		final InputFileStuff ifs = new InputFileStuff();

		boolean needToUpdateTcDecoder = false;
		boolean haveInitTcDependentObjs = false;

		final TimestampEpoch tsEpochStart = TimestampEpoch.ofEmpty();
		final TimestampEpoch tsEpochLast = TimestampEpoch.ofEmpty();

		final Bandwidth bw = new Bandwidth();
	}

	private static final boolean BW_INFO_OUTPUT_ENABLED = false;
	private static final long BW_INFO_OUTPUT_INTV_MS = 5_000L;
	private static final Set<@NonNull String> ALLOWED_INPUT_FILE_EXTS = Set.of(
			"aac", "ac3", "eac3", "flac", "m4a", "mp2", "mp3", "ogg", "opus", "wav"
		);

	private final @NonNull ProUri inputSourceDmxJbUri;
	private final @NonNull AsCodecSettingsChangedFromDmxJbInterface codecSettingsChangedInterface;
	private final @NonNull RtspProtoIdInputSource idInputSource = RtspProtoIdInputSource.ofEmpty();
	private final @NonNull RtspProtoIdEsSource idEsSource = RtspProtoIdEsSource.ofEmpty();

	private final String threadName;
	private final CancelToken localCancelToken = new CancelToken();
	private final @NonNull AdaptiveScheduler adaptiveScheduler;
	private final RuntimeData rd = new RuntimeData();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param inputSourceDmxJbUri URI of the Input Source
	 * @param tcCodecSettings Transcoder Codec settings for the output
	 * @param codecSettingsChangedInterface 'Codec settings changed' interface
	 * @param idInputSource Input Source identifier
	 * @param idEsSource Elementary-Stream Source identifier
	 */
	public ThreadInpDmxJb(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull ProUri inputSourceDmxJbUri,
				RtspProtoEsSourceExpandedInfo.@NonNull TcSettingsAudio tcCodecSettings,
				@NonNull AsCodecSettingsChangedFromDmxJbInterface codecSettingsChangedInterface,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdEsSource idEsSource
			) {
		super(logMsgInterface, cancelToken);

		final String FNC_NAME = getClass().getSimpleName() + ".ctor()";

		//
		this.inputSourceDmxJbUri = inputSourceDmxJbUri.clone();

		this.rd.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec =
				CfgTcCodecToFfCodecHelper.convertCfgTcCodecToFfmpegCodec(tcCodecSettings.codecStr());
		this.rd.dpm.ffTcSettingsOutAudio.cfgBitRateKbps = approximateBr(tcCodecSettings.audioBitRateKbps());
		this.rd.dpm.ffTcSettingsOutAudio.cfgSampleRateFixed = tcCodecSettings.audioSampleRate();
		this.rd.dpm.ffTcSettingsOutAudio.cfgChannelCm = (tcCodecSettings.audioChannelCount() == 1 ?
				FfmpegTcSettingsOutAudio.ChannelConversionMode.DOWNMIX_MONO
				: FfmpegTcSettingsOutAudio.ChannelConversionMode.FIXED_STEREO
			);

		this.codecSettingsChangedInterface = codecSettingsChangedInterface;
		if (idEsSource.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": idEsSource must be set");
		}
		this.idInputSource.copyFrom(idInputSource);
		this.idEsSource.copyFrom(idEsSource);

		//
		this.threadName = String.format("DMXJB#is%s", computeIsIdForThreadName(idInputSource));

		//
		this.adaptiveScheduler = new AdaptiveScheduler(logMsgInterface, -1.0);

		// remaining transcoder settings
		if (FfCodecToMqCodecHelper.convertFfToMqCodec(this.rd.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec).isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": " +
					"TC Codec not supported: " + this.rd.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec);
		}
		if (this.rd.dpm.ffTcSettingsOutAudio.cfgSampleRateFixed == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException(FNC_NAME + ": TC Sample rate must be set");
		}
		this.rd.dpm.ffTcSettingsOutAudio.cfgOutputModeAac = FfmpegPktConvModeAac.WITH_ADTS;
		this.rd.dpm.ffTcSettingsOutAudio.cfgOutputModeOpus = FfmpegTcSettingsOutAudio.OutputModeOpus.RTP;
		this.rd.dpm.ffTcSettingsOutAudio.cfgSrCm = FfmpegTcSettingsOutAudio.SampleRateConversionMode.FIXED;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		Thread.currentThread().setName(threadName);

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		//
		try (FileFolderWatcher localAffl = new FileFolderWatcher(
					Objects.requireNonNull(logMsgInterface),
					localCancelToken,
				inputSourceDmxJbUri,
					ALLOWED_INPUT_FILE_EXTS
				)) {
			rd.ifs.ffwPtr = localAffl;

			//
			while (! (hasBeenRequestedToStop() || localCancelToken.cancelled)) {
				if (! mainLoop()) {
					break;
				}
				//noinspection BusyWait
				Thread.sleep(1);
			}
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException caught");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			rd.ifs.ffwPtr = null;
			//
			rd.dyn.closeMq();
			rd.dyn.closeDemuxer();
			rd.dyn.closeTranscoder();
			//
			isRunning.set(false);
			//
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	public void stopThread() {
		localCancelToken.cancelled = true;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop() throws MqException, InterruptedException {
		if (! mainLoop_acquireInput()) {
			return false;
		}

		Optional<FfmpegTcAvPacketList> tmpOptAvPktListPtr = mainLoop_acquireOutput();
		if (tmpOptAvPktListPtr.isEmpty()) {
			return false;  // error
		}
		if (! tmpOptAvPktListPtr.get().isEmpty()) {
			if (! rd.haveInitTcDependentObjs) {
				mainLoop_initTcDependentObjs(tmpOptAvPktListPtr.get().iterator().next());
			}

			//
			if (rd.dpm.hasMetadataTagsChanged) {
				codecSettingsChangedInterface.onFileTagsChangedFromDmxJb(idInputSource, rd.dpm.metadataTags);
				rd.dpm.hasMetadataTagsChanged = false;
			}

			//
			for (FfmpegAvPktBasics tcAvPkt : tmpOptAvPktListPtr.get()) {
				adaptiveScheduler.waitForNextFrame();
				sendAvPktToMq(tcAvPkt);
			}
		}

		//
		if (rd.needToUpdateTcDecoder) {
			rd.needToUpdateTcDecoder = false;
			if (! rd.tsEpochLast.isEmpty()) {
				rd.tsEpochStart.copyFrom(rd.tsEpochLast);
			}
		}
		return true;
	}

	private boolean mainLoop_acquireInput() {
		do {
			if (! readNextAvPktFromDemuxer()) {
				return false;
			}

			if (rd.dyn.ffmpegTcObj == null || rd.needToUpdateTcDecoder) {
				FfmpegDmxSubStreamInfoAudio tmpSsInfoAud = new FfmpegDmxSubStreamInfoAudio();
				if (! checkInputCodecInfo(tmpSsInfoAud)) {
					blacklistFilePath(rd.ifs.currentFilePath);
					continue;
				}
				//
				updateTranscoder(tmpSsInfoAud);
				//
				updateTrackMetadata(tmpSsInfoAud.metaMap);
			}
			break;
		} while (true);
		return true;
	}

	private Optional<FfmpegTcAvPacketList> mainLoop_acquireOutput() {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop_acquireOutput()";

		if (rd.dyn.ffmpegTcObj == null) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder object not initialized");
		}

		//
		FfmpegTcAvPacketList tcAvPktListPtr;
		try {
			tcAvPktListPtr = rd.dyn.ffmpegTcObj.transcodePacketFromBuffer(rd.ffDmxPktCacheEntry.ffPktObj);
		} catch (FfmpegDecoderNotFoundException e) {
			logError(FNC_NAME, "FfmpegDecoderNotFoundException caught: " + e.getMessage());
			return Optional.empty();
		} catch (FfmpegGenericException e) {
			logError(FNC_NAME, "FfmpegGenericException caught: " + e.getMessage());
			return Optional.empty();
		} catch (FfmpegEncoderNotFoundException e) {
			logError(FNC_NAME, "FfmpegEncoderNotFoundException caught: " + e.getMessage());
			return Optional.empty();
		}
		return Optional.of(tcAvPktListPtr);
	}

	private void mainLoop_initTcDependentObjs(@NonNull FfmpegAvPktBasics firstAvPkt) throws MqException {
		if (rd.haveInitTcDependentObjs) {
			return;
		}

		if (rd.dpm.mqCodecSettings.codec == null) {
			createMqCodecSettings(firstAvPkt);
		}
		if (rd.dyn.mqInternalPub == null) {
			rd.dyn.mqInternalPub = new MqInternalPub(logMsgInterface, idEsSource);
			rd.dyn.mqInternalPub.connectToMq();
		}
		if (adaptiveScheduler.getSendIntervalNs() < 0.001 &&
				rd.dpm.mqCodecSettings.audioSamplesPerFrame != null &&
				rd.dpm.mqCodecSettings.audioSamplerate != null) {
			double virtualFps = computeVirtualFps(
					rd.dpm.mqCodecSettings.audioSamplesPerFrame,
					rd.dpm.mqCodecSettings.audioSamplerate
				);
			adaptiveScheduler.setFps(virtualFps);
		}

		rd.haveInitTcDependentObjs = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean readNextAvPktFromDemuxer() {
		final String FNC_NAME = getClass().getSimpleName() + ".readNextAvPktFromDemuxer()";

		boolean haveEof = false;
		boolean isFirstFrame = false;
		do {
			if (haveEof || rd.dyn.ffDemuxerObj == null) {
				if (! findAndLoadNextFile()) {
					return false;
				}
				isFirstFrame = true;
			}
			try {
				FfmpegDemuxer.ReadResult tmpRr = rd.dyn.ffDemuxerObj.readNextAvPacket(rd.ffDmxPktCacheEntry.ffPktObj);
				if (tmpRr == FfmpegDemuxer.ReadResult.RR_EOF) {
					if (isFirstFrame) {
						return false;  // empty file?
					}
					haveEof = true;
					rd.needToUpdateTcDecoder = (rd.dyn.ffmpegTcObj != null);
					continue;
				}
				break;
			} catch (FfmpegGenericException e) {
				logWarn(FNC_NAME, "FfmpegGenericException caught for fn='" + rd.ifs.currentFilePath + "': " + e.getMessage());
				blacklistFilePath(rd.ifs.currentFilePath);
				haveEof = true;
			}
		} while (true);

		//
		if (rd.tsEpochStart.isEmpty()) {
			rd.tsEpochStart.copyFrom(TimestampEpoch.ofNow());
		}

		//
		rd.ffDmxPktCacheEntry.ffPktTimestamp = TimestampMonotonic.ofNsUnsigned64bit(
				(long)(rd.ffDmxPktCacheEntry.ffPktObj.ptsUnitsToSeconds() * 1_000_000_000.0)
			);

		return true;
	}

	private void sendAvPktToMq(@NonNull FfmpegAvPktBasics tcAvPkt) throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendAvPktToMq()";

		if (rd.dyn.mqInternalPub == null) {
			throw new IllegalStateException(FNC_NAME + ": mqInternalPub not initialized");
		}
		if (rd.dpm.mqCodecSettings.codec == null ||
				rd.dpm.mqCodecSettings.audioSamplerate == null || rd.dpm.mqCodecSettings.audioChannels == null ||
				rd.dpm.mqCodecSettings.audioSamplesPerFrame == null) {
			throw new IllegalStateException(FNC_NAME + ": mqCodecSettings not initialized");
		}

		//
		boolean isVirtFpsOk = updateMqCodecSettings_spf(tcAvPkt, false);

		//
		double tmpPtsSeconds = tcAvPkt.ptsUnitsToSeconds();
		if (tmpPtsSeconds < 0.0) {
			return;
		}
		TimestampEpoch tmpTsEpoch = TimestampEpoch.ofEpochNsUnsigned64bit(
				rd.tsEpochStart.getEpochNsUnsigned64bit().orElseThrow() +
						(long)(tmpPtsSeconds * 1_000_000_000.0)
			);
		rd.tsEpochLast.copyFrom(tmpTsEpoch);
		rd.tsEpochLast.setEpochNsUnsigned64bit(
				tmpTsEpoch.getEpochNsUnsigned64bit().orElseThrow() +
				(long)(computeDurationSeconds((int)tcAvPkt.duration, rd.dpm.mqCodecSettings.audioSamplerate) * 1_000_000_000L)
			);

		//
		if (! isVirtFpsOk) {
			return;
		}

		//
		MqPacketAv mqPkt = new MqPacketAv(
				rd.dpm.msgNr++,
				rd.dpm.mqCodecSettings.codec,
				false,
				tmpTsEpoch,
				rd.dpm.counter++,
				false,
				ImageDimensions.ofEmpty(),
				FrameRateEnum.UNKNOWN,
				0,
				rd.dpm.mqCodecSettings.audioSamplerate,
				rd.dpm.mqCodecSettings.audioChannels,
				rd.dpm.mqCodecSettings.audioSamplesPerFrame,
				(byte)0x00,  // CRC8, 0x00 ^= do not validate checksum
				tcAvPkt.pktBe
			);
		rd.dyn.mqInternalPub.sendMessageAv(mqPkt);

		//
		rd.bw.bytesSent += tcAvPkt.pktBe.getUsed();
		if (rd.bw.tsLastChecked.isEmpty()) {
			rd.bw.tsLastChecked.setToNow();
		} else {
			long deltaMs = ((TimestampEpoch.ofNow().getEpochNsUnsigned64bit().orElseThrow() -
					rd.bw.tsLastChecked.getEpochNsUnsigned64bit().orElseThrow()) / 1_000_000L);
			if (deltaMs > BW_INFO_OUTPUT_INTV_MS) {
				if (BW_INFO_OUTPUT_ENABLED) {
					double bytesPerSec = ((double)rd.bw.bytesSent / (double)deltaMs) * 1_000.0;
					logDebug(FNC_NAME,
							String.format("bandwidth: %.2f kB/s", bytesPerSec / 1_024.0).replace(",", ".")
						);
				}
				rd.bw.tsLastChecked.setToNow();
				rd.bw.bytesSent = 0L;
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void createMqCodecSettings(@NonNull FfmpegAvPktBasics firstAvPkt) {
		final String FNC_NAME = getClass().getSimpleName() + ".createMqCodecSettings()";

		if (rd.dyn.ffmpegTcObj == null) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder object not initialized");
		}
		FfmpegCdcParamsAudio tmpCdcParams = new FfmpegCdcParamsAudio();
		rd.dyn.ffmpegTcObj.getCdcParamsAudio(tmpCdcParams);

		//
		rd.dpm.mqCodecSettings.codec =
				FfCodecToMqCodecHelper.convertFfToMqCodec(rd.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec).orElseThrow();
		rd.dpm.mqCodecSettings.audioSamplerate = rd.dpm.ffTcSettingsOutAudio.cfgSampleRateFixed;
		rd.dpm.mqCodecSettings.audioChannels = (byte)tmpCdcParams.channelCount;
		updateMqCodecSettings_spf(firstAvPkt, true);

		String metadataHex = tmpCdcParams.extradataHex.getEd();
		codecSettingsChangedInterface.onCodecMetadataFromDmxJb(idEsSource, metadataHex);
	}

	private boolean updateMqCodecSettings_spf(@NonNull FfmpegAvPktBasics avPkt, boolean forceUpdateUpstream) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateMqCodecSettings_spf()";

		if (rd.dyn.ffmpegTcObj == null) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder object not initialized");
		}
		if (rd.dpm.mqCodecSettings.codec == null || rd.dpm.mqCodecSettings.audioChannels == null ||
				rd.dpm.mqCodecSettings.audioSamplerate == null) {
			throw new IllegalStateException(FNC_NAME + ": MQ Codec Settings not initialized");
		}

		final int lastSpf = Objects.requireNonNullElse(rd.dpm.mqCodecSettings.audioSamplesPerFrame, -1);
		int nextSpf;
		final double lastVirtualFps = rd.dpm.virtualFps;

		if (rd.dpm.mqCodecSettings.codec == MqPacketCodec.AACLC) {
			if (avPkt.duration != DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1) {
				// this can happen at the end of an input file
				return false;
			}
			nextSpf = (int)avPkt.duration;
		} else if (rd.dpm.mqCodecSettings.codec == MqPacketCodec.OPUS) {
			AudioOpusInfo tmpInf;
			try {
				tmpInf = rd.dpm.parserOpus.parseOpusData(new BufferView(avPkt.pktBe));
			} catch (AvInvalidCodecDataException e) {
				logError(FNC_NAME, "Invalid Opus data: " + e.getMessage());
				return false;
			}
			nextSpf = tmpInf.samplesPerChannelInAudioData;
		} else if (avPkt.duration < 1) {
			if (! rd.dpm.mqCodecSettings.codec.isPcmAudio()) {
				throw new IllegalStateException(FNC_NAME + ": Transcoder did not provide samplesPerFrame");
			}
			int tmpBytes = avPkt.pktBe.getUsed();
			tmpBytes /= rd.dpm.mqCodecSettings.audioChannels;
			tmpBytes /= (rd.dpm.mqCodecSettings.codec == MqPacketCodec.LPCM16S ? 2 : 1);
			nextSpf = tmpBytes;
		} else {
			nextSpf = (int)avPkt.duration;
		}
		final double tmpVirtFps = computeVirtualFps(
				nextSpf,
				rd.dpm.mqCodecSettings.audioSamplerate
			);
		if (tmpVirtFps < 1.0 || tmpVirtFps > RtpConstants.RTP_MAX_FRAMES_PER_SECOND) {
			logError(FNC_NAME, "Invalid virtual FPS: " + tmpVirtFps + " " +
					"(codec=" + rd.dpm.mqCodecSettings.codec + ", inpFn=" + rd.ifs.currentFilePath + ")");
			return false;
		}
		rd.dpm.mqCodecSettings.audioSamplesPerFrame = nextSpf;
		rd.dpm.virtualFps = tmpVirtFps;
		if (forceUpdateUpstream ||
				lastSpf != rd.dpm.mqCodecSettings.audioSamplesPerFrame || lastVirtualFps != rd.dpm.virtualFps) {
			if (! forceUpdateUpstream) {
				logDebug(FNC_NAME, "update virtual FPS: " + tmpVirtFps + " " +
						", SPF: " + rd.dpm.mqCodecSettings.audioSamplesPerFrame + " " +
						"(codec=" + rd.dpm.mqCodecSettings.codec + ")");
			}
			codecSettingsChangedInterface.onCodecSettingsChangedFromDmxJb(idEsSource, rd.dpm.mqCodecSettings);
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean findAndLoadNextFile() {
		final String FNC_NAME = getClass().getSimpleName() + ".findAndLoadNextFile()";

		rd.dyn.closeDemuxer();

		if (rd.ifs.ffwPtr == null) {
			return false;
		}
		if (rd.ifs.ffwPtr.haveMatchingFilesChanged()) {
			rd.ifs.ffwPtr.getMatchingFilesAsStrings(rd.ifs.blacklistedFilePaths, rd.ifs.inputFilePathsAsSet);
			rd.ifs.inputFilePathsAsList.clear();
			rd.ifs.inputFilePathsAsList.addAll(rd.ifs.inputFilePathsAsSet);
			rd.ifs.alreadyPlayedFpIdx.clear();
			rd.ifs.lastPlayedFpIdx = -1;
		}

		do {
			if (rd.ifs.inputFilePathsAsList.isEmpty()) {
				logError(FNC_NAME, "no input files available");
				return false;
			}
			if (rd.ifs.alreadyPlayedFpIdx.size() >= rd.ifs.inputFilePathsAsList.size()) {
				rd.ifs.alreadyPlayedFpIdx.clear();
				if (rd.ifs.inputFilePathsAsList.size() > 1) {
					rd.ifs.alreadyPlayedFpIdx.add(rd.ifs.lastPlayedFpIdx);
				}
			}

			// pick an index in the range [0, rd.ifs.inputFilePaths.size())
			int tmpNextIx = (int)(Math.random() * rd.ifs.inputFilePathsAsList.size());
			if (rd.ifs.alreadyPlayedFpIdx.contains(tmpNextIx)) {
				continue;
			}
			String tmpAbsFn = rd.ifs.inputFilePathsAsList.get(tmpNextIx);
			//
			Path tmpPathObj = Paths.get(tmpAbsFn);
			if (! tmpPathObj.toFile().exists()) {
				blacklistFilePath(tmpAbsFn);
				continue;
			}
			rd.ifs.currentFilePath = tmpAbsFn;
			rd.ifs.alreadyPlayedFpIdx.add(tmpNextIx);
			rd.ifs.lastPlayedFpIdx = tmpNextIx;
			//
			initDemuxer();
			break;
		} while (true);

		logDebug(FNC_NAME, "Input file: '" + rd.ifs.currentFilePath + "'");
		return true;
	}

	private void blacklistFilePath(@NonNull String fp) {
		int tmpFpIx = -1;
		for (int tmpSearchIx = 0; tmpSearchIx < rd.ifs.inputFilePathsAsList.size(); tmpSearchIx++) {
			if (rd.ifs.inputFilePathsAsList.get(tmpSearchIx).equals(fp)) {
				tmpFpIx = tmpSearchIx;
				break;
			}
		}
		if (tmpFpIx >= 0) {
			rd.ifs.alreadyPlayedFpIdx.remove(tmpFpIx);
			if (rd.ifs.lastPlayedFpIdx == tmpFpIx) {
				rd.ifs.lastPlayedFpIdx = -1;
			} else if (rd.ifs.lastPlayedFpIdx > tmpFpIx) {
				--rd.ifs.lastPlayedFpIdx;
			}
		}

		rd.ifs.inputFilePathsAsSet.remove(fp);
		rd.ifs.inputFilePathsAsList.remove(fp);
		rd.ifs.blacklistedFilePaths.add(fp);
	}

	private boolean checkInputCodecInfo(@NonNull FfmpegDmxSubStreamInfoAudio ssInfoAud) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkInputCodecInfo()";

		if (rd.dyn.ffDemuxerObj == null) {
			throw new IllegalStateException(FNC_NAME + ": Demuxer object not initialized");
		}
		Optional<FfmpegDmxSubStreamInfoAudio> tmpSsInfo = rd.dyn.ffDemuxerObj.getFfAvSubStreamInfoAudio();

		if (tmpSsInfo.isEmpty()) {
			logWarn(FNC_NAME, "no audio sub-stream found");
			return false;
		}
		ssInfoAud.copyFrom(tmpSsInfo.get());
		return true;
	}

	private void initDemuxer() {
		rd.dyn.closeDemuxer();

		if (rd.ifs.currentFilePath.isBlank()) {
			return;
		}

		FfmpegDmxSettingsDemux dmxSettingsDemux = new FfmpegDmxSettingsDemux();
		dmxSettingsDemux.cfgOutputModeAac = FfmpegPktConvModeAac.WITH_ADTS;
		dmxSettingsDemux.cfgAllowOnlySpecificCodecsVideo = true;
		dmxSettingsDemux.cfgAllowOnlySpecificCodecsAudio = true;
		dmxSettingsDemux.cfgAllowedCodecsAudio.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO);
		dmxSettingsDemux.cfgAllowedCodecsAudio.add(FfmpegCodec.A_PCM_S24LE);
		dmxSettingsDemux.cfgAllowedCodecsAudio.add(FfmpegCodec.A_PCM_S32LE);

		rd.dyn.ffDemuxerObj = FfmpegDemuxer.createForDemuxingOnly(
				null,
				rd.ifs.currentFilePath,
				dmxSettingsDemux
			);
	}

	private void initTranscoder(@NonNull FfmpegDmxSubStreamInfoAudio ssInfoAud) {
		rd.dyn.closeTranscoder();

		FfmpegTcParamsInpAudio sourceParamsAudio = new FfmpegTcParamsInpAudio();
		sourceParamsAudio.ffmpegCodec = ssInfoAud.ffmpegCodec;
		sourceParamsAudio.timeBase.copyFrom(ssInfoAud.timeBasePts);
		sourceParamsAudio.sampleRate = ssInfoAud.sampleRate;
		sourceParamsAudio.channelCount = ssInfoAud.channelCount;
		sourceParamsAudio.extradataHex.copyFrom(ssInfoAud.extradataHex);

		rd.dyn.ffmpegTcObj = FfmpegTranscoder.createForAudioOnly(
				null,
				sourceParamsAudio,
				rd.dpm.ffTcSettingsOutAudio
			);
	}

	private void updateTranscoder(@NonNull FfmpegDmxSubStreamInfoAudio ssInfoAud) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateTranscoder()";

		if (rd.dyn.ffmpegTcObj == null) {
			initTranscoder(ssInfoAud);
			return;
		}
		FfmpegTcParamsInpAudio sourceParamsAudio = new FfmpegTcParamsInpAudio();
		sourceParamsAudio.ffmpegCodec = ssInfoAud.ffmpegCodec;
		sourceParamsAudio.timeBase.copyFrom(ssInfoAud.timeBasePts);
		sourceParamsAudio.sampleRate = ssInfoAud.sampleRate;
		sourceParamsAudio.channelCount = ssInfoAud.channelCount;
		sourceParamsAudio.extradataHex.copyFrom(ssInfoAud.extradataHex);

		try {
			rd.dyn.ffmpegTcObj.updateAudioDecoderFromBuffer(sourceParamsAudio);
		} catch (FfmpegDecoderNotFoundException e) {
			logError(FNC_NAME, "FfmpegDecoderNotFoundException caught: " + e.getMessage());
		} catch (FfmpegGenericException e) {
			logError(FNC_NAME, "FfmpegGenericException caught: " + e.getMessage());
		}
	}

	private static double computeDurationSeconds(int samplesPerFrame, @NonNull SampleRateEnum sr) {
		if (samplesPerFrame < 1 || sr == SampleRateEnum.UNKNOWN) {
			return -1.0;
		}
		return (double)samplesPerFrame / (double)sr.getSrHz();
	}

	private static double computeVirtualFps(int samplesPerFrame, @NonNull SampleRateEnum sr) {
		double frameIntv = computeDurationSeconds(samplesPerFrame, sr);
		return (1.0 / frameIntv);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void updateTrackMetadata(@NonNull Map<@NonNull String, @NonNull String> metaMap) {
		StringBuilder sb = new StringBuilder();
		for (String tag : metaMap.keySet()) {
			String tmpVal = metaMap.get(tag);
			if (tag.length() > 999 || tmpVal.isBlank() || tmpVal.length() > 999) {
				continue;
			}
			sb
					.append(String.format("%03d", tag.length()))
					.append(tag)
					.append(String.format("%03d", tmpVal.length()))
					.append(tmpVal);
		}
		String tmpOut = sb.toString();
		rd.dpm.hasMetadataTagsChanged = (! tmpOut.equals(rd.dpm.metadataTags));
		rd.dpm.metadataTags = tmpOut;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull FfmpegAudioBitRate approximateBr(int brKbps) {
		FfmpegAudioBitRate closestEn = FfmpegAudioBitRate.UNKNOWN;
		int closestDiff = Integer.MAX_VALUE;
		for (FfmpegAudioBitRate tmpEn : FfmpegAudioBitRate.values()) {
			int curDiff = Math.abs(tmpEn.getBrInt() - brKbps);
			if (curDiff < closestDiff) {
				closestDiff = curDiff;
				closestEn = tmpEn;
			}
		}
		if (closestEn != FfmpegAudioBitRate.UNKNOWN) {
			return closestEn;
		}
		return FfmpegAudioBitRate.ABR_008;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String computeIsIdForThreadName(@NonNull RtspProtoIdInputSource idInputSource) {
		return "JB" + HashMd5Helper.hashOfString(
						idInputSource.getIdStr().orElse("-unset-"),
						false
				).substring(0, 8);
	}

}

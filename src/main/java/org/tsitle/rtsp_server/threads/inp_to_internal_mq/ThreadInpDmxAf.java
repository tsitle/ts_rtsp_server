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
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.*;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceExpandedInfo;
import org.tsitle.rtsp_server.availstreams.AsCodecSettingsChangedFromDmxAfInterface;
import org.tsitle.rtsp_server.helpers.FfCodecToMqCodecHelper;
import org.tsitle.rtsp_server.helpers.CfgTcCodecToFfCodecHelper;
import org.tsitle.rtsp_server.threads.AdaptiveScheduler;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.RunnableBase;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class ThreadInpDmxAf extends RunnableBase {

	private static class FfDmxPktCacheEntry {
		final FfmpegAvPktBasics ffPktObj = new FfmpegAvPktBasics();
		@NonNull TimestampMonotonic ffPktTimestamp = TimestampMonotonic.ofEmpty();
	}

	private static class DataPerMq {
		final FfmpegTcSettingsOutAudio ffTcSettingsOutAudio = new FfmpegTcSettingsOutAudio();
		final MqCodecSettings mqCodecSettings = new MqCodecSettings();
		@NonNull String metadataTags = "";
		boolean hasMetadataTagsChanged = false;
		long msgNr = 1;
		int counter = 0;
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
		final Set<@NonNull String> inputFilePaths = new HashSet<>();
		final Set<@NonNull String> blacklistedFilePaths = new HashSet<>();
		int inputFilePathIdx = -1;
		@NonNull String currentFilePath = "";
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

		boolean needToDrainTc = false;
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

	private final @NonNull ProUri inputSourceDmxAfUri;
	private final @NonNull AsCodecSettingsChangedFromDmxAfInterface codecSettingsChangedInterface;
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
	 * @param inputSourceDmxAfUri URI of the Input Source
	 * @param tcCodecSettings Transcoder Codec settings for the output
	 * @param codecSettingsChangedInterface 'Codec settings changed' interface
	 * @param idInputSource Input Source identifier
	 * @param idEsSource Elementary-Stream Source identifier
	 */
	public ThreadInpDmxAf(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull ProUri inputSourceDmxAfUri,
				RtspProtoEsSourceExpandedInfo.@NonNull TcSettingsAudio tcCodecSettings,
				@NonNull AsCodecSettingsChangedFromDmxAfInterface codecSettingsChangedInterface,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdEsSource idEsSource
			) {
		super(logMsgInterface, cancelToken);

		final String FNC_NAME = getClass().getSimpleName() + ".ctor()";

		//
		this.inputSourceDmxAfUri = inputSourceDmxAfUri.clone();

		this.rd.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec =
				CfgTcCodecToFfCodecHelper.convertCfgTcCodecToFfmpegCodec(tcCodecSettings.codec());
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
		this.threadName = String.format("DMXAF#is%s", computeIsIdForThreadName(idInputSource));

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
					inputSourceDmxAfUri,
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
				codecSettingsChangedInterface.onFileTagsChangedFromDmxAf(idInputSource, rd.dpm.metadataTags);
				rd.dpm.hasMetadataTagsChanged = false;
			}

			//
			for (FfmpegAvPktBasics tcAvPkt : tmpOptAvPktListPtr.get()) {
				adaptiveScheduler.waitForNextFrame();
				sendAvPktToMq(tcAvPkt);
			}
		}

		//
		if (rd.needToDrainTc) {
			rd.dyn.ffmpegTcObj = null;
			rd.needToDrainTc = false;
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

			if (rd.dyn.ffmpegTcObj == null) {
				FfmpegDmxSubStreamInfoAudio tmpSsInfoAud = new FfmpegDmxSubStreamInfoAudio();
				if (! checkInputCodecInfo(tmpSsInfoAud)) {
					blacklistFilePath(rd.ifs.currentFilePath);
					continue;
				}
				//
				initTranscoder(tmpSsInfoAud);
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

		if (rd.needToDrainTc) {
			rd.dyn.ffmpegTcObj.close();  // drain the encoder etc.
			//
			return Optional.of(rd.dyn.ffmpegTcObj.getRemainingPackets(false));
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
					rd.needToDrainTc = (rd.dyn.ffmpegTcObj != null);
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
		if (tmpCdcParams.samplesPerFrame < 1) {
			if (rd.dpm.mqCodecSettings.codec.isPcmAudio()) {
				int tmpBytes = firstAvPkt.pktBe.getUsed();
				tmpBytes /= rd.dpm.mqCodecSettings.audioChannels;
				tmpBytes /= (rd.dpm.mqCodecSettings.codec == MqPacketCodec.LPCM16S ? 2 : 1);
				rd.dpm.mqCodecSettings.audioSamplesPerFrame = tmpBytes;
			} else {
				throw new IllegalStateException(FNC_NAME + ": Transcoder did not provide samplesPerFrame");
			}
		} else {
			rd.dpm.mqCodecSettings.audioSamplesPerFrame = tmpCdcParams.samplesPerFrame;
		}
		double virtualFps = computeVirtualFps(
				rd.dpm.mqCodecSettings.audioSamplesPerFrame,
				rd.dpm.mqCodecSettings.audioSamplerate
			);
		if (virtualFps < 1.0 || virtualFps > 100.0) {
			logError(FNC_NAME, "Invalid virtual FPS: " + virtualFps + " " +
					"(codec=" + rd.dpm.mqCodecSettings.codec + ", inpFn=" + rd.ifs.currentFilePath + ")");
			return;
		}
		codecSettingsChangedInterface.onCodecSettingsChangedFromDmxAf(idEsSource, rd.dpm.mqCodecSettings);

		String metadataHex = tmpCdcParams.extradataHex.getEd();
		codecSettingsChangedInterface.onCodecMetadataFromDmxAf(idEsSource, metadataHex);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean findAndLoadNextFile() {
		rd.dyn.closeDemuxer();

		if (rd.ifs.ffwPtr == null) {
			return false;
		}
		if (rd.ifs.ffwPtr.haveMatchingFilesChanged()) {
			rd.ifs.ffwPtr.getMatchingFilesAsStrings(rd.ifs.blacklistedFilePaths, rd.ifs.inputFilePaths);
		}

		do {
			if (rd.ifs.inputFilePaths.isEmpty()) {
				return false;
			}

			// pick an index in the range [0, rd.ifs.inputFilePaths.size())
			int tmpNextIx = (int)(Math.random() * rd.ifs.inputFilePaths.size());
			if (rd.ifs.inputFilePaths.size() > 1 && tmpNextIx == rd.ifs.inputFilePathIdx) {
				continue;
			}
			String tmpAbsFn = rd.ifs.inputFilePaths.toArray(new String[0])[tmpNextIx];
			//
			Path tmpPathObj = Paths.get(tmpAbsFn);
			if (! tmpPathObj.toFile().exists()) {
				blacklistFilePath(tmpAbsFn);
				continue;
			}
			rd.ifs.inputFilePathIdx = tmpNextIx;
			rd.ifs.currentFilePath = tmpAbsFn;
			//
			initDemuxer();
			break;
		} while (true);
		return true;
	}

	private void blacklistFilePath(@NonNull String fp) {
		rd.ifs.inputFilePaths.remove(fp);
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
		return "AF" + HashMd5Helper.hashOfString(
						idInputSource.getIdStr().orElse("-unset-"),
						false
				).substring(0, 8);
	}

}

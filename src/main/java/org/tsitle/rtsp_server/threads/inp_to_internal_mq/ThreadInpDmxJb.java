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

		double lastPtsSecs = 0.0;
		final TimestampEpoch lastPktTsEpoch = TimestampEpoch.ofEmpty();

		final AudioOpusParser parserOpus = new AudioOpusParser();
	}

	private static class DynamicObjs {
		final FfmpegDmxSettingsDemux dmxSettingsDemux = new FfmpegDmxSettingsDemux();
		final FfmpegTcParamsInpAudio sourceParamsAudio = new FfmpegTcParamsInpAudio();

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

		boolean needToDrainTc = false;
		boolean needToCreateTc = false;
		boolean haveInitTcDependentObjs = false;

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

	private final @NonNull String threadName;
	private final CancelToken localCancelToken = new CancelToken();
	private final @NonNull ThreadLocal<@NonNull AdaptiveScheduler> tlAdaptiveScheduler;
	private final @NonNull ThreadLocal<@NonNull RuntimeData> tlRd;

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

		//
		RuntimeData tmpRdObj = new RuntimeData();

		//
		tmpRdObj.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec =
				CfgTcCodecToFfCodecHelper.convertCfgTcCodecToFfmpegCodec(tcCodecSettings.codecStr());
		tmpRdObj.dpm.ffTcSettingsOutAudio.cfgBitRateKbps = approximateBr(tcCodecSettings.audioBitRateKbps());
		tmpRdObj.dpm.ffTcSettingsOutAudio.cfgSampleRateFixed = tcCodecSettings.audioSampleRate();
		tmpRdObj.dpm.ffTcSettingsOutAudio.cfgChannelCm = (tcCodecSettings.audioChannelCount() == 1 ?
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
		this.tlAdaptiveScheduler = ThreadLocal.withInitial(() -> new AdaptiveScheduler(logMsgInterface, -1.0));

		// demuxer settings
		tmpRdObj.dyn.dmxSettingsDemux.cfgOutputModeAac = FfmpegPktConvModeAac.WITH_ADTS;
		tmpRdObj.dyn.dmxSettingsDemux.cfgAllowOnlySpecificCodecsVideo = true;
		tmpRdObj.dyn.dmxSettingsDemux.cfgAllowOnlySpecificCodecsAudio = true;
		tmpRdObj.dyn.dmxSettingsDemux.cfgAllowedCodecsAudio.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO);
		tmpRdObj.dyn.dmxSettingsDemux.cfgAllowedCodecsAudio.add(FfmpegCodec.A_PCM_S24LE);
		tmpRdObj.dyn.dmxSettingsDemux.cfgAllowedCodecsAudio.add(FfmpegCodec.A_PCM_S32LE);

		// remaining transcoder settings
		if (FfCodecToMqCodecHelper.convertFfToMqCodec(tmpRdObj.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec).isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": " +
					"TC Codec not supported: " + tmpRdObj.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec);
		}
		if (tmpRdObj.dpm.ffTcSettingsOutAudio.cfgSampleRateFixed == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException(FNC_NAME + ": TC Sample rate must be set");
		}
		tmpRdObj.dpm.ffTcSettingsOutAudio.cfgOutputModeAac = FfmpegPktConvModeAac.WITH_ADTS;
		tmpRdObj.dpm.ffTcSettingsOutAudio.cfgOutputModeOpus = FfmpegTcSettingsOutAudio.OutputModeOpus.RTP;
		tmpRdObj.dpm.ffTcSettingsOutAudio.cfgSrCm = FfmpegTcSettingsOutAudio.SampleRateConversionMode.FIXED;

		//
		this.tlRd = ThreadLocal.withInitial(() -> tmpRdObj);
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
		RuntimeData tmpRdObj = tlRd.get();

		//
		try (FileFolderWatcher localAffl = new FileFolderWatcher(
					Objects.requireNonNull(logMsgInterface),
					localCancelToken,
					inputSourceDmxJbUri,
					ALLOWED_INPUT_FILE_EXTS
				)) {
			tmpRdObj.ifs.ffwPtr = localAffl;

			//
			AdaptiveScheduler tmpAdaptiveSchedulerObj = tlAdaptiveScheduler.get();

			//
			while (! (hasBeenRequestedToStop() || localCancelToken.cancelled)) {
				if (! mainLoop(tmpAdaptiveSchedulerObj)) {
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
			tmpRdObj.ifs.ffwPtr = null;
			//
			tmpRdObj.dyn.closeMq();
			tmpRdObj.dyn.closeDemuxer();
			tmpRdObj.dyn.closeTranscoder();

			//
			tlAdaptiveScheduler.remove();
			tlRd.remove();

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

	private boolean mainLoop(@NonNull AdaptiveScheduler adaptiveScheduler) throws MqException, InterruptedException {
		if (! mainLoop_acquireInput()) {
			return false;
		}

		RuntimeData tmpRdObj = tlRd.get();

		Optional<FfmpegTcAvPacketList> tmpOptAvPktListPtr = mainLoop_acquireOutput();
		if (tmpOptAvPktListPtr.isEmpty()) {
			return false;  // error
		}
		if (tmpOptAvPktListPtr.get().isEmpty()) {
			return true;
		}

		if (! tmpRdObj.haveInitTcDependentObjs) {
			mainLoop_initTcDependentObjs(tmpOptAvPktListPtr.get().iterator().next());
		}

		//
		if (tmpRdObj.dpm.hasMetadataTagsChanged) {
			codecSettingsChangedInterface.onFileTagsChangedFromDmxJb(idInputSource, tmpRdObj.dpm.metadataTags);
			tmpRdObj.dpm.hasMetadataTagsChanged = false;
		}

		//
		for (FfmpegAvPktBasics tcAvPkt : tmpOptAvPktListPtr.get()) {
			sendAvPktToMq(adaptiveScheduler, tcAvPkt);
		}

		return true;
	}

	private boolean mainLoop_acquireInput() {
		RuntimeData tmpRdObj = tlRd.get();

		do {
			if (! readNextAvPktFromDemuxer()) {
				return false;
			}

			if (hasBeenRequestedToStop() || localCancelToken.cancelled || ! isRunning.get()) {
				return false;
			}

			if (tmpRdObj.dyn.ffmpegTcObj == null || tmpRdObj.needToCreateTc || tmpRdObj.needToDrainTc) {
				FfmpegDmxSubStreamInfoAudio tmpSsInfoAud = new FfmpegDmxSubStreamInfoAudio();
				if (! checkInputCodecInfo(tmpSsInfoAud)) {
					blacklistFilePath(tmpRdObj.ifs.currentFilePath);
					tmpRdObj.dyn.closeDemuxer();
					continue;
				}
			}
			break;
		} while (! (hasBeenRequestedToStop() || localCancelToken.cancelled) && isRunning.get());
		return (! (hasBeenRequestedToStop() || localCancelToken.cancelled) && isRunning.get());
	}

	private Optional<FfmpegTcAvPacketList> mainLoop_acquireOutput() {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop_acquireOutput()";

		RuntimeData tmpRdObj = tlRd.get();

		if (tmpRdObj.dyn.ffmpegTcObj != null && tmpRdObj.needToDrainTc) {
			tmpRdObj.needToDrainTc = false;
			// drain transcoder
			tmpRdObj.dyn.ffmpegTcObj.close();
			return Optional.of(
					tmpRdObj.dyn.ffmpegTcObj.getRemainingPackets(false)
				);
		}

		if (tmpRdObj.dyn.ffmpegTcObj == null || tmpRdObj.needToCreateTc) {
			tmpRdObj.needToCreateTc = false;
			tmpRdObj.dyn.ffmpegTcObj = null;
			// create new transcoder
			FfmpegDmxSubStreamInfoAudio tmpSsInfoAud = new FfmpegDmxSubStreamInfoAudio();
			checkInputCodecInfo(tmpSsInfoAud);
			initTranscoder(tmpSsInfoAud);
			//
			updateTrackMetadata(tmpSsInfoAud.metaMap);
			//
			tmpRdObj.dpm.lastPtsSecs = 0.0;
		}

		if (tmpRdObj.dyn.ffmpegTcObj == null) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder object not initialized");
		}

		//
		FfmpegTcAvPacketList tcAvPktListPtr;
		try {
			tcAvPktListPtr = tmpRdObj.dyn.ffmpegTcObj.transcodePacketFromBuffer(tmpRdObj.ffDmxPktCacheEntry.ffPktObj);
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
		RuntimeData tmpRdObj = tlRd.get();

		if (tmpRdObj.haveInitTcDependentObjs) {
			return;
		}

		if (tmpRdObj.dpm.mqCodecSettings.codec == null) {
			createMqCodecSettings(firstAvPkt);
		}
		if (tmpRdObj.dyn.mqInternalPub == null) {
			tmpRdObj.dyn.mqInternalPub = new MqInternalPub(logMsgInterface, idEsSource);
			tmpRdObj.dyn.mqInternalPub.connectToMq();
		}

		tmpRdObj.haveInitTcDependentObjs = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean readNextAvPktFromDemuxer() {
		final String FNC_NAME = getClass().getSimpleName() + ".readNextAvPktFromDemuxer()";

		RuntimeData tmpRdObj = tlRd.get();

		boolean haveEof = false;
		boolean isFirstFrame = false;
		do {
			if (haveEof || tmpRdObj.dyn.ffDemuxerObj == null) {
				if (! findAndLoadNextFile()) {
					return false;
				}
				isFirstFrame = true;
			}
			try {
				FfmpegDemuxer.ReadResult tmpRr = tmpRdObj.dyn.ffDemuxerObj.readNextAvPacket(tmpRdObj.ffDmxPktCacheEntry.ffPktObj);
				if (tmpRr == FfmpegDemuxer.ReadResult.RR_EOF) {
					if (isFirstFrame) {
						return false;  // empty file?
					}
					if (tmpRdObj.needToCreateTc) {
						haveEof = true;
						continue;
					}
					tmpRdObj.needToDrainTc = (tmpRdObj.dyn.ffmpegTcObj != null);
					tmpRdObj.needToCreateTc = true;
					return true;
				}
				break;
			} catch (FfmpegGenericException e) {
				logWarn(FNC_NAME, "FfmpegGenericException caught for fn='" + tmpRdObj.ifs.currentFilePath + "': " + e.getMessage());
				blacklistFilePath(tmpRdObj.ifs.currentFilePath);
				haveEof = true;
			}
		} while (! (hasBeenRequestedToStop() || localCancelToken.cancelled));

		if (hasBeenRequestedToStop() || localCancelToken.cancelled) {
			return false;
		}

		//
		tmpRdObj.ffDmxPktCacheEntry.ffPktTimestamp = TimestampMonotonic.ofNsUnsigned64bit(
				(long)(tmpRdObj.ffDmxPktCacheEntry.ffPktObj.ptsUnitsToSeconds() * 1_000_000_000.0)
			);

		return true;
	}

	private void sendAvPktToMq(@NonNull AdaptiveScheduler adaptiveScheduler, @NonNull FfmpegAvPktBasics tcAvPkt)
			throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendAvPktToMq()";

		RuntimeData tmpRdObj = tlRd.get();

		if (tmpRdObj.dyn.mqInternalPub == null) {
			throw new IllegalStateException(FNC_NAME + ": mqInternalPub not initialized");
		}
		if (tmpRdObj.dpm.mqCodecSettings.codec == null ||
				tmpRdObj.dpm.mqCodecSettings.audioSamplerate == null || tmpRdObj.dpm.mqCodecSettings.audioChannels == null ||
				tmpRdObj.dpm.mqCodecSettings.audioSamplesPerFrame == null) {
			throw new IllegalStateException(FNC_NAME + ": mqCodecSettings not initialized");
		}

		//
		boolean isVirtFpsOk = updateMqCodecSettings_spf(tcAvPkt, false);
		if (! isVirtFpsOk) {
			return;
		}

		//
		double tmpPtsSeconds = tcAvPkt.ptsUnitsToSeconds();
		if (tmpPtsSeconds < 0.0) {  // tmpPtsSeconds is allowed to be exactly zero or greater
			return;  // the very first packet of a file often has a negative PTS
		}

		//
		if (tmpRdObj.dpm.lastPktTsEpoch.isEmpty()) {
			tmpRdObj.dpm.lastPktTsEpoch.setToNow();
		}

		//
		double deltaSecs = Math.abs(tmpPtsSeconds - tmpRdObj.dpm.lastPtsSecs);
		tmpRdObj.dpm.lastPtsSecs = tmpPtsSeconds;
		if (deltaSecs > 0.0001 && deltaSecs < 1.0) {
			double asVirtualFps = 1.0 / deltaSecs;
			adaptiveScheduler.setFps(asVirtualFps);
		}
		adaptiveScheduler.waitForNextFrame();

		//
		MqPacketAv mqPkt = new MqPacketAv(
				tmpRdObj.dpm.msgNr++,
				tmpRdObj.dpm.mqCodecSettings.codec,
				false,
				tmpRdObj.dpm.lastPktTsEpoch,
				tmpRdObj.dpm.counter++,
				false,
				ImageDimensions.ofEmpty(),
				FrameRateEnum.UNKNOWN,
				0,
				tmpRdObj.dpm.mqCodecSettings.audioSamplerate,
				tmpRdObj.dpm.mqCodecSettings.audioChannels,
				tmpRdObj.dpm.mqCodecSettings.audioSamplesPerFrame,
				(byte)0x00,  // CRC8, 0x00 ^= do not validate checksum
				tcAvPkt.pktBe
			);
		tmpRdObj.dyn.mqInternalPub.sendMessageAv(mqPkt);

		//
		tmpRdObj.dpm.lastPktTsEpoch.setEpochNsUnsigned64bit(
				tmpRdObj.dpm.lastPktTsEpoch.getEpochNsUnsigned64bit().orElseThrow() +
						(long)(tmpPtsSeconds * 1_000_000_000.0)
			);

		//
		tmpRdObj.bw.bytesSent += tcAvPkt.pktBe.getUsed();
		if (tmpRdObj.bw.tsLastChecked.isEmpty()) {
			tmpRdObj.bw.tsLastChecked.setToNow();
		} else {
			long deltaMs = ((TimestampEpoch.ofNow().getEpochNsUnsigned64bit().orElseThrow() -
					tmpRdObj.bw.tsLastChecked.getEpochNsUnsigned64bit().orElseThrow()) / 1_000_000L);
			if (deltaMs > BW_INFO_OUTPUT_INTV_MS) {
				if (BW_INFO_OUTPUT_ENABLED) {
					double bytesPerSec = ((double)tmpRdObj.bw.bytesSent / (double)deltaMs) * 1_000.0;
					logDebug(FNC_NAME,
							String.format("bandwidth: %.2f kB/s", bytesPerSec / 1_024.0).replace(",", ".")
						);
				}
				tmpRdObj.bw.tsLastChecked.setToNow();
				tmpRdObj.bw.bytesSent = 0L;
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void createMqCodecSettings(@NonNull FfmpegAvPktBasics firstAvPkt) {
		final String FNC_NAME = getClass().getSimpleName() + ".createMqCodecSettings()";

		RuntimeData tmpRdObj = tlRd.get();

		if (tmpRdObj.dyn.ffmpegTcObj == null) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder object not initialized");
		}
		FfmpegCdcParamsAudio tmpCdcParams = new FfmpegCdcParamsAudio();
		tmpRdObj.dyn.ffmpegTcObj.getCdcParamsAudio(tmpCdcParams);

		//
		tmpRdObj.dpm.mqCodecSettings.codec =
				FfCodecToMqCodecHelper.convertFfToMqCodec(tmpRdObj.dpm.ffTcSettingsOutAudio.cfgFfmpegCodec).orElseThrow();
		tmpRdObj.dpm.mqCodecSettings.audioSamplerate = tmpRdObj.dpm.ffTcSettingsOutAudio.cfgSampleRateFixed;
		tmpRdObj.dpm.mqCodecSettings.audioChannels = (byte)tmpCdcParams.channelCount;
		updateMqCodecSettings_spf(firstAvPkt, true);

		String metadataHex = tmpCdcParams.extradataHex.getEd();
		codecSettingsChangedInterface.onCodecMetadataFromDmxJb(idEsSource, metadataHex);
	}

	private boolean updateMqCodecSettings_spf(@NonNull FfmpegAvPktBasics avPkt, boolean forceUpdateUpstream) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateMqCodecSettings_spf()";

		RuntimeData tmpRdObj = tlRd.get();

		if (tmpRdObj.dyn.ffmpegTcObj == null) {
			throw new IllegalStateException(FNC_NAME + ": Transcoder object not initialized");
		}
		if (tmpRdObj.dpm.mqCodecSettings.codec == null || tmpRdObj.dpm.mqCodecSettings.audioChannels == null ||
				tmpRdObj.dpm.mqCodecSettings.audioSamplerate == null) {
			throw new IllegalStateException(FNC_NAME + ": MQ Codec Settings not initialized");
		}

		final int lastSpf = Objects.requireNonNullElse(tmpRdObj.dpm.mqCodecSettings.audioSamplesPerFrame, -1);
		int nextSpf;
		final double lastVirtualFps = tmpRdObj.dpm.virtualFps;

		if (tmpRdObj.dpm.mqCodecSettings.codec == MqPacketCodec.AACLC) {
			if (avPkt.duration != DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1) {
				// this can happen at the end of an input file
				return false;
			}
			nextSpf = (int)avPkt.duration;
		} else if (tmpRdObj.dpm.mqCodecSettings.codec == MqPacketCodec.OPUS) {
			AudioOpusInfo tmpInf;
			try {
				tmpInf = tmpRdObj.dpm.parserOpus.parseOpusData(new BufferView(avPkt.pktBe));
			} catch (AvInvalidCodecDataException e) {
				logError(FNC_NAME, "Invalid Opus data: " + e.getMessage());
				return false;
			}
			nextSpf = tmpInf.samplesPerChannelInAudioData;
		} else if (avPkt.duration < 1) {
			if (! tmpRdObj.dpm.mqCodecSettings.codec.isPcmAudio()) {
				throw new IllegalStateException(FNC_NAME + ": Transcoder did not provide samplesPerFrame");
			}
			int tmpBytes = avPkt.pktBe.getUsed();
			tmpBytes /= tmpRdObj.dpm.mqCodecSettings.audioChannels;
			tmpBytes /= (tmpRdObj.dpm.mqCodecSettings.codec == MqPacketCodec.LPCM16S ? 2 : 1);
			nextSpf = tmpBytes;
		} else {
			nextSpf = (int)avPkt.duration;
		}
		final double tmpVirtFps = computeVirtualFps(
				nextSpf,
				tmpRdObj.dpm.mqCodecSettings.audioSamplerate
			);
		if (tmpVirtFps < 1.0 || tmpVirtFps > RtpConstants.RTP_MAX_FRAMES_PER_SECOND) {
			logError(FNC_NAME, "Invalid virtual FPS: " + tmpVirtFps + " " +
					"(codec=" + tmpRdObj.dpm.mqCodecSettings.codec + ", inpFn=" + tmpRdObj.ifs.currentFilePath + ")");
			return false;
		}
		tmpRdObj.dpm.mqCodecSettings.audioSamplesPerFrame = nextSpf;
		tmpRdObj.dpm.virtualFps = tmpVirtFps;
		if (forceUpdateUpstream ||
				lastSpf != tmpRdObj.dpm.mqCodecSettings.audioSamplesPerFrame || lastVirtualFps != tmpRdObj.dpm.virtualFps) {
			if (! forceUpdateUpstream) {
				logDebug(FNC_NAME, "update virtual FPS: " + tmpVirtFps + " " +
						", SPF: " + tmpRdObj.dpm.mqCodecSettings.audioSamplesPerFrame + " " +
						"(codec=" + tmpRdObj.dpm.mqCodecSettings.codec + ")");
			}
			codecSettingsChangedInterface.onCodecSettingsChangedFromDmxJb(idEsSource, tmpRdObj.dpm.mqCodecSettings);
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean findAndLoadNextFile() {
		final String FNC_NAME = getClass().getSimpleName() + ".findAndLoadNextFile()";

		RuntimeData tmpRdObj = tlRd.get();

		tmpRdObj.dyn.closeDemuxer();

		if (tmpRdObj.ifs.ffwPtr == null) {
			return false;
		}
		if (tmpRdObj.ifs.ffwPtr.haveMatchingFilesChanged()) {
			tmpRdObj.ifs.ffwPtr.getMatchingFilesAsStrings(tmpRdObj.ifs.blacklistedFilePaths, tmpRdObj.ifs.inputFilePathsAsSet);
			tmpRdObj.ifs.inputFilePathsAsList.clear();
			tmpRdObj.ifs.inputFilePathsAsList.addAll(tmpRdObj.ifs.inputFilePathsAsSet);
			tmpRdObj.ifs.alreadyPlayedFpIdx.clear();
			tmpRdObj.ifs.lastPlayedFpIdx = -1;
			tmpRdObj.ifs.blacklistedFilePaths.clear();
		}

		do {
			if (tmpRdObj.ifs.inputFilePathsAsList.isEmpty()) {
				logError(FNC_NAME, "no input files available");
				return false;
			}
			if (tmpRdObj.ifs.alreadyPlayedFpIdx.size() >= tmpRdObj.ifs.inputFilePathsAsList.size()) {
				tmpRdObj.ifs.alreadyPlayedFpIdx.clear();
				if (tmpRdObj.ifs.inputFilePathsAsList.size() > 1) {
					tmpRdObj.ifs.alreadyPlayedFpIdx.add(tmpRdObj.ifs.lastPlayedFpIdx);
				}
			}

			// pick an index in the range [0, tmpRdObj.ifs.inputFilePaths.size())
			int tmpNextIx = (int)(Math.random() * tmpRdObj.ifs.inputFilePathsAsList.size());
			if (tmpRdObj.ifs.alreadyPlayedFpIdx.contains(tmpNextIx)) {
				continue;
			}
			String tmpAbsFn = tmpRdObj.ifs.inputFilePathsAsList.get(tmpNextIx);
			//
			Path tmpPathObj = Paths.get(tmpAbsFn);
			if (! tmpPathObj.toFile().exists()) {
				blacklistFilePath(tmpAbsFn);
				continue;
			}
			tmpRdObj.ifs.currentFilePath = tmpAbsFn;
			tmpRdObj.ifs.alreadyPlayedFpIdx.add(tmpNextIx);
			tmpRdObj.ifs.lastPlayedFpIdx = tmpNextIx;
			//
			initDemuxer();
			break;
		} while (true);

		logDebug(FNC_NAME, "Input file: '" + tmpRdObj.ifs.currentFilePath + "'");
		return true;
	}

	private void blacklistFilePath(@NonNull String fp) {
		RuntimeData tmpRdObj = tlRd.get();

		int tmpFpIx = -1;
		for (int tmpSearchIx = 0; tmpSearchIx < tmpRdObj.ifs.inputFilePathsAsList.size(); tmpSearchIx++) {
			if (tmpRdObj.ifs.inputFilePathsAsList.get(tmpSearchIx).equals(fp)) {
				tmpFpIx = tmpSearchIx;
				break;
			}
		}
		if (tmpFpIx >= 0) {
			tmpRdObj.ifs.alreadyPlayedFpIdx.remove(tmpFpIx);
			if (tmpRdObj.ifs.lastPlayedFpIdx == tmpFpIx) {
				tmpRdObj.ifs.lastPlayedFpIdx = -1;
			} else if (tmpRdObj.ifs.lastPlayedFpIdx > tmpFpIx) {
				--tmpRdObj.ifs.lastPlayedFpIdx;
			}
		}

		tmpRdObj.ifs.inputFilePathsAsSet.remove(fp);
		tmpRdObj.ifs.inputFilePathsAsList.remove(fp);
		tmpRdObj.ifs.blacklistedFilePaths.add(fp);
	}

	private boolean checkInputCodecInfo(@NonNull FfmpegDmxSubStreamInfoAudio ssInfoAud) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkInputCodecInfo()";

		RuntimeData tmpRdObj = tlRd.get();

		if (tmpRdObj.dyn.ffDemuxerObj == null) {
			throw new IllegalStateException(FNC_NAME + ": Demuxer object not initialized");
		}
		Optional<FfmpegDmxSubStreamInfoAudio> tmpSsInfo = tmpRdObj.dyn.ffDemuxerObj.getFfAvSubStreamInfoAudio();

		if (tmpSsInfo.isEmpty()) {
			logWarn(FNC_NAME, "no audio sub-stream found");
			return false;
		}
		ssInfoAud.copyFrom(tmpSsInfo.get());
		return true;
	}

	private void initDemuxer() {
		RuntimeData tmpRdObj = tlRd.get();

		tmpRdObj.dyn.closeDemuxer();

		if (tmpRdObj.ifs.currentFilePath.isBlank()) {
			return;
		}

		tmpRdObj.dyn.ffDemuxerObj = FfmpegDemuxer.createForDemuxingOnly(
				null,
				tmpRdObj.ifs.currentFilePath,
				tmpRdObj.dyn.dmxSettingsDemux
			);
	}

	private void initTranscoder(@NonNull FfmpegDmxSubStreamInfoAudio ssInfoAud) {
		RuntimeData tmpRdObj = tlRd.get();

		tmpRdObj.dyn.closeTranscoder();

		tmpRdObj.dyn.sourceParamsAudio.ffmpegCodec = ssInfoAud.ffmpegCodec;
		tmpRdObj.dyn.sourceParamsAudio.timeBase.copyFrom(ssInfoAud.timeBasePts);
		tmpRdObj.dyn.sourceParamsAudio.sampleRate = ssInfoAud.sampleRate;
		tmpRdObj.dyn.sourceParamsAudio.channelCount = ssInfoAud.channelCount;
		tmpRdObj.dyn.sourceParamsAudio.extradataHex.copyFrom(ssInfoAud.extradataHex);

		tmpRdObj.dyn.ffmpegTcObj = FfmpegTranscoder.createForAudioOnly(
				null,
				tmpRdObj.dyn.sourceParamsAudio,
				tmpRdObj.dpm.ffTcSettingsOutAudio
			);
	}

	@SuppressWarnings("unused")  // keep this method for the time being
	private void updateTranscoder(@NonNull FfmpegDmxSubStreamInfoAudio ssInfoAud) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateTranscoder()";

		RuntimeData tmpRdObj = tlRd.get();

		if (tmpRdObj.dyn.ffmpegTcObj == null) {
			initTranscoder(ssInfoAud);
			return;
		}
		tmpRdObj.dyn.sourceParamsAudio.ffmpegCodec = ssInfoAud.ffmpegCodec;
		tmpRdObj.dyn.sourceParamsAudio.timeBase.copyFrom(ssInfoAud.timeBasePts);
		tmpRdObj.dyn.sourceParamsAudio.sampleRate = ssInfoAud.sampleRate;
		tmpRdObj.dyn.sourceParamsAudio.channelCount = ssInfoAud.channelCount;
		tmpRdObj.dyn.sourceParamsAudio.extradataHex.copyFrom(ssInfoAud.extradataHex);

		try {
			tmpRdObj.dyn.ffmpegTcObj.updateAudioDecoderFromBuffer(tmpRdObj.dyn.sourceParamsAudio);
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
		RuntimeData tmpRdObj = tlRd.get();

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
		tmpRdObj.dpm.hasMetadataTagsChanged = (! tmpOut.equals(tmpRdObj.dpm.metadataTags));
		tmpRdObj.dpm.metadataTags = tmpOut;
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

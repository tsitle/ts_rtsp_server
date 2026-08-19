package org.tsitle.rtsp_server.threads.inp_e2i;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.DpConstants;
import org.tsitle.lib_ffmpeg.FfmpegAvPktBasics;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeAac;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeH26x;
import org.tsitle.lib_ffmpeg.demux.FfmpegDemuxer;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSettingsDemux;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSubStreamInfoAudio;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSubStreamInfoVideo;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
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
import org.tsitle.rtsp_server.availstreams.CodecSettingsChangedFromDmxRtspInterface;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.RunnableBase;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ThreadInpDmxRtsp extends RunnableBase {

	private static class FfPktCacheEntry {
		final @NonNull FfmpegAvPktBasics ffPktObj = new FfmpegAvPktBasics();
		@NonNull TimestampMonotonic ffPktTimestamp = TimestampMonotonic.ofEmpty();
	}

	private static class DataPerMq {
		@NonNull FfmpegCodec ffCodec = FfmpegCodec.UNKNOWN;
		@NonNull ImageDimensions videoReso = ImageDimensions.ofEmpty();
		final MqCodecSettings mqCodecSettings = new MqCodecSettings();
		long msgNr = 1;
		int counter = 0;
	}

	private final @NonNull CodecSettingsChangedFromDmxRtspInterface codecSettingsChangedInterface;
	private final @NonNull URI inputSourceDmxRtspUri;
	private final @NonNull RtspProtoIdEsSource idEsSourceVid = RtspProtoIdEsSource.ofEmpty();
	private final @NonNull RtspProtoIdEsSource idEsSourceAud = RtspProtoIdEsSource.ofEmpty();

	private final String threadName;

	private @Nullable FfmpegDemuxer ffDemuxerPtr = null;
	private @Nullable MqInternalPub mqInternalPubVid = null;
	private @Nullable MqInternalPub mqInternalPubAud = null;

	private final @NonNull CancelToken localCancelToken = new CancelToken();

	private final AtomicBoolean haveInputSi = new AtomicBoolean(false);
	private final AtomicBoolean haveInputVideo = new AtomicBoolean(false);
	private final AtomicBoolean haveInputAudio = new AtomicBoolean(false);

	private final @NonNull FfPktCacheEntry ffPktCacheEntry = new FfPktCacheEntry();
	private final @NonNull DataPerMq dataPerMqVid = new DataPerMq();
	private final @NonNull DataPerMq dataPerMqAud = new DataPerMq();
	private final @NonNull TimestampEpoch tsEpochStart = TimestampEpoch.ofEmpty();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param inputSourceDmxRtspUri URI of the Input Source
	 * @param idInputSource Input Source identifier
	 * @param idEsSourceVid Elementary-Stream Source identifier for video
	 * @param idEsSourceAud Elementary-Stream Source identifier for audio
	 */
	public ThreadInpDmxRtsp(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull URI inputSourceDmxRtspUri,
				@NonNull CodecSettingsChangedFromDmxRtspInterface codecSettingsChangedInterface,
				@NonNull RtspProtoIdInputSource idInputSource,
				@NonNull RtspProtoIdEsSource idEsSourceVid,
				@NonNull RtspProtoIdEsSource idEsSourceAud
			) {
		super(logMsgInterface, cancelToken);

		//
		this.codecSettingsChangedInterface = codecSettingsChangedInterface;
		if (inputSourceDmxRtspUri.toString().isBlank()) {
			throw new IllegalArgumentException("Input URI must not be blank");
		}
		this.inputSourceDmxRtspUri = URI.create(inputSourceDmxRtspUri.toString());
		if (this.inputSourceDmxRtspUri.getScheme() == null) {
			throw new IllegalArgumentException("Input URI must have a protocol");
		}
		if (! ("http".equals(inputSourceDmxRtspUri.getScheme()) || "https".equals(inputSourceDmxRtspUri.getScheme()))) {
			throw new IllegalArgumentException("Input URI scheme must be 'http|https'");
		}
		if (this.inputSourceDmxRtspUri.getPath() == null) {
			throw new IllegalArgumentException("Input URI must have a path");
		}
		this.idEsSourceVid.copyFrom(idEsSourceVid);
		this.idEsSourceAud.copyFrom(idEsSourceAud);

		//
		this.threadName = String.format("DMXRTSP#is%s", computeIsIdForThreadName(idInputSource));
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
		try {
			int retryCount = 0;
			while (! (hasBeenRequestedToStop() || localCancelToken.cancelled)) {
				demuxerConnectWrapper();

				// sleep before trying to reconnect
				if (++retryCount > 10) {
					retryCount = 10;
				}
				sleepLongAndProsper(localCancelToken, retryCount);
			}
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException caught");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			if (mqInternalPubVid != null) {
				mqInternalPubVid.close();
				mqInternalPubVid = null;
			}
			if (mqInternalPubAud != null) {
				mqInternalPubAud.close();
				mqInternalPubAud = null;
			}
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

	private void demuxerConnectWrapper() throws InterruptedException, MqException {
		String inputFilePath = inputSourceDmxRtspUri.toString()
				.replace("http://", "rtsp://")
				.replace("https://", "rtsps://");

		//
		FfmpegDmxSettingsDemux dmxSettingsDemux = new FfmpegDmxSettingsDemux();
		dmxSettingsDemux.cfgOutputModeH26x = FfmpegPktConvModeH26x.ANNEXB;
		dmxSettingsDemux.cfgOutputModeAac = FfmpegPktConvModeAac.WITH_ADTS;
		dmxSettingsDemux.cfgAllowOnlySpecificCodecsVideo = true;
		dmxSettingsDemux.cfgAllowedCodecsVideo.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_VIDEO);
		dmxSettingsDemux.cfgAllowOnlySpecificCodecsAudio = true;
		dmxSettingsDemux.cfgAllowedCodecsAudio.addAll(DpConstants.DP_FFMPEG_ALLOWED_CODECS_AUDIO);

		try (FfmpegDemuxer ffDemuxer = FfmpegDemuxer.createForDemuxingOnly(
					logMsgInterface,
					inputFilePath,
					dmxSettingsDemux
				)) {
			ffDemuxerPtr = ffDemuxer;
			//
			while (! (hasBeenRequestedToStop() || localCancelToken.cancelled)) {
				if (! mainLoop()) {
					break;
				}
			}
		} finally {
			ffDemuxerPtr = null;
		}
	}

	private boolean mainLoop() throws MqException {
		if (! readNextAvPktFromDemuxer()) {
			return false;
		}
		if (ffPktCacheEntry.ffPktObj.isVideo && mqInternalPubVid != null) {
			sendAvPktToMq(mqInternalPubVid, dataPerMqVid);
		} else if (! ffPktCacheEntry.ffPktObj.isVideo && mqInternalPubAud != null) {
			sendAvPktToMq(mqInternalPubAud, dataPerMqAud);
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean readNextAvPktFromDemuxer() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".readNextAvPktFromDemuxer()";

		if (ffDemuxerPtr == null) {
			return false;
		}
		try {
			FfmpegDemuxer.ReadResult tmpRr = ffDemuxerPtr.readNextAvPacket(ffPktCacheEntry.ffPktObj);
			if (tmpRr == FfmpegDemuxer.ReadResult.RR_EOF) {
				return false;
			}
		} catch (FfmpegGenericException e) {
			logWarn(FNC_NAME, "FfmpegGenericException caught: " + e.getMessage());
			return false;
		}

		//
		if (! haveInputSi.get()) {
			initCodecInfo();

			//
			tsEpochStart.copyFrom(TimestampEpoch.ofNow());
		}

		//
		ffPktCacheEntry.ffPktTimestamp = TimestampMonotonic.ofNsUnsigned64bit(
				(long)(ffPktCacheEntry.ffPktObj.ptsUnitsToSeconds() * 1_000_000_000.0)
			);

		return true;
	}

	private void initCodecInfo() throws MqException {
		final String FNC_NAME = getClass().getSimpleName() + ".initCodecInfo()";

		if (ffDemuxerPtr == null) {
			return;
		}

		haveInputVideo.set(ffDemuxerPtr.getFfAvSubStreamIxVideo().isPresent());
		haveInputAudio.set(ffDemuxerPtr.getFfAvSubStreamIxAudio().isPresent());
		haveInputSi.set(true);

		//
		if (haveInputVideo.get()) {
			FfmpegDmxSubStreamInfoVideo tmpSsInfo = ffDemuxerPtr.getFfAvSubStreamInfoVideo().orElseThrow();

			dataPerMqVid.ffCodec = tmpSsInfo.ffmpegCodec;
			Optional<MqPacketCodec> tmpOptMqCodec = convertFfToMqCodec(dataPerMqVid.ffCodec);
			if (tmpOptMqCodec.isEmpty()) {
				logWarn(FNC_NAME, "Unsupported codec: " + tmpSsInfo.ffmpegCodec);
				haveInputVideo.set(false);
			} else if (idEsSourceVid.isEmpty()) {
				logWarn(FNC_NAME, "ignoring video sub-stream");
				haveInputVideo.set(false);
			} else {
				dataPerMqVid.videoReso = ImageDimensions.of(tmpSsInfo.imgDims);

				dataPerMqVid.mqCodecSettings.codec = tmpOptMqCodec.get();
				if (tmpSsInfo.fps.toDouble() > 120.0) {
					/*
					 * FFmpeg reports the Time Base as the Frame Rate for RTSP streams if the SDP doesn't define the actual FPS.
					 * Then the FPS is 90000. So we set it to a safe 30.
					 */
					dataPerMqVid.mqCodecSettings.videoFps = FrameRateEnum.FPS_30_0;
				} else {
					dataPerMqVid.mqCodecSettings.videoFps = FrameRateEnum.of(tmpSsInfo.fps.toDouble());
					if (dataPerMqVid.mqCodecSettings.videoFps == FrameRateEnum.UNKNOWN) {
						dataPerMqVid.mqCodecSettings.videoFps = approximateFps(tmpSsInfo.fps.toDouble());
					}
				}
				dataPerMqVid.mqCodecSettings.audioSamplerate = SampleRateEnum.UNKNOWN;
				dataPerMqVid.mqCodecSettings.audioChannels = (byte)0;
				dataPerMqVid.mqCodecSettings.audioSamplesPerFrame = 0;
				codecSettingsChangedInterface.onCodecSettingsChangedFromDmxRtsp(idEsSourceVid, dataPerMqVid.mqCodecSettings);

				String metadataHexVid = tmpSsInfo.extradataHex.getEd();
				codecSettingsChangedInterface.onCodecMetadataFromDmxRtsp(idEsSourceVid, metadataHexVid);
			}
		}
		if (haveInputAudio.get()) {
			FfmpegDmxSubStreamInfoAudio tmpSsInfo = ffDemuxerPtr.getFfAvSubStreamInfoAudio().orElseThrow();

			dataPerMqAud.ffCodec = tmpSsInfo.ffmpegCodec;
			Optional<MqPacketCodec> tmpOptMqCodec = convertFfToMqCodec(dataPerMqAud.ffCodec);
			if (tmpOptMqCodec.isEmpty()) {
				logWarn(FNC_NAME, "Unsupported codec: " + tmpSsInfo.ffmpegCodec);
				haveInputAudio.set(false);
			} else if (idEsSourceAud.isEmpty()) {
				logWarn(FNC_NAME, "ignoring video sub-stream");
				haveInputVideo.set(false);
			} else {
				dataPerMqAud.mqCodecSettings.codec = tmpOptMqCodec.get();
				dataPerMqAud.mqCodecSettings.videoFps = FrameRateEnum.UNKNOWN;
				dataPerMqAud.mqCodecSettings.audioSamplerate = tmpSsInfo.sampleRate;
				dataPerMqAud.mqCodecSettings.audioChannels = (byte)tmpSsInfo.channelCount;
				dataPerMqAud.mqCodecSettings.audioSamplesPerFrame = tmpSsInfo.samplesPerFrame;
				codecSettingsChangedInterface.onCodecSettingsChangedFromDmxRtsp(idEsSourceAud, dataPerMqAud.mqCodecSettings);

				String metadataHexAud = tmpSsInfo.extradataHex.getEd();
				codecSettingsChangedInterface.onCodecMetadataFromDmxRtsp(idEsSourceAud, metadataHexAud);
			}
		}

		//
		if (haveInputVideo.get()) {
			mqInternalPubVid = new MqInternalPub(logMsgInterface, idEsSourceVid);
			mqInternalPubVid.connectToMq();
		}
		if (haveInputAudio.get()) {
			mqInternalPubAud = new MqInternalPub(logMsgInterface, idEsSourceAud);
			mqInternalPubAud.connectToMq();
		}
	}

	private void sendAvPktToMq(
				@NonNull MqInternalPub mqInternalPub,
				@NonNull DataPerMq dataPerMq
			) throws MqException {
		if (dataPerMq.mqCodecSettings.codec == null || dataPerMq.mqCodecSettings.videoFps == null ||
				dataPerMq.mqCodecSettings.audioSamplerate == null || dataPerMq.mqCodecSettings.audioChannels == null ||
				dataPerMq.mqCodecSettings.audioSamplesPerFrame == null) {
			return;
		}

		//
		TimestampEpoch tmpTsEpoch = TimestampEpoch.ofEpochNsUnsigned64bit(
				tsEpochStart.getEpochNsUnsigned64bit().orElseThrow() +
				ffPktCacheEntry.ffPktTimestamp.getNsUnsigned64bit().orElseThrow()
			);

		//
		MqPacketAv mqPkt = new MqPacketAv(
				dataPerMq.msgNr++,
				dataPerMq.mqCodecSettings.codec,
				false,
				tmpTsEpoch,
				dataPerMq.counter++,
				ffPktCacheEntry.ffPktObj.isVidKeyFrame,
				dataPerMq.videoReso,
				dataPerMq.mqCodecSettings.videoFps,
				0,
				dataPerMq.mqCodecSettings.audioSamplerate,
				dataPerMq.mqCodecSettings.audioChannels,
				dataPerMq.mqCodecSettings.audioSamplesPerFrame,
				(byte)0x00,  // CRC8, 0x00 ^= do not validate checksum
				ffPktCacheEntry.ffPktObj.pktBe
			);
		mqInternalPub.sendMessageAv(mqPkt);
	}

	private static Optional<MqPacketCodec> convertFfToMqCodec(@NonNull FfmpegCodec ffCodec) {
		return switch (ffCodec) {
				case A_AAC -> Optional.of(MqPacketCodec.AACLC);
				case A_AC3 -> Optional.of(MqPacketCodec.AC3);
				case A_MP2 -> Optional.of(MqPacketCodec.MP2);
				case A_MP3 -> Optional.of(MqPacketCodec.MP3);
				case A_OPUS -> Optional.of(MqPacketCodec.OPUS);
				case A_PCM_ALAW -> Optional.of(MqPacketCodec.PCMA);
				case A_PCM_MULAW -> Optional.of(MqPacketCodec.PCMU);
				case A_PCM_U8 -> Optional.of(MqPacketCodec.LPCM08U);
				case A_PCM_S16BE -> Optional.of(MqPacketCodec.LPCM16S);
				//
				case V_H264 -> Optional.of(MqPacketCodec.H264);
				case V_H265 -> Optional.of(MqPacketCodec.H265);
				case V_MJPEG -> Optional.of(MqPacketCodec.MJPEG);
				case V_VP8 -> Optional.of(MqPacketCodec.VP8);
				default -> Optional.empty();
			};
	}

	private static @NonNull FrameRateEnum approximateFps(double orgFps) {
		FrameRateEnum closestEn = FrameRateEnum.UNKNOWN;
		double closestDiff = Double.MAX_VALUE;
		for (FrameRateEnum tmpEn : FrameRateEnum.values()) {
			double curDiff = Math.abs(tmpEn.getFrDbl() - orgFps);
			if (Double.compare(curDiff, closestDiff) < 0) {
				closestDiff = curDiff;
				closestEn = tmpEn;
			}
		}
		return (closestEn != FrameRateEnum.UNKNOWN ? closestEn : FrameRateEnum.FPS_1_0);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String computeIsIdForThreadName(@NonNull RtspProtoIdInputSource idInputSource) {
		return "I" + HashMd5Helper.hashOfString(
						idInputSource.getIdStr().orElse("-unset-"),
						false
				).substring(0, 8);
	}

}

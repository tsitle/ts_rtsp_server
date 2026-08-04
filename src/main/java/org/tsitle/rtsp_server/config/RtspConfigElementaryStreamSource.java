package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSubStreamInfoAudio;
import org.tsitle.lib_ffmpeg.demux.FfmpegDmxSubStreamInfoVideo;
import org.tsitle.lib_rtsp_mq.client.types.MqElementaryStreamSourceSettings;
import org.tsitle.lib_rtsp_mq.common.mqdata.MqPacketCodec;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataForSdpHelper;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSocketPortNr;
import org.tsitle.rtsp_server.exceptions.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.threads.rtp.RtpConstants;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;

/**
 * Elementary-Stream Source within an Input Source for RTSP streams.
 */
public final class RtspConfigElementaryStreamSource {

	/** Elementary-Stream Source ID */
	@GsonAnnoExclude
	private @NonNull Integer id;
	/** Is this Elementary-Stream Source enabled? (default: true) */
	@Expose
	private @NonNull Boolean enabled;
	/** Path to the media file -- either {@code filePath} or {@code mq} must be set, but not both. */
	@Expose
	private @NonNull String filePath;
	/** Message Queue settings for the media stream -- either {@code filePath} or {@code mq} must be set, but not both. */
	@Expose
	private @Nullable RtspConfigEsMq mq;
	/** Codec used for the stream -- only when {@code filePath} is set. */
	@Expose
	private final @NonNull ConfigEsCodec codec;
	/** Video frames per second -- only when {@code filePath} is set. */
	@Expose
	private final @NonNull Double videoFps;
	/** Audio samplerate in Hz -- only when {@code filePath} is set. */
	@Expose
	private final @NonNull Integer audioSamplerateHz;
	/** Audio channel count -- only when {@code filePath} is set. */
	@Expose
	private final @NonNull Integer audioChannelCount;
	/** Is PCM Audio input big-endian? -- only when {@code filePath} is set. */
	@Expose
	private final @NonNull Boolean isPcmAudioBigEndian;

	/** Only for AAC: Audio samples per frame -- only when {@code filePath} is set. */
	@Expose
	private @NonNull Integer aacSamplesPerFrame;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;
	/** Internal use: Codec used for the stream */
	@GsonAnnoExclude
	private @NonNull RtpPacketType internalCodec;
	/** Internal use: Duration in seconds */
	@GsonAnnoExclude
	private double internalDurationSecs;
	/** Internal use: Video frames per second */
	@GsonAnnoExclude
	private @NonNull FrameRateEnum internalVideoFps;
	/** Internal use: Video extradata as Base64 string */
	@GsonAnnoExclude
	@NonNull ExtradataContainerSdp internalVideoExtradataB64;
	/** Internal use: Audio samplerate */
	@GsonAnnoExclude
	@NonNull SampleRateEnum internalAudioSampleRate;
	/** Internal use: Audio channel count */
	@GsonAnnoExclude
	byte internalAudioChannelCount;
	/** Internal use: Is PCM Audio input big-endian? */
	@GsonAnnoExclude
	private boolean internalIsPcmAudioBigEndian;
	/** Internal use: Audio Samples per Frame */
	@GsonAnnoExclude
	private int internalAudioSamplesPerFrame;
	/** Internal use: only for AAC: AudioSpecificConfig as hex string */
	@GsonAnnoExclude
	@NonNull ExtradataContainerHex internalAacAudioSpecificConfigHex;

	/** for MQs: Codec */
	@GsonAnnoExclude
	private @NonNull RtpPacketType mqDynamicCodec;
	/** for MQs: Video frames per second */
	@GsonAnnoExclude
	private @NonNull FrameRateEnum mqDynamicVideoFps;
	/** for MQs: Audio samplerate */
	@GsonAnnoExclude
	private @NonNull SampleRateEnum mqDynamicAudioSamplerateHz;
	/** for MQs: Audio channel count */
	@GsonAnnoExclude
	private byte mqDynamicAudioChannelCount;
	/** for MQs: Audio samples per frame */
	@GsonAnnoExclude
	private int mqDynamicAudioSamplesPerFrame;

	/** for Demuxed Muxed-Stream Sources: Muxed-Stream Source ID */
	@GsonAnnoExclude
	private @Nullable Integer msSourceId;
	/** for Demuxed Muxed-Stream Sources: Muxed-Stream Source URI */
	@GsonAnnoExclude
	private @Nullable URI msSourceUri;
	/** for Demuxed Muxed-Stream Sources: FFmpeg Stream Index */
	@GsonAnnoExclude
	private @Nullable Integer msSourceFfmpegStreamIx;

	public RtspConfigElementaryStreamSource() {
		this.id = -1;
		this.enabled = true;
		this.filePath = "";
		this.mq = null;
		//noinspection DataFlowIssue
		this.codec = null;
		this.videoFps = -1.0;
		this.audioSamplerateHz = -1;
		this.audioChannelCount = -1;
		this.isPcmAudioBigEndian = false;

		this.aacSamplesPerFrame = RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1;
		this.internalAacAudioSpecificConfigHex = ExtradataContainerHex.ofEmpty();

		this.internalHasBeenPostProcessed = false;
		this.internalCodec = RtpPacketType.UNKNOWN;
		this.internalDurationSecs = -1.0;
		this.internalVideoFps = FrameRateEnum.UNKNOWN;
		this.internalVideoExtradataB64 = ExtradataContainerSdp.ofEmpty();
		this.internalAudioSampleRate = SampleRateEnum.UNKNOWN;
		this.internalAudioChannelCount = -1;
		this.internalIsPcmAudioBigEndian = false;
		this.internalAudioSamplesPerFrame = -1;

		this.mqDynamicCodec = RtpPacketType.UNKNOWN;
		this.mqDynamicVideoFps = FrameRateEnum.UNKNOWN;
		this.mqDynamicAudioSamplerateHz = SampleRateEnum.UNKNOWN;
		this.mqDynamicAudioChannelCount = -1;
		this.mqDynamicAudioSamplesPerFrame = -1;

		this.msSourceId = null;
		this.msSourceUri = null;
		this.msSourceFfmpegStreamIx = null;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull RtspConfigElementaryStreamSource createFromDemuxedSubStreamVideo(
				int esSourceId,
				int msSourceId,
				@NonNull URI msSourceUri,
				@NonNull FfmpegDmxSubStreamInfoVideo subStreamInfo
			) throws ConfigInvalidException {
		final String FNC_NAME = RtspConfigElementaryStreamSource.class.getSimpleName() + ".createFromDemuxedSubStreamVideo()";

		final String errMsgUri = buildMsSourceUriForErrorMsgs(msSourceUri);

		RtspConfigElementaryStreamSource resObj = new RtspConfigElementaryStreamSource();
		resObj.id = esSourceId;
		resObj.msSourceId = msSourceId;
		resObj.msSourceUri = msSourceUri;
		resObj.msSourceFfmpegStreamIx = subStreamInfo.subStreamIx;

		resObj.internalHasBeenPostProcessed = true;

		resObj.internalCodec = convertFfmpegVideoCodecToRtpPacketType(subStreamInfo.ffmpegCodec);
		resObj.internalDurationSecs = subStreamInfo.durationSecs;

		resObj.internalVideoFps = FrameRateEnum.of(subStreamInfo.fps.toDouble());
		if (resObj.internalVideoFps == FrameRateEnum.UNKNOWN) {  // just in case
			throw new ConfigInvalidException(FNC_NAME + ": cannot handle FPS value " + subStreamInfo.fps + " " +
					"for MS Source '" + errMsgUri + "'");
		}
		resObj.internalVideoExtradataB64.copyFrom(
				ExtradataForSdpHelper.buildExtradataForSdp(resObj.internalCodec, subStreamInfo.extradataHex)
			);

		return resObj;
	}

	static @NonNull RtspConfigElementaryStreamSource createFromDemuxedSubStreamAudio(
				int esSourceId,
				int msSourceId,
				@NonNull URI msSourceUri,
				@NonNull FfmpegDmxSubStreamInfoAudio subStreamInfo
			) throws ConfigInvalidException {
		final String FNC_NAME = RtspConfigElementaryStreamSource.class.getSimpleName() + ".createFromDemuxedSubStreamAudio()";

		final String errMsgUri = buildMsSourceUriForErrorMsgs(msSourceUri);

		RtspConfigElementaryStreamSource resObj = new RtspConfigElementaryStreamSource();
		resObj.id = esSourceId;
		resObj.msSourceId = msSourceId;
		resObj.msSourceUri = msSourceUri;
		resObj.msSourceFfmpegStreamIx = subStreamInfo.subStreamIx;

		resObj.internalHasBeenPostProcessed = true;

		resObj.internalCodec = convertFfmpegAudioCodecToRtpPacketType(
				subStreamInfo.ffmpegCodec,
				subStreamInfo.sampleRate,
				(byte)subStreamInfo.channelCount
			);
		resObj.internalDurationSecs = subStreamInfo.durationSecs;

		resObj.internalAudioSampleRate = SampleRateEnum.of(subStreamInfo.sampleRate.getSrHz());
		if (resObj.internalAudioSampleRate == SampleRateEnum.UNKNOWN) {  // just in case
			throw new ConfigInvalidException(FNC_NAME + ": cannot handle SampleRate value " + subStreamInfo.sampleRate + " " +
					"for MS Source '" + errMsgUri + "'");
		}
		resObj.internalAudioChannelCount = (byte)subStreamInfo.channelCount;
		if (resObj.internalAudioChannelCount < 1 ||
				resObj.internalAudioChannelCount > RtpConstants.RTP_AUDIO_CHANNELS_MAX) {  // just in case
			throw new ConfigInvalidException(FNC_NAME + ": cannot handle ChannelCount value " + subStreamInfo.channelCount + " " +
					"for MS Source '" + errMsgUri + "'");
		}
		resObj.internalIsPcmAudioBigEndian = (subStreamInfo.ffmpegCodec == FfmpegCodec.A_PCM_S16BE);
		resObj.internalAudioSamplesPerFrame = subStreamInfo.samplesPerFrame;

		resObj.aacSamplesPerFrame = subStreamInfo.samplesPerFrame;
		if (subStreamInfo.ffmpegCodec == FfmpegCodec.A_AAC) {
			resObj.internalAacAudioSpecificConfigHex.copyFrom(subStreamInfo.extradataHex);
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getIdAsInt() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (id == null ? -1 : id);
	}
	public @NonNull RtspProtoIdEsSource getIdAsProtoId() {
		RtspProtoIdEsSource resObj = RtspProtoIdEsSource.ofEmpty();
		resObj.setIdStr(Integer.toUnsignedString(getIdAsInt()));
		resObj.writeProtect();
		return resObj;
	}

	public boolean getEnabled() {
		checkPostProcessed();
		return enabled;
	}

	public @NonNull URI getInputUri() {
		checkPostProcessed();
		return switch (getSourceType()) {
				case ST_ES_FILE -> URI.create("file:" + filePath);
				case ST_ES_MQ -> Objects.requireNonNull(mq).getInputUri();
				case ST_DEMUX_MS_FILE, ST_DEMUX_MS_RTSP -> Objects.requireNonNull(msSourceUri);
			};
	}

	public Optional<MqElementaryStreamSourceSettings> getInputMqSettings() {
		checkPostProcessed();
		if (! filePath.isBlank()) {
			return Optional.empty();
		}
		if (mq == null) {
			throw new IllegalStateException("MQ is null");
		}
		MqElementaryStreamSourceSettings resObj;
		try {
			resObj = new MqElementaryStreamSourceSettings(
					mq.getUsername(),
					mq.getPassword(),
					mq.getHost(),
					RtspProtoSocketPortNr.of(mq.getPort()),
					mq.getRscGroup(),
					mq.getRscChannel()
				);
		} catch (RtspProtoNumberRangeException e) {
			throw new IllegalStateException("MQ Port Number is out of range");
		}
		return Optional.of(resObj);
	}

	public @NonNull RtspProtoEsSourceType getSourceType() {
		final String FNC_NAME = getClass().getSimpleName() + ".getSourceType()";

		checkPostProcessed();
		if (! filePath.isBlank()) {
			return RtspProtoEsSourceType.ST_ES_FILE;
		}
		if (mq != null) {
			return RtspProtoEsSourceType.ST_ES_MQ;
		}
		if (msSourceId != null && msSourceFfmpegStreamIx != null && msSourceUri != null) {
			return ("file".equals(msSourceUri.getScheme()) ?
					RtspProtoEsSourceType.ST_DEMUX_MS_FILE : RtspProtoEsSourceType.ST_DEMUX_MS_RTSP
				);
		}
		throw new IllegalStateException(FNC_NAME + ": could not identify Source Type");
	}

	public synchronized @NonNull RtpPacketType getCodec() {
		checkPostProcessed();
		if (getSourceType() == RtspProtoEsSourceType.ST_ES_MQ) {
			// the actual codec will be determined dynamically when reading from a MQ
			return mqDynamicCodec;
		}
		//noinspection ConstantValue
		return (internalCodec == null ? RtpPacketType.UNKNOWN : internalCodec);
	}

	public synchronized double getDurationSecs() {
		checkPostProcessed();
		if (getSourceType() != RtspProtoEsSourceType.ST_DEMUX_MS_FILE) {
			return -1.0;
		}
		return internalDurationSecs;
	}

	public synchronized @NonNull FrameRateEnum getVideoFps() {
		checkPostProcessed();
		if (getSourceType() == RtspProtoEsSourceType.ST_ES_MQ) {
			// the actual FPS doesn't matter when reading from a MQ, but it will be determined dynamically when reading from a MQ
			return mqDynamicVideoFps;
		}
		return internalVideoFps;
	}

	public synchronized @NonNull ExtradataContainerSdp getVideoExtraB64Cfg() {
		checkPostProcessed();
		ExtradataContainerSdp resObj = ExtradataContainerSdp.ofEmpty();
		resObj.copyFrom(internalVideoExtradataB64);
		return resObj;
	}

	public synchronized @NonNull SampleRateEnum getAudioSamplerate() {
		checkPostProcessed();
		if (getSourceType() == RtspProtoEsSourceType.ST_ES_MQ) {
			// the actual samplerate will be determined dynamically when reading from a MQ
			return mqDynamicAudioSamplerateHz;
		}
		return internalAudioSampleRate;
	}

	public synchronized byte getAudioChannelCount() {
		checkPostProcessed();
		if (getSourceType() == RtspProtoEsSourceType.ST_ES_MQ) {
			// the actual channel count will be determined dynamically when reading from a MQ
			return mqDynamicAudioChannelCount;
		}
		return internalAudioChannelCount;
	}

	/**
	 * Compute the virtual framerate as required for RTP.
	 * @return Frames per second or -1.0 if the framerate cannot be computed
	 * @throws IllegalArgumentException If the samplerate is not valid
	 */
	public double computeAudioVirtualFps() {
		checkPostProcessed();
		//
		if (getAudioSamplerate() == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException("audioSamplerateHz must be valid");
		}
		final int tmpSpf = getAudioSamplesPerFrame();
		if (tmpSpf < 1) {
			return -1.0;
		}
		/*
		 * 25 fps ^= 1 frame each 40 ms
		 * 8000 samples/sec ^= 1 sample each 0.125 ms
		 * 40 ms / 0.125 ms == 320 samples per frame
		 *
		 * 15 fps ^= 1 frame each 66.7 ms
		 * 8000 samples/sec ^= 1 sample each 0.125 ms
		 * 66.7 ms / 0.125 ms == 533 samples per frame
		 *
		 * SampleIntv = 1 / SpS
		 * FrameIntv = SpF * SampleIntv --- SpF = FrameIntv / SampleIntv
		 *
		 * FpS = 1 / FrameIntv
		 */
		double frameIntv = (double)tmpSpf / (double)getAudioSamplerate().getSrHz();
		return (1.0 / frameIntv);
	}

	/**
	 * Get the number of audio samples per frame as required for RTP.
	 * @return Samples per frame or -1 if the value is not available
	 */
	public int getAudioSamplesPerFrame() {
		checkPostProcessed();
		if (getSourceType() == RtspProtoEsSourceType.ST_ES_MQ) {
			// the actual number of samples per frame will be determined dynamically when reading from a MQ
			return mqDynamicAudioSamplesPerFrame;
		}
		return internalAudioSamplesPerFrame;
	}

	public boolean getIsPcmAudioBigEndian() {
		checkPostProcessed();
		if (getSourceType() == RtspProtoEsSourceType.ST_ES_MQ) {
			return true;  // when reading from a MQ, the audio data is expected to be big-endian
		}
		return internalIsPcmAudioBigEndian;
	}

	/**
	 * Only for AAC: Get AudioSpecificConfig as a hex string for SDP.
	 * @return AudioSpecificConfig
	 */
	public @NonNull ExtradataContainerHex getAacAudioSpecificConfigHex() {
		checkPostProcessed();
		ExtradataContainerHex resObj = ExtradataContainerHex.ofEmpty();
		resObj.copyFrom(internalAacAudioSpecificConfigHex);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void setMqDynamicCodec(@NonNull RtpPacketType value) { this.mqDynamicCodec = value; }

	public synchronized void setMqDynamicVideoFps(@NonNull FrameRateEnum value) { this.mqDynamicVideoFps = value; }

	public synchronized void setMqDynamicAudioSamplerateHz(@NonNull SampleRateEnum value) { this.mqDynamicAudioSamplerateHz = value; }

	public synchronized void setMqDynamicAudioChannelCount(byte value) { this.mqDynamicAudioChannelCount = value; }

	public synchronized void setMqDynamicAudioSamplesPerFrame(int value) { this.mqDynamicAudioSamplesPerFrame = value; }

	public synchronized void setMqDynamicExtradata(@NonNull String value) {
		internalVideoExtradataB64.copyFrom(
				ExtradataForSdpHelper.buildExtradataForSdp(getCodec(), value)
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void setIdAsInt(int id) { this.id = id; }

	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull String dataFilenameToAbsolutePath(@NonNull Path dataDir, @NonNull String dataFn) {
		return Path.of(dataDir.toAbsolutePath().toString(), dataFn.strip()).toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the Elementary-Stream Source.
	 * @param dataDir Directory containing the media files
	 */
	void postProcess(@NonNull Path dataDir) {
		internalHasBeenPostProcessed = true;
		//
		//noinspection ConstantValue
		if (enabled == null) {
			enabled = true;
		}
 		//
		//noinspection ConstantValue
		if (filePath != null && ! filePath.isBlank()) {
			filePath = dataFilenameToAbsolutePath(dataDir, filePath);
			mq = null;
		} else {
			filePath = "";
		}
		if (mq != null) {
			mq.postProcess();
		}
		//
		//noinspection ConstantValue
		if (codec == null) {
			internalCodec = RtpPacketType.UNKNOWN;
			return;
		}

		MqPacketCodec tmpMqPktCodec = switch (codec) {
				case AACLC -> MqPacketCodec.AACLC;
				case AC3 -> MqPacketCodec.AC3;
				case PCMA -> MqPacketCodec.PCMA;
				case PCMU -> MqPacketCodec.PCMU;
				case LPCM08U -> MqPacketCodec.LPCM08U;
				case LPCM16S -> MqPacketCodec.LPCM16S;
				//
				case MJPEG -> MqPacketCodec.MJPEG;
				case H264 -> MqPacketCodec.H264;
				case H265 -> MqPacketCodec.H265;
			};

		internalCodec = tmpMqPktCodec.convertToRtpPacketType(getAudioSamplerate(), getAudioChannelCount());

		//
		//noinspection ConstantValue
		internalVideoFps = FrameRateEnum.of(videoFps == null ? -1.0 : videoFps);
		//noinspection ConstantValue
		internalAudioSampleRate = SampleRateEnum.of(audioSamplerateHz == null ? -1 : audioSamplerateHz);
		//noinspection ConstantValue
		internalAudioChannelCount = (byte)(audioChannelCount == null ? -1 : audioChannelCount);
		//noinspection ConstantValue
		internalIsPcmAudioBigEndian = (isPcmAudioBigEndian != null && isPcmAudioBigEndian);
		if (getSourceType() == RtspProtoEsSourceType.ST_ES_FILE) {
			if (internalCodec == RtpPacketType.A_AAC) {
				internalAudioSamplesPerFrame = aacSamplesPerFrame;
			} else if (internalCodec == RtpPacketType.A_AC3) {
				internalAudioSamplesPerFrame = RtpConstants.RTP_SAMPLES_PER_FRAME_AC3_AUDIO;
			} else if (internalCodec.isPcmAudio()) {
				double tmpSampleIntvMs = 1000.0 / (double)internalAudioSampleRate.getSrHz();
				double tmpSpF = (double)RtpConstants.RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS / tmpSampleIntvMs;
				internalAudioSamplesPerFrame = (int)tmpSpF;
			}
		}
	}

	/**
	 * Validate the Elementary-Stream Source.
	 * @param mapStreamSourceIdIntToExt Map of Elementary-Stream Source IDs (internal) to their external representation
	 * @throws ConfigInvalidException If the Elementary-Stream Source is invalid
	 */
	void validate(
				@NonNull Map<@NonNull Integer, @NonNull String> mapStreamSourceIdIntToExt
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		//
		final String tmpExtSsId = mapStreamSourceIdIntToExt.get(getIdAsInt());
		final String errMsgSuffix = " for Elementary-Stream Source ID '" + tmpExtSsId + "'";

		if (getIdAsInt() < 0) {
			throw new ConfigInvalidException(FNC_NAME + ": Elementary-Stream Source has no ID");
		}

		//
		if (filePath.isBlank() && mq == null) {
			throw new ConfigInvalidException(FNC_NAME + ": No File Path / MQ found" + errMsgSuffix);
		}
		if (! filePath.isBlank() && mq != null) {
			throw new ConfigInvalidException(FNC_NAME + ": Cannot have both File Path and MQ" + errMsgSuffix);
		}
		if (! (filePath.isBlank() || Path.of(filePath).toFile().exists())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid File Path '" + filePath +
					"'" + errMsgSuffix + " - file not found");
		}
		if (mq != null) {
			mq.validate(tmpExtSsId);
		}

		// ----------------------------------------------------

		if (getSourceType() != RtspProtoEsSourceType.ST_ES_FILE) {
			return;
		}
		//noinspection ConstantValue
		if (internalCodec == null || internalCodec == RtpPacketType.UNKNOWN) {
			throw new ConfigInvalidException(FNC_NAME + ": No (valid) Codec defined" + errMsgSuffix);
		}
		if (! (internalCodec.isAudio() || internalCodec.isVideo())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Codec" + errMsgSuffix);
		}
		if (internalCodec.isVideo() && getVideoFps() == FrameRateEnum.UNKNOWN) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Video FPS" + errMsgSuffix);
		}
		if (internalCodec.isPcmAudio() && getAudioSamplerate() == SampleRateEnum.UNKNOWN) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid PCM Audio Sample Rate" + errMsgSuffix +
					" (PCM needs a valid Sample Rate)");
		}
		if (internalCodec.isPcmAudio() &&
				(getAudioChannelCount() < 1 || getAudioChannelCount() > RtpConstants.RTP_AUDIO_CHANNELS_MAX)) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid PCM Audio Channel Count" + errMsgSuffix +
					" (PCM: min=1, max=" + RtpConstants.RTP_AUDIO_CHANNELS_MAX +
					", is=" + Integer.toUnsignedString(getAudioChannelCount()) + ")");
		}
		if (internalCodec.isPcmMonoAudio() && getAudioChannelCount() != 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid PCM Audio Channel Count" + errMsgSuffix +
					" (should be mono)");
		}
		if (internalCodec.isPcmStereoAudio() && getAudioChannelCount() != 2) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid PCM Audio Channel Count" + errMsgSuffix +
					" (should be stereo)");
		}
		if (internalCodec == RtpPacketType.A_OPUS && (getAudioChannelCount() < 1 || getAudioChannelCount() > 2)) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Opus Audio Channel Count" + errMsgSuffix +
					" (must be mono or stereo)");
		}
		if (internalCodec.isPcmAudio() && getAudioSamplesPerFrame() < 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid PCM Audio Samples Per Frame" + errMsgSuffix +
					" (needs to be positive)");
		}

		//
		if (enabled && internalCodec == RtpPacketType.A_AAC) {
			switch (aacSamplesPerFrame) {
				case RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1:
				case RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2:
				case RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD:
					break;
				default:
					throw new ConfigInvalidException(FNC_NAME + ": Invalid AAC Samples Per Frame" +
							errMsgSuffix + " (allowed values: " +
							RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1 + ", " +
							RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2 + ", " +
							RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD + ")");
			}
		}

		//
		ReadEsFileMeta.readEsFileMeta(tmpExtSsId, this);
	}

	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull String buildMsSourceUriForErrorMsgs(@NonNull URI msSourceUri) {
		if (! ("http".equals(msSourceUri.getScheme()) || "https".equals(msSourceUri.getScheme()))) {
			return msSourceUri.toString();
		}
		String tmpProto = msSourceUri.getScheme();
		String tmpHost = msSourceUri.getHost();
		int tmpPort = msSourceUri.getPort();
		String tmpPath = msSourceUri.getPath();
		String tmpQuery = msSourceUri.getQuery();
		URI cleanedUp = URI.create(tmpProto + "://" + tmpHost + (tmpPort > 0 ? ":" + tmpPort : "") +
				tmpPath + (tmpQuery != null ? "?" + tmpQuery : ""));
		return cleanedUp.toString()
				.replace("http://", "rtsp://")
				.replace("https://", "rtsps://");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Elementary-Stream Source has not been post-processed yet");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtpPacketType convertFfmpegVideoCodecToRtpPacketType(@NonNull FfmpegCodec ffmpegCodec)
			throws ConfigInvalidException {
		final String FNC_NAME = RtspConfigElementaryStreamSource.class.getSimpleName() + ".convertFfmpegVideoCodecToRtpPacketType()";

		return switch (ffmpegCodec) {
				case V_H264 -> RtpPacketType.V_H264;
				case V_H265 -> RtpPacketType.V_H265;
				case V_MJPEG -> RtpPacketType.V_MJPEG;
				case V_VP8 -> RtpPacketType.V_VP8;
				default -> throw new ConfigInvalidException(FNC_NAME + ": cannot convert Codec " + ffmpegCodec);
			};
	}

	private static @NonNull RtpPacketType convertFfmpegAudioCodecToRtpPacketType(
				@NonNull FfmpegCodec ffmpegCodec,
				@NonNull SampleRateEnum audioSamplerate,
				byte audioChannelCount
			) throws ConfigInvalidException {
		final String FNC_NAME = RtspConfigElementaryStreamSource.class.getSimpleName() + ".convertFfmpegAudioCodecToRtpPacketType()";

		return switch (ffmpegCodec) {
				case A_AAC -> RtpPacketType.A_AAC;
				case A_AC3 -> RtpPacketType.A_AC3;
				case A_OPUS -> RtpPacketType.A_OPUS;
				case A_PCM_ALAW -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_008000) {
							yield RtpPacketType.A_PCMA_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMA_VAR;
					}
				case A_PCM_MULAW -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_008000) {
							yield RtpPacketType.A_PCMU_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMU_VAR;
					}
				case A_PCM_U8 -> RtpPacketType.A_LINEAR_PCM_U08_VAR;
				case A_PCM_S16BE, A_PCM_S16LE -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_044100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_MONO;
						}
						if (audioChannelCount == 2 && audioSamplerate == SampleRateEnum.SR_044100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_STEREO;
						}
						yield RtpPacketType.A_LINEAR_PCM_S16_VAR;
					}
				default -> throw new ConfigInvalidException(FNC_NAME + ": cannot convert Codec " + ffmpegCodec);
			};
	}

}

package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_ffmpeg.demux.FfmpegStreamInfoAudio;
import org.tsitle.lib_ffmpeg.demux.FfmpegStreamInfoVideo;
import org.tsitle.lib_rtsp_mq.client.types.MqElementaryStreamSourceSettings;
import org.tsitle.lib_rtsp_mq.common.mqdata.MqPacketCodec;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.AudioAacParser;
import org.tsitle.lib_xrtxp.common.helpers.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSocketPortNr;
import org.tsitle.rtsp_server.avstreams.codec_a_aac.FrameGrabberAudioAacFromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_a_ac3.FrameGrabberAudioAc3FromEsFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.exceptions.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.threads.rtp.RtpConstants;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
	/** Is audio input big-endian? -- only when {@code filePath} is set. */
	@Expose
	private final @NonNull Boolean isAudioBigEndian;

	/** Only for AAC: Audio samples per frame -- only when {@code filePath} is set. */
	@Expose
	private @NonNull Integer aacSamplesPerFrame;
	/** Only for AAC: AudioSpecificConfig as hex string */
	@GsonAnnoExclude
	private @NonNull String aacAudioSpecificConfigHex;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;
	/** Internal use: Codec used for the stream */
	@GsonAnnoExclude
	private @NonNull RtpPacketType internalCodec;
	/** Internal use: Video frames per second */
	@GsonAnnoExclude
	private @NonNull FrameRateEnum internalVideoFps;
	/** Internal use: Audio samplerate */
	@GsonAnnoExclude
	private @NonNull SampleRateEnum internalAudioSampleRate;
	/** Internal use: Audio channel count */
	@GsonAnnoExclude
	private byte internalAudioChannelCount;
	/** Internal use: Is Audio input big-endian? */
	@GsonAnnoExclude
	private boolean internalIsAudioBigEndian;

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
		this.isAudioBigEndian = false;

		this.aacSamplesPerFrame = RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1;
		this.aacAudioSpecificConfigHex = "";

		this.internalHasBeenPostProcessed = false;
		this.internalCodec = RtpPacketType.UNKNOWN;
		this.internalVideoFps = FrameRateEnum.UNKNOWN;
		this.internalAudioSampleRate = SampleRateEnum.UNKNOWN;
		this.internalAudioChannelCount = -1;
		this.internalIsAudioBigEndian = false;

		this.mqDynamicCodec = RtpPacketType.UNKNOWN;
		this.mqDynamicVideoFps = FrameRateEnum.UNKNOWN;
		this.mqDynamicAudioSamplerateHz = SampleRateEnum.UNKNOWN;
		this.mqDynamicAudioChannelCount = -1;

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
				@NonNull FfmpegStreamInfoVideo streamInfo
			) {
		final String FNC_NAME = RtspConfigElementaryStreamSource.class.getSimpleName() + ".createFromDemuxedSubStreamVideo()";

		RtspConfigElementaryStreamSource resObj = new RtspConfigElementaryStreamSource();
		resObj.id = esSourceId;
		resObj.msSourceId = msSourceId;
		resObj.msSourceUri = msSourceUri;
		resObj.msSourceFfmpegStreamIx = streamInfo.streamIx;

		resObj.internalHasBeenPostProcessed = true;

		resObj.internalCodec = convertFfmpegVideoCodecToRtpPacketType(streamInfo.ffmpegCodec);

		resObj.internalVideoFps = FrameRateEnum.of(streamInfo.fps.toDouble());
		if (resObj.internalVideoFps == FrameRateEnum.UNKNOWN) {  // just in case
			throw new IllegalArgumentException(FNC_NAME + ": cannot handle FPS value " + streamInfo.fps);
		}

		return resObj;
	}

	static @NonNull RtspConfigElementaryStreamSource createFromDemuxedSubStreamAudio(
				int esSourceId,
				int msSourceId,
				@NonNull URI msSourceUri,
				@NonNull FfmpegStreamInfoAudio streamInfo
			) {
		final String FNC_NAME = RtspConfigElementaryStreamSource.class.getSimpleName() + ".createFromDemuxedSubStreamAudio()";

		RtspConfigElementaryStreamSource resObj = new RtspConfigElementaryStreamSource();
		resObj.id = esSourceId;
		resObj.msSourceId = msSourceId;
		resObj.msSourceUri = msSourceUri;
		resObj.msSourceFfmpegStreamIx = streamInfo.streamIx;

		resObj.internalHasBeenPostProcessed = true;

		resObj.internalCodec = convertFfmpegAudioCodecToRtpPacketType(
				streamInfo.ffmpegCodec,
				streamInfo.sampleRate,
				(byte)streamInfo.channelCount
			);

		resObj.internalAudioSampleRate = SampleRateEnum.of(streamInfo.sampleRate.getSrHz());
		if (resObj.internalAudioSampleRate == SampleRateEnum.UNKNOWN) {  // just in case
			throw new IllegalArgumentException(FNC_NAME + ": cannot handle SR value " + streamInfo.sampleRate);
		}
		resObj.internalAudioChannelCount = (byte)streamInfo.channelCount;
		if (resObj.internalAudioChannelCount < 1 || resObj.internalAudioChannelCount > 10) {  // just in case
			throw new IllegalArgumentException(FNC_NAME + ": cannot handle ChannelCount value " + streamInfo.channelCount);
		}
		resObj.internalIsAudioBigEndian = (streamInfo.ffmpegCodec == FfmpegCodec.A_PCM_S16BE);

		resObj.aacSamplesPerFrame = streamInfo.aacSamplesPerFrame;
		resObj.aacAudioSpecificConfigHex = streamInfo.aacAudioSpecificConfigHex;

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
				case ST_DEMUX_MS_FILE -> Objects.requireNonNull(msSourceUri);
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

	public @NonNull RtspConfigEsSourceType getSourceType() {
		final String FNC_NAME = getClass().getSimpleName() + ".getSourceType()";

		checkPostProcessed();
		if (! filePath.isBlank()) {
			return RtspConfigEsSourceType.ST_ES_FILE;
		}
		if (mq != null) {
			return RtspConfigEsSourceType.ST_ES_MQ;
		}
		if (msSourceId != null && msSourceFfmpegStreamIx != null) {
			return RtspConfigEsSourceType.ST_DEMUX_MS_FILE;
		}
		throw new IllegalStateException(FNC_NAME + ": could not identify Source Type");
	}

	public synchronized @NonNull RtpPacketType getCodec() {
		checkPostProcessed();
		if (getSourceType() == RtspConfigEsSourceType.ST_ES_MQ) {
			// the actual codec will be determined dynamically when reading from a MQ
			return mqDynamicCodec;
		}
		//noinspection ConstantValue
		return (internalCodec == null ? RtpPacketType.UNKNOWN : internalCodec);
	}

	public synchronized @NonNull FrameRateEnum getVideoFps() {
		checkPostProcessed();
		if (getSourceType() == RtspConfigEsSourceType.ST_ES_MQ) {
			// the actual FPS doesn't matter when reading from a MQ, but it will be determined dynamically when reading from a MQ
			return mqDynamicVideoFps;
		}
		return internalVideoFps;
	}

	public synchronized @NonNull SampleRateEnum getAudioSamplerate() {
		checkPostProcessed();
		if (getSourceType() == RtspConfigEsSourceType.ST_ES_MQ) {
			// the actual samplerate will be determined dynamically when reading from a MQ
			return mqDynamicAudioSamplerateHz;
		}
		return internalAudioSampleRate;
	}

	public synchronized byte getAudioChannelCount() {
		checkPostProcessed();
		if (getSourceType() == RtspConfigEsSourceType.ST_ES_MQ) {
			// the actual channel count will be determined dynamically when reading from a MQ
			return mqDynamicAudioChannelCount;
		}
		return internalAudioChannelCount;
	}

	/** Get audio samples per frame as required for RTP. */
	public int getRtpAudioSamplesPerFrame(double videoFpsAsDbl) {
		checkPostProcessed();
		//
		if (getSourceType() == RtspConfigEsSourceType.ST_ES_MQ) {
			return 1;  // the actual value doesn't matter when reading from a MQ
		}
		if (videoFpsAsDbl < 0.1) {
			throw new IllegalArgumentException("videoFpsAsDbl must be positive");
		}
		if (internalAudioSampleRate == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException("audioSamplerateHz must be valid");
		}
		/*
		 * 25 fps ^= 1 frame each 40 ms
		 * 8000 samples/sec ^= 1 sample each 0.125 ms
		 * 40 ms / 0.125 ms == 320 samples per frame
		 *
		 * 15 fps ^= 1 frame each 66.7 ms
		 * 8000 samples/sec ^= 1 sample each 0.125 ms
		 * 66.7 ms / 0.125 ms == 533 samples per frame
		 */
		double videoFrameIntervalMs = 1000.0 / videoFpsAsDbl;
		double audioSampleIntervalMs = 1000.0 / internalAudioSampleRate.getSrHz();
		int resI = (int)(videoFrameIntervalMs / audioSampleIntervalMs);
		return Math.max(1, resI);
	}

	public boolean getIsAudioBigEndian() {
		checkPostProcessed();
		if (getSourceType() == RtspConfigEsSourceType.ST_ES_MQ) {
			return true;  // when reading from a MQ, the audio data is expected to be big-endian
		}
		return internalIsAudioBigEndian;
	}

	public synchronized int getAacSamplesPerFrame() {
		checkPostProcessed();
		return aacSamplesPerFrame;
	}

	/**
	 * Only for AAC: Get AudioSpecificConfig as a hex string for SDP.
	 * @return AudioSpecificConfig as hex string
	 */
	public @NonNull String getAacAudioSpecificConfigHexStr() {
		checkPostProcessed();
		return aacAudioSpecificConfigHex;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void setMqDynamicCodec(@NonNull RtpPacketType value) { this.mqDynamicCodec = value; }

	public synchronized void setMqDynamicVideoFps(@NonNull FrameRateEnum value) { this.mqDynamicVideoFps = value; }

	public synchronized void setMqDynamicAudioSamplerateHz(@NonNull SampleRateEnum value) { this.mqDynamicAudioSamplerateHz = value; }

	public synchronized void setMqDynamicAudioChannelCount(byte value) { this.mqDynamicAudioChannelCount = value; }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void setIdAsInt(int id) { this.id = id; }

	static @NonNull String dataFilenameToAbsolutePath(@NonNull Path dataDir, @NonNull String dataFn) {
		return Path.of(dataDir.toAbsolutePath().toString(), dataFn.strip()).toString();
	}

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
		internalIsAudioBigEndian = (isAudioBigEndian != null && isAudioBigEndian);
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
		String tmpExtSsId = mapStreamSourceIdIntToExt.get(getIdAsInt());

		if (getIdAsInt() < 0) {
			throw new ConfigInvalidException(FNC_NAME + ": Elementary-Stream Source has no ID");
		}

		//
		if (filePath.isBlank() && mq == null) {
			throw new ConfigInvalidException(FNC_NAME + ": No file path / MQ found for Elementary-Stream Source ID '" +
					tmpExtSsId + "'");
		}
		if (! (filePath.isBlank() || Path.of(filePath).toFile().exists())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid file path '" + filePath +
					"' for Elementary-Stream Source ID '" + tmpExtSsId + "' - file not found");
		}
		if (mq != null) {
			mq.validate(tmpExtSsId);
		}

		// ----------------------------------------------------

		if (getSourceType() != RtspConfigEsSourceType.ST_ES_FILE) {
			return;
		}
		//noinspection ConstantValue
		if (internalCodec == null || internalCodec == RtpPacketType.UNKNOWN) {
			throw new ConfigInvalidException(FNC_NAME + ": No (valid) Codec defined for Elementary-Stream Source ID '" +
					tmpExtSsId + "'");
		}
		if (! (internalCodec.isAudio() || internalCodec.isVideo())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Codec for Elementary-Stream Source ID '" +
					tmpExtSsId + "'");
		}
		if (internalCodec.isVideo() && getVideoFps() == FrameRateEnum.UNKNOWN) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Video FPS for Elementary-Stream Source ID '" +
					tmpExtSsId + "'");
		}
		if (internalCodec.isPcmAudio() && getAudioSamplerate() == SampleRateEnum.UNKNOWN) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Sample Rate for Elementary-Stream Source ID '" +
					tmpExtSsId + "' (PCM needs a valid Sample Rate)");
		}
		if (internalCodec.isPcmAudio() && getAudioChannelCount() < 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Channel Count for Elementary-Stream Source ID '" +
					tmpExtSsId + "' (PCM: min=1, is=" + Integer.toUnsignedString(getAudioChannelCount()) + ")");
		}
		if (internalCodec.isPcmAudio() && (getAudioChannelCount() < 1 || getAudioChannelCount() > 2)) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Channel Count for Elementary-Stream Source ID '" +
					tmpExtSsId + "' (PCM: min=1, max=2, is=" + Integer.toUnsignedString(getAudioChannelCount()) + ")");
		}
		if (internalCodec.isMonoAudio() && getAudioChannelCount() != 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Channel Count for Elementary-Stream Source ID '" +
					tmpExtSsId + "' (should be mono)");
		}
		if (internalCodec.isStereoAudio() && getAudioChannelCount() != 2) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Channel Count for Elementary-Stream Source ID '" +
					tmpExtSsId + "' (should be stereo)");
		}

		//
		if (enabled && internalCodec == RtpPacketType.A_AAC) {
			switch (aacSamplesPerFrame) {
				case RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1:
				case RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2:
				case RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD:
					break;
				default:
					throw new ConfigInvalidException(FNC_NAME + ": Invalid AAC Samples Per Frame " +
							"for Elementary-Stream Source ID '" + tmpExtSsId + "' (allowed values: " +
							RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1 + ", " +
							RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2 + ", " +
							RtpConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD + ")");
			}
			//
			readAacHeader(getIdAsProtoId(), tmpExtSsId);
		} else if (enabled && internalCodec == RtpPacketType.A_AC3) {
			readAc3Header(getIdAsProtoId(), tmpExtSsId);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Elementary-Stream Source has not been post-processed yet");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void readAacHeader(@NonNull RtspProtoIdEsSource internalIdEsSource, @NonNull String extEsId)
			throws ConfigInvalidException {
		try (AvStreamIncomingFromEsFile avStreamIncoming = new AvStreamIncomingFromEsFile(internalIdEsSource, getInputUri())) {
			BufferExt tmpBuf = new BufferExt();
			FrameGrabberAudioAacFromEsFile asoAac = new FrameGrabberAudioAacFromEsFile(avStreamIncoming);
			TimestampEpochNs tmpStTimestamp = TimestampEpochNs.ofEmpty();
			asoAac.getNextFrame(tmpBuf, tmpStTimestamp);

			AudioAacInfo aacInfo = AudioAacParser.parseAdtsHeader(tmpBuf);

			if (aacInfo.audioObjectType != AudioAacInfo.AudioObjectType.AAC_LC) {
				throw new ConfigInvalidException("Unsupported AAC AudioObjectType " + aacInfo.audioObjectType +
						" for Elementary-Stream Source ID '" + extEsId + "'");
			}
			if (aacInfo.samplerate == AudioAacInfo.Samplerate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AAC Samplerate for Elementary-Stream Source ID '" + extEsId + "'");
			}
			if (getAudioSamplerate() != SampleRateEnum.UNKNOWN &&
					SampleRateEnum.of(aacInfo.samplerate.getHz()) != getAudioSamplerate()) {
				throw new ConfigInvalidException("AAC Samplerate mismatch for Elementary-Stream Source ID '" + extEsId + "' (" +
						"config=" + getAudioSamplerate().getSrHz() + ", fileHeader=" + aacInfo.samplerate.getHz() + ")");
			}
			internalAudioSampleRate = SampleRateEnum.of(aacInfo.samplerate.getHz());
			if (getAudioChannelCount() > 0 && aacInfo.channelConfiguration != getAudioChannelCount()) {
				throw new ConfigInvalidException("AAC ChannelCount mismatch for Elementary-Stream Source ID '" + extEsId + "' (" +
						"config=" + getAudioChannelCount() + ", fileHeader=" + aacInfo.channelConfiguration + ")");
			}
			internalAudioChannelCount = (byte)aacInfo.channelConfiguration;

			internalIsAudioBigEndian = false;

			aacAudioSpecificConfigHex = aacInfo.sdpFmtpConfigHex;
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from AAC file for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse AAC header for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		}
	}

	private void readAc3Header(@NonNull RtspProtoIdEsSource internalIdEsSource, @NonNull String extEsId)
			throws ConfigInvalidException {
		try (AvStreamIncomingFromEsFile avStreamIncoming = new AvStreamIncomingFromEsFile(internalIdEsSource, getInputUri())) {
			BufferExt tmpBuf = new BufferExt();
			FrameGrabberAudioAc3FromEsFile asoAc3 = new FrameGrabberAudioAc3FromEsFile(avStreamIncoming);
			TimestampEpochNs tmpStTimestamp = TimestampEpochNs.ofEmpty();
			asoAc3.getNextFrame(tmpBuf, tmpStTimestamp);

			AudioAc3Info ac3Info = AudioAc3Parser.parseAc3Header(tmpBuf);

			if (ac3Info.bitrate == AudioAc3Info.Bitrate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AC-3 Bitrate for Elementary-Stream Source ID '" + extEsId + "'");
			}
			if (ac3Info.samplerate == AudioAc3Info.Samplerate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AC-3 Samplerate for Elementary-Stream Source ID '" + extEsId + "'");
			}
			if (getAudioSamplerate() != SampleRateEnum.UNKNOWN &&
					SampleRateEnum.of(ac3Info.samplerate.getHz()) != getAudioSamplerate()) {
				throw new ConfigInvalidException("AC-3 Samplerate mismatch for Elementary-Stream Source ID '" + extEsId + "' (" +
						"config=" + getAudioSamplerate().getSrHz() + ", fileHeader=" + ac3Info.samplerate.getHz() + ")");
			}
			internalAudioSampleRate = SampleRateEnum.of(ac3Info.samplerate.getHz());
			if (getAudioChannelCount() > 0 && ac3Info.audioCodingMode.getChannelCount() != getAudioChannelCount()) {
				throw new ConfigInvalidException("AC-3 ChannelCount mismatch for Elementary-Stream Source ID '" + extEsId + "' (" +
						"config=" + getAudioChannelCount() + ", fileHeader=" + ac3Info.audioCodingMode.getChannelCount() + ")");
			}
			internalAudioChannelCount = (byte)ac3Info.audioCodingMode.getChannelCount();

			internalIsAudioBigEndian = false;
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from AC-3 file for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse AC-3 header for Elementary-Stream Source ID '" + extEsId + "': " +
					e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull RtpPacketType convertFfmpegVideoCodecToRtpPacketType(@NonNull FfmpegCodec ffmpegCodec) {
		final String FNC_NAME = RtspConfigElementaryStreamSource.class.getSimpleName() + ".convertFfmpegVideoCodecToRtpPacketType()";

		return switch (ffmpegCodec) {
				case V_H264 -> RtpPacketType.V_H264;
				case V_H265 -> RtpPacketType.V_H265;
				case V_MJPEG -> RtpPacketType.V_MJPEG;
				default -> throw new IllegalArgumentException(FNC_NAME + ": cannot convert Codec " + ffmpegCodec);
			};
	}

	private static @NonNull RtpPacketType convertFfmpegAudioCodecToRtpPacketType(
				@NonNull FfmpegCodec ffmpegCodec,
				@NonNull SampleRateEnum audioSamplerate,
				byte audioChannelCount
			) {
		final String FNC_NAME = RtspConfigElementaryStreamSource.class.getSimpleName() + ".convertFfmpegAudioCodecToRtpPacketType()";

		return switch (ffmpegCodec) {
				case A_AAC -> RtpPacketType.A_AAC;
				case A_AC3 -> RtpPacketType.A_AC3;
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
				default -> throw new IllegalArgumentException(FNC_NAME + ": cannot convert Codec " + ffmpegCodec);
			};
	}

}

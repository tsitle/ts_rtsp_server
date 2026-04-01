package org.tsitle.rtsp.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.AudioAacInfo;
import org.tsitle.rtsp.avdata.AudioAacParser;
import org.tsitle.rtsp.avstreams.AudioStreamOutgoingAacFromFile;
import org.tsitle.rtsp.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

/**
 * Stream Source within an Input Source for RTSP streams.
 */
public class RtspStreamSource {

	/** Stream Source ID */
	@GsonAnnoExclude
	private @NonNull Integer id;
	/** Is this Stream Source enabled? (default: true) */
	@Expose
	private @NonNull Boolean enabled;
	/** Path to the media file -- either {@code filePath} or {@code mqUrl} must be set, but not both. */
	@Expose
	private @NonNull String filePath;
	/** URL of the Message Queue for the media stream -- either {@code filePath} or {@code mqUrl} must be set, but not both. */
	@Expose
	private @NonNull String mqUrl;
	/** Codec used for the stream -- only when {@code filePath} is set. */
	@Expose
	private final @NonNull ConfigSsCodec codec;
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
	private final @NonNull Integer aacSamplesPerFrame;
	/** Only for AAC: AudioSpecificConfig as hex string */
	@GsonAnnoExclude
	private @NonNull String aacAudioSpecificConfigHex;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed = false;
	/** Internal use: Codec used for the stream */
	@GsonAnnoExclude
	private @NonNull RtpPacketType internalCodec;

	/** for MQs: Codec */
	@GsonAnnoExclude
	private RtpPacketType mqDynamicCodec = RtpPacketType.UNKNOWN;
	/** for MQs: Video frames per second */
	@GsonAnnoExclude
	private double mqDynamicVideoFps = -1.0;
	/** for MQs: Audio samplerate in Hz */
	@GsonAnnoExclude
	private int mqDynamicAudioSamplerateHz = -1;
	/** for MQs: Audio channel count */
	@GsonAnnoExclude
	private byte mqDynamicAudioChannelCount = -1;

	public RtspStreamSource() {
		this.id = -1;
		this.enabled = true;
		this.filePath = "";
		this.mqUrl = "";
		//noinspection DataFlowIssue
		this.codec = null;
		this.videoFps = -1.0;
		this.audioSamplerateHz = -1;
		this.audioChannelCount = -1;
		this.isAudioBigEndian = false;

		this.aacSamplesPerFrame = RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1;
		this.aacAudioSpecificConfigHex = "";

		this.internalCodec = RtpPacketType.UNKNOWN;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getId() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (id == null ? -1 : id);
	}
	public void setId(int id) { this.id = id; }

	public boolean getEnabled() {
		checkPostProcessed();
		return enabled;
	}

	public @NonNull URI getInputUri() {
		checkPostProcessed();
		if (! filePath.isBlank()) {
			return URI.create("file:" + filePath);
		}
		return URI.create("https://" + mqUrl);
	}

	public boolean getIsSourceFromFile() {
		checkPostProcessed();
		return (! filePath.isBlank());
	}

	public boolean getIsSourceFromMq() {
		checkPostProcessed();
		return filePath.isBlank();
	}

	public synchronized @NonNull RtpPacketType getCodec() {
		checkPostProcessed();
		if (getIsSourceFromMq()) {
			// the actual codec will be determined dynamically when reading from a MQ
			return mqDynamicCodec;
		}
		//noinspection ConstantValue
		return (internalCodec == null ? RtpPacketType.UNKNOWN : internalCodec);
	}

	public synchronized double getVideoFps() {
		checkPostProcessed();
		if (getIsSourceFromMq()) {
			// the actual FPS doesn't matter when reading from a MQ, but it will be determined dynamically when reading from a MQ
			return mqDynamicVideoFps;
		}
		//noinspection ConstantValue
		return (videoFps == null ? -1.0 : videoFps);
	}

	public synchronized int getAudioSamplerateHz() {
		checkPostProcessed();
		if (getIsSourceFromMq()) {
			// the actual samplerate will be determined dynamically when reading from a MQ
			return mqDynamicAudioSamplerateHz;
		}
		//noinspection ConstantValue
		return (audioSamplerateHz == null ? -1 : audioSamplerateHz);
	}

	public synchronized byte getAudioChannelCount() {
		checkPostProcessed();
		if (getIsSourceFromMq()) {
			// the actual channel count will be determined dynamically when reading from a MQ
			return mqDynamicAudioChannelCount;
		}
		//noinspection ConstantValue
		return (byte)(audioChannelCount == null ? -1 : audioChannelCount);
	}

	/** Get audio samples per frame as required for RTP. */
	public int getRtpAudioSamplesPerFrame(double videoFps) {
		checkPostProcessed();
		//
		if (getIsSourceFromMq()) {
			return 1;  // the actual value doesn't matter when reading from a MQ
		}
		if (videoFps <= 0) {
			throw new IllegalArgumentException("videoFps must be positive");
		}
		//noinspection ConstantValue
		if (audioSamplerateHz == null || audioSamplerateHz <= 0) {
			throw new IllegalArgumentException("audioSamplerateHz must be positive");
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
		double videoFrameIntervalMs = 1000.0 / videoFps;
		double audioSampleIntervalMs = 1000.0 / audioSamplerateHz;
		int resI = (int)(videoFrameIntervalMs / audioSampleIntervalMs);
		return Math.max(1, resI);
	}

	public boolean getIsAudioBigEndian() {
		checkPostProcessed();
		if (getIsSourceFromMq()) {
			return true;  // when reading from a MQ, the audio data is expected to be big-endian
		}
		//noinspection ConstantValue
		return (isAudioBigEndian != null && isAudioBigEndian);
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

	public synchronized void setMqDynamicVideoFps(double value) { this.mqDynamicVideoFps = value; }

	public synchronized void setMqDynamicAudioSamplerateHz(int value) { this.mqDynamicAudioSamplerateHz = value; }

	public synchronized void setMqDynamicAudioChannelCount(byte value) { this.mqDynamicAudioChannelCount = value; }

	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull String dataFilenameToAbsolutePath(@NonNull Path dataDir, @NonNull String dataFn) {
		return Path.of(dataDir.toAbsolutePath().toString(), dataFn.strip()).toString();
	}

	/**
	 * Post-process the Stream Source.
	 * @param dataDir Directory containing the media files
	 */
	public void postProcess(@NonNull Path dataDir) {
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
			mqUrl = "";
		} else {
			filePath = "";
		}
		if (filePath.isBlank()) {
			//noinspection ConstantValue
			if (mqUrl == null || mqUrl.isBlank()) {
				mqUrl = "";
			}
		}
		//
		//noinspection ConstantValue
		if (codec == null) {
			internalCodec = RtpPacketType.UNKNOWN;
			return;
		}
		internalCodec = codec.convertToRtpPacketType(getAudioSamplerateHz(), getAudioChannelCount());
	}

	/**
	 * Validate the Stream Source.
	 * @param mapStreamSourceIdIntToExt Map of Stream Source IDs (internal) to their external representation
	 * @throws ConfigInvalidException If the Stream Source is invalid
	 */
	public void validate(
				@NonNull Map<@NonNull Integer, @NonNull String> mapStreamSourceIdIntToExt
			) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		//
		String tmpExtSsId = mapStreamSourceIdIntToExt.get(getId());

		if (getId() < 0) {
			throw new ConfigInvalidException(FNC_NAME + ": Stream Source has no ID");
		}

		//
		if (filePath.isBlank() && mqUrl.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No file path / MQ URL found for Stream Source ID '" + tmpExtSsId + "'");
		}
		if (! (filePath.isBlank() || Path.of(filePath).toFile().exists())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid file path '" + filePath +
					"' for Stream Source ID '" + tmpExtSsId + "' - file not found");
		}
		if (! mqUrl.isBlank()) {
			validateMqUrl(FNC_NAME, tmpExtSsId, mqUrl);
			//
			final String tmpUriAuth = getInputUri().getUserInfo();
			final String tmpUriHost = getInputUri().getHost();
			final int tmpUriPort = getInputUri().getPort();
			final String tmpUriPath = getInputUri().getPath();
			mqUrl = tmpUriAuth + "@" + tmpUriHost + ":" + (tmpUriPort != -1 ? tmpUriPort : 443) + tmpUriPath;
		}

		//
		if (getIsSourceFromFile()) {
			//noinspection ConstantValue
			if (internalCodec == null || internalCodec == RtpPacketType.UNKNOWN) {
				throw new ConfigInvalidException(FNC_NAME + ": No (valid) codec defined for Stream Source ID '" + tmpExtSsId + "'");
			}
			if (! (internalCodec.isAudio() || internalCodec.isVideo())) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid codec for Stream Source ID '" + tmpExtSsId + "'");
			}
			if (internalCodec.isVideo() && getVideoFps() < 1.0) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Video FPS for Stream Source ID '" + tmpExtSsId + "'");
			}
			if (internalCodec.isAudio() && getAudioSamplerateHz() < 1) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Samplerate for Stream Source ID '" + tmpExtSsId + "'");
			}
			if (internalCodec.isAudio() && (getAudioChannelCount() < 1 || getAudioChannelCount() > 2)) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Channel Count for Stream Source ID '" + tmpExtSsId + "'");
			}
			if (internalCodec.isMonoAudio() && getAudioChannelCount() != 1) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Channel Count for Stream Source ID '" + tmpExtSsId + "'");
			}
			if (internalCodec.isStereoAudio() && getAudioChannelCount() != 2) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Channel Count for Stream Source ID '" + tmpExtSsId + "'");
			}

			//
			if (enabled && internalCodec == RtpPacketType.A_AAC) {
				switch (aacSamplesPerFrame) {
					case RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1:
					case RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2:
					case RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD:
						break;
					default:
						throw new ConfigInvalidException(FNC_NAME + ": Invalid AAC Samples Per Frame " +
								"for Stream Source ID '" + tmpExtSsId + "' (allowed values: " +
								RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1 + ", " +
								RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2 + ", " +
								RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD + ")");
				}
				//
				readAacHeader(getId(), tmpExtSsId);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Stream Source has not been post-processed yet");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void validateMqUrl(@NonNull String fncName, @NonNull String extSsId, @NonNull String mqUrl) throws ConfigInvalidException {
		final String errMsgPrefix = fncName + ": Invalid MQ URL '" + mqUrl + "' for Stream Source ID '" + extSsId + "' - ";

		if (! mqUrl.endsWith(".mq")) {
			throw new ConfigInvalidException(errMsgPrefix + "must end with '.mq'");
		}
		final URI tmpUri = getInputUri();
		if (tmpUri.getUserInfo() == null || tmpUri.getUserInfo().isBlank()) {
			throw new ConfigInvalidException(errMsgPrefix + "must contain user info");
		}
		if (! tmpUri.getUserInfo().contains(":")) {
			throw new ConfigInvalidException(errMsgPrefix + "must contain username and password separated by colon");
		}
		final String tmpAuthUser = tmpUri.getUserInfo().split(":")[0];
		if (tmpAuthUser.isBlank()) {
			throw new ConfigInvalidException(errMsgPrefix + "must contain username");
		}
		final String tmpAuthPw = tmpUri.getUserInfo().split(":")[1];
		if (tmpAuthPw.isBlank()) {
			throw new ConfigInvalidException(errMsgPrefix + "must contain password");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void readAacHeader(int intSsId, @NonNull String extSsId) throws ConfigInvalidException {
		try (AvStreamIncomingFromFile avStreamIncoming = new AvStreamIncomingFromFile(intSsId, getInputUri())) {
			BufferExt tmpBuf = new BufferExt();
			AudioStreamOutgoingAacFromFile asoAac = new AudioStreamOutgoingAacFromFile(avStreamIncoming);
			asoAac.getNextFrame(tmpBuf);

			AudioAacInfo aacInfo = AudioAacParser.parseAdtsHeader(tmpBuf);

			if (aacInfo.audioObjectType != AudioAacInfo.AudioObjectType.AAC_LC) {
				throw new ConfigInvalidException("Unsupported AAC AudioObjectType " + aacInfo.audioObjectType +
						" for Stream Source ID '" + extSsId + "'");
			}
			if (aacInfo.samplerate == AudioAacInfo.Samplerate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AAC Samplerate for Stream Source ID '" + extSsId + "'");
			}
			if (aacInfo.samplerate.getHz() != getAudioSamplerateHz()) {
				throw new ConfigInvalidException("AAC Samplerate mismatch for Stream Source ID '" + extSsId + "' (" +
						"config=" + getAudioSamplerateHz() + ", fileHeader=" + aacInfo.samplerate.getHz() + ")");
			}
			if (aacInfo.channelConfiguration != getAudioChannelCount()) {
				throw new ConfigInvalidException("AAC ChannelCount mismatch for Stream Source ID '" + extSsId + "' (" +
						"config=" + getAudioChannelCount() + ", fileHeader=" + aacInfo.channelConfiguration + ")");
			}

			aacAudioSpecificConfigHex = aacInfo.sdpFmtpConfigHex;
		} catch (AvCannotOpenInputException | InputStreamIoException | InputStreamEosException e) {
			throw new ConfigInvalidException("Could not read from AAC file for Stream Source ID '" + extSsId + "': " +
					e.getMessage());
		} catch (AvInvalidCodecDataException e) {
			throw new ConfigInvalidException("Could not parse AAC header for Stream Source ID '" + extSsId + "': " +
					e.getMessage());
		}
	}

}

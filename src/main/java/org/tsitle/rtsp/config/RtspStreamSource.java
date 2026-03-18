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

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stream Source within an Input Source for RTSP streams.
 */
public class RtspStreamSource {

	/** Stream Source ID */
	@GsonAnnoExclude
	private @NonNull Integer id;
	/** Is this Stream Source enabled? */
	@Expose
	private @NonNull Boolean enabled;
	/** Path to the media file */
	@Expose
	private @NonNull String filePath;
	/** URL of the Message Queue for the media stream */
	@Expose
	private @NonNull String mqUrl;
	/** Codec used for the stream */
	@Expose
	private final @NonNull ConfigSsCodec codec;
	/** Video frames per second */
	@Expose
	private final @NonNull Double videoFps;
	/** Audio sample rate in Hz */
	@Expose
	private final @NonNull Integer audioSampleRateHz;
	/** Audio channel count */
	@Expose
	private final @NonNull Integer audioChannelCount;
	/** Is audio input big-endian? */
	@Expose
	private final @NonNull Boolean isAudioBigEndian;

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
	private final AtomicInteger mqDynamicCodec = new AtomicInteger(RtpPacketType.UNKNOWN.getValue());
	/** for MQs: Video frames per second */
	@GsonAnnoExclude
	private final AtomicInteger mqDynamicVideoFps = new AtomicInteger(-1);
	/** for MQs: Audio sample rate in Hz */
	@GsonAnnoExclude
	private final AtomicInteger mqDynamicAudioSampleRateHz = new AtomicInteger(-1);
	/** for MQs: Audio channel count */
	@GsonAnnoExclude
	private final AtomicInteger mqDynamicAudioChannelCount = new AtomicInteger(-1);

	public RtspStreamSource() {
		this.id = -1;
		this.enabled = true;
		this.filePath = "";
		this.mqUrl = "";
		//noinspection DataFlowIssue
		this.codec = null;
		this.videoFps = -1.0;
		this.audioSampleRateHz = -1;
		this.audioChannelCount = -1;
		this.isAudioBigEndian = false;

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
			return RtpPacketType.of((byte)mqDynamicCodec.get());
		}
		//noinspection ConstantValue
		return (internalCodec == null ? RtpPacketType.UNKNOWN : internalCodec);
	}

	public synchronized double getVideoFps() {
		checkPostProcessed();
		if (getIsSourceFromMq()) {
			// the actual FPS doesn't matter when reading from a MQ, but it will be determined dynamically when reading from a MQ
			return mqDynamicVideoFps.doubleValue();
		}
		//noinspection ConstantValue
		return (videoFps == null ? -1.0 : videoFps);
	}

	public synchronized int getAudioSampleRateHz() {
		checkPostProcessed();
		if (getIsSourceFromMq()) {
			// the actual samplerate will be determined dynamically when reading from a MQ
			return mqDynamicAudioSampleRateHz.get();
		}
		//noinspection ConstantValue
		return (audioSampleRateHz == null ? -1 : audioSampleRateHz);
	}

	public synchronized int getAudioChannelCount() {
		checkPostProcessed();
		if (getIsSourceFromMq()) {
			// the actual channel count will be determined dynamically when reading from a MQ
			return mqDynamicAudioChannelCount.get();
		}
		//noinspection ConstantValue
		return (audioChannelCount == null ? -1 : audioChannelCount);
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
		if (audioSampleRateHz == null || audioSampleRateHz <= 0) {
			throw new IllegalArgumentException("audioSampleRateHz must be positive");
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
		double audioSampleIntervalMs = 1000.0 / audioSampleRateHz;
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

	/**
	 * Only for AAC: Get AudioSpecificConfig as a hex string for SDP.
	 * @return AudioSpecificConfig as hex string
	 */
	public @NonNull String getAacAudioSpecificConfigHexStr() {
		checkPostProcessed();
		return aacAudioSpecificConfigHex;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void setMqDynamicCodec(@NonNull RtpPacketType value) { this.mqDynamicCodec.set(value.getValue()); }

	public synchronized void setMqDynamicVideoFps(int value) { this.mqDynamicVideoFps.set(value); }

	public synchronized void setMqDynamicAudioSampleRateHz(int value) { this.mqDynamicAudioSampleRateHz.set(value); }

	public synchronized void setMqDynamicAudioChannelCount(int value) { this.mqDynamicAudioChannelCount.set(value); }

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
		switch (codec) {
			case AACLC:
				internalCodec = RtpPacketType.A_AAC;
				break;
			case PCMU:
				if (getAudioChannelCount() == 1 && getAudioSampleRateHz() == 8000) {
					internalCodec = RtpPacketType.A_PCMU_8KHZ_MONO;
				} else {
					internalCodec = RtpPacketType.A_PCMU_VAR;
				}
				break;
			case LPCM08:
				internalCodec = RtpPacketType.A_LINEAR_PCM_U08_VAR;
				break;
			case LPCM16:
				if (getAudioChannelCount() == 1 && getAudioSampleRateHz() == 44100) {
					internalCodec = RtpPacketType.A_LINEAR_PCM_S16_441K_MONO;
				} else if (getAudioChannelCount() == 2 && getAudioSampleRateHz() == 44100) {
					internalCodec = RtpPacketType.A_LINEAR_PCM_S16_441K_STEREO;
				} else {
					internalCodec = RtpPacketType.A_LINEAR_PCM_S16_VAR;
				}
				break;
			case MJPEG:
				internalCodec = RtpPacketType.V_JPEG;
				break;
			case H264:
				internalCodec = RtpPacketType.V_H264;
				break;
			case H265:
				internalCodec = RtpPacketType.V_H265;
				break;
			default:
				internalCodec = RtpPacketType.UNKNOWN;
		}
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
			throw new ConfigInvalidException(FNC_NAME + ": Invalid ile path '" + filePath +
					"' for Stream Source ID '" + tmpExtSsId + "' does not exist");
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
			if (internalCodec.isAudio() && getAudioSampleRateHz() < 1) {
				throw new ConfigInvalidException(FNC_NAME + ": Invalid Audio Sample Rate for Stream Source ID '" + tmpExtSsId + "'");
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
			if (aacInfo.samplerate == AudioAacInfo.SampleRate.UNKNOWN) {
				throw new ConfigInvalidException("Could not parse AAC SampleRate for Stream Source ID '" + extSsId + "'");
			}
			if (aacInfo.samplerate.getHz() != getAudioSampleRateHz()) {
				throw new ConfigInvalidException("AAC SampleRate mismatch for Stream Source ID '" + extSsId + "' (" +
						"config=" + getAudioSampleRateHz() + ", fileHeader=" + aacInfo.samplerate.getHz() + ")");
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

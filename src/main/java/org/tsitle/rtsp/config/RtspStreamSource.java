package org.tsitle.rtsp.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.ConfigInvalidException;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;

import java.nio.file.Path;
import java.util.Map;

/**
 * Stream Source within an Input Source for RTSP streams.
 */
public class RtspStreamSource {

	/** Stream Source ID */
	@GsonAnnoExclude
	private @NonNull Integer id;
	/** Path to the media file */
	@Expose
	private @NonNull String filePath;
	/** Codec used for the stream */
	@Expose
	private final @NonNull ConfigSsCodec codec;
	/** Video frames per second */
	@Expose
	private final @NonNull Float videoFps;
	/** Audio sample rate in Hz */
	@Expose
	private final @NonNull Integer audioSampleRateHz;
	/** Audio channel count */
	@Expose
	private final @NonNull Integer audioChannelCount;
	/** Is audio input big-endian? */
	@Expose
	private final @NonNull Boolean isAudioBigEndian;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed = false;
	/** Internal use: Codec used for the stream */
	@GsonAnnoExclude
	private @NonNull RtpPacketType internalCodec;

	public RtspStreamSource() {
		this.id = -1;
		this.filePath = "";
		//noinspection DataFlowIssue
		this.codec = null;
		this.videoFps = -1.0f;
		this.audioSampleRateHz = -1;
		this.audioChannelCount = -1;
		this.isAudioBigEndian = false;

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

	public @NonNull String getFilePath() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (filePath == null ? "" : filePath.strip());
	}

	public @NonNull RtpPacketType getCodec() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (internalCodec == null ? RtpPacketType.UNKNOWN : internalCodec);
	}

	public float getVideoFps() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (videoFps == null ? -1.0f : videoFps);
	}

	public int getAudioSampleRateHz() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (audioSampleRateHz == null ? -1 : audioSampleRateHz);
	}

	public int getAudioChannelCount() {
		checkPostProcessed();
		//noinspection ConstantValue
		return (audioChannelCount == null ? -1 : audioChannelCount);
	}

	/** Get audio samples per frame as required for RTP. */
	public int getRtpAudioSamplesPerFrame(int videoFps) {
		checkPostProcessed();
		//
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
		//noinspection ConstantValue
		return (isAudioBigEndian != null && isAudioBigEndian);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the Stream Source.
	 * @param dataDir Directory containing the media files
	 */
	public void postProcess(@NonNull Path dataDir) {
		internalHasBeenPostProcessed = true;
		//
		filePath = Path.of(dataDir.toAbsolutePath().toString(), getFilePath()).toString();
		//
		//noinspection ConstantValue
		if (codec == null) {
			internalCodec = RtpPacketType.UNKNOWN;
			return;
		}
		switch (codec) {
			case MJPEG:
				internalCodec = RtpPacketType.V_JPEG;
				break;
			case H265:
				internalCodec = RtpPacketType.V_H265;
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

		if (getFilePath().isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No file path found for Stream Source ID '" + tmpExtSsId + "'");
		}
		if (! Path.of(getFilePath()).toFile().exists()) {
			throw new ConfigInvalidException(FNC_NAME + ": File path '" + getFilePath() +
					"' for Stream Source ID '" + tmpExtSsId + "' does not exist");
		}

		//noinspection ConstantValue
		if (internalCodec == null || internalCodec == RtpPacketType.UNKNOWN) {
			throw new ConfigInvalidException(FNC_NAME + ": No (valid) codec defined for Stream Source ID '" + tmpExtSsId + "'");
		}
		if (! (internalCodec.isAudio() || internalCodec.isVideo())) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid codec for Stream Source ID '" + tmpExtSsId + "'");
		}
		if (internalCodec.isVideo() && getVideoFps() < 1) {
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
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException("Stream Source has not been post-processed yet");
		}
	}

}

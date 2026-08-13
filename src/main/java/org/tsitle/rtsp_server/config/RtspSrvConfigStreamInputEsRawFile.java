package org.tsitle.rtsp_server.config;

import com.google.gson.annotations.Expose;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.DpConstants;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;
import org.tsitle.lib_xrtxp.common.types.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.exceptions.ConfigInvalidException;
import org.tsitle.rtsp_server.threads.rtp.RtpConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Raw File Elementary-Stream Source.
 */
public final class RtspSrvConfigStreamInputEsRawFile implements Cloneable {

	/** Path to the media file */
	@Expose
	private @NonNull String filePath;
	/** Codec used for the stream */
	@Expose
	private @Nullable ConfigEsCodec codec;
	/** Video frames per second */
	@Expose
	private @NonNull Double videoFps;
	/** Audio samplerate in Hz */
	@Expose
	private @NonNull Integer audioSamplerateHz;
	/** Audio channel count */
	@Expose
	private @NonNull Integer audioChannelCount;
	/** Is PCM Audio input big-endian? (default: false) */
	@Expose
	private @NonNull Boolean isPcmAudioBigEndian;

	/** Only for AAC: Audio samples per frame (only required when a non-default value is used) */
	@Expose
	private @NonNull Integer aacSamplesPerFrame;

	@GsonAnnoExclude
	private boolean internalHasBeenPostProcessed;

	public RtspSrvConfigStreamInputEsRawFile() {
		this.filePath = "";
		this.codec = null;
		this.videoFps = -1.0;
		this.audioSamplerateHz = -1;
		this.audioChannelCount = -1;
		this.isPcmAudioBigEndian = false;

		this.aacSamplesPerFrame = DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1;

		this.internalHasBeenPostProcessed = false;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull String getFilePath() {
		checkPostProcessed();
		return filePath;
	}

	public @NonNull URI getInputUri() {
		checkPostProcessed();
		if (! filePath.isBlank()) {
			return URI.create("file:" + filePath);
		}
		throw new IllegalStateException("filePath is blank");
	}

	public Optional<RtpPacketType> getCodec() {
		checkPostProcessed();
		if (codec == null) {
			return Optional.empty();
		}
		RtpPacketType resEn = codec.convertToRtpPacketType(getAudioSamplerate(), getAudioChannelCount());
		return Optional.of(resEn);
	}

	public @NonNull FrameRateEnum getVideoFps() {
		checkPostProcessed();
		return FrameRateEnum.of(videoFps);
	}

	public @NonNull SampleRateEnum getAudioSamplerate() {
		checkPostProcessed();
		return SampleRateEnum.of(audioSamplerateHz);
	}

	public byte getAudioChannelCount() {
		checkPostProcessed();
		return (byte)((int)audioChannelCount);
	}

	/**
	 * Get the number of audio samples per frame as required for RTP.
	 * @return Samples per frame or -1 if the value is not available
	 */
	public int getAudioSamplesPerFrame() {
		checkPostProcessed();
		if (getCodec().isEmpty()) {
			return -1;
		}
		RtpPacketType internalCodec = getCodec().orElseThrow();
		if (internalCodec == RtpPacketType.A_AAC) {
			return aacSamplesPerFrame;
		}
		if (internalCodec == RtpPacketType.A_AC3) {
			return DpConstants.DP_SAMPLES_PER_FRAME_AC3_AUDIO;
		}
		if (internalCodec == RtpPacketType.A_MP3) {
			return DpConstants.DP_SAMPLES_PER_FRAME_MP3_AUDIO;
		}
		if (internalCodec.isPcmAudio()) {
			double tmpSampleIntvMs = 1000.0 / (double)getAudioSamplerate().getSrHz();
			double tmpSpF = (double)RtpConstants.RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS / tmpSampleIntvMs;
			return (int)tmpSpF;
		}
		return -1;
	}

	public boolean getIsPcmAudioBigEndian() {
		checkPostProcessed();
		return isPcmAudioBigEndian;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull RtspSrvConfigStreamInputEsRawFile clone() {
		checkPostProcessed();
		try {
			RtspSrvConfigStreamInputEsRawFile clone = (RtspSrvConfigStreamInputEsRawFile)super.clone();
			//
			//noinspection ConstantValue
			clone.videoFps = (videoFps != null ? videoFps : -1.0);
			//noinspection ConstantValue
			clone.audioSamplerateHz = (audioSamplerateHz != null ? audioSamplerateHz : -1);
			//noinspection ConstantValue
			clone.audioChannelCount = (audioChannelCount != null ? audioChannelCount : 0);
			//noinspection ConstantValue
			clone.isPcmAudioBigEndian = (isPcmAudioBigEndian != null && isPcmAudioBigEndian);
			//noinspection ConstantValue
			clone.aacSamplesPerFrame = (aacSamplesPerFrame != null ? aacSamplesPerFrame : -1);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull String hashSum() {
		checkPostProcessed();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write(filePath.getBytes());
			baos.write(codec == null ? -1 : codec.ordinal());
			baos.write(String.format("%.5f", videoFps).getBytes());
			baos.write(audioSamplerateHz);
			baos.write(audioChannelCount);
			baos.write(isPcmAudioBigEndian ? 1 : 0);
			baos.write(aacSamplesPerFrame);
		} catch (IOException e) {
			// ignore
		}
		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspSrvConfigStreamInputEsRawFile that)) {
			return false;
		}
		return hashSum().equals(that.hashSum());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Post-process the configuration.
	 */
	void postProcess() {
		internalHasBeenPostProcessed = true;

 		//
		//noinspection ConstantValue
		if (filePath == null || filePath.isBlank()) {
			filePath = "";
		}
		//noinspection ConstantValue
		if (videoFps == null) {
			videoFps = -1.0;
		}
		//noinspection ConstantValue
		if (audioSamplerateHz == null) {
			audioSamplerateHz = -1;
		}
		//noinspection ConstantValue
		if (audioChannelCount == null) {
			audioChannelCount = 0;
		}
		//noinspection ConstantValue
		if (isPcmAudioBigEndian == null) {
			isPcmAudioBigEndian = false;
		}
		//noinspection ConstantValue
		if (aacSamplesPerFrame == null) {
			aacSamplesPerFrame = DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1;
		}
	}

	/**
	 * Validate the configuration.
	 * @throws ConfigInvalidException If the configuration is invalid
	 */
	void validate(@NonNull String extIdStr, @NonNull Path dataDirPath) throws ConfigInvalidException {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		checkPostProcessed();

		//
		final String errMsgSuffix = " for Sub-Stream Source ID '" + extIdStr + "'";

		//
		if (getCodec().isEmpty()) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Codec found" + errMsgSuffix);
		}

		//
		if (filePath.isBlank()) {
			throw new ConfigInvalidException(FNC_NAME + ": No File Path found" + errMsgSuffix);
		}
		if (! dataDirPath.resolve(filePath).toFile().exists()) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid File Path '" + filePath +
					"'" + errMsgSuffix + " - file not found");
		}
		filePath = dataDirPath.resolve(filePath).toString();

		// ----------------------------------------------------

		RtpPacketType internalCodec = getCodec().orElseThrow();

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
				(getAudioChannelCount() < 1 || getAudioChannelCount() > DpConstants.DP_PCM_AUDIO_CHANNELS_MAX)) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid PCM Audio Channel Count" + errMsgSuffix +
					" (PCM: min=1, max=" + DpConstants.DP_PCM_AUDIO_CHANNELS_MAX +
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
		if (internalCodec == RtpPacketType.A_OPUS &&
				(getAudioChannelCount() < 1 || getAudioChannelCount() > DpConstants.DP_OPUS_AUDIO_CHANNELS_MAX)) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid Opus Audio Channel Count" + errMsgSuffix +
					" (must be mono or stereo)");
		}
		if (internalCodec.isPcmAudio() && getAudioSamplesPerFrame() < 1) {
			throw new ConfigInvalidException(FNC_NAME + ": Invalid PCM Audio Samples Per Frame" + errMsgSuffix +
					" (needs to be positive)");
		}

		//
		if (internalCodec == RtpPacketType.A_AAC) {
			switch (aacSamplesPerFrame) {
				case DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1:
				case DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2:
				case DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD:
					break;
				default:
					throw new ConfigInvalidException(FNC_NAME + ": Invalid AAC Samples Per Frame" +
							errMsgSuffix + " (allowed values: " +
							DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1 + ", " +
							DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2 + ", " +
							DpConstants.DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD + ")");
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkPostProcessed() {
		if (! internalHasBeenPostProcessed) {
			throw new IllegalStateException(getClass().getSimpleName() + " object has not been post-processed yet");
		}
	}

}

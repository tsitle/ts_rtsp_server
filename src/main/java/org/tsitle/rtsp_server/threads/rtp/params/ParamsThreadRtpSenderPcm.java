package org.tsitle.rtsp_server.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

public final class ParamsThreadRtpSenderPcm implements Cloneable {

	/** Audio channel count */
	private int audioChannelCount;
	private boolean isSetAudioChannelCount;
	/** Audio bits per sample */
	private int audioBitsPerSample;
	private boolean isSetAudioBitsPerSample;
	/** Is audio input Big-Endian? */
	private boolean isAudioInputBigEndian;
	private boolean isSetAudioInputBigEndian;
	/** Audio codec */
	private @NonNull RtpPacketType audioCodec = RtpPacketType.UNKNOWN;
	private boolean isSetAudioCodec;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getAudioChannelCount() { return audioChannelCount; }
	public void setAudioChannelCount(int audioChannelCount) {
		this.audioChannelCount = audioChannelCount;
		this.isSetAudioChannelCount = true;
	}

	public int getAudioBitsPerSample() { return audioBitsPerSample; }
	public void setAudioBitsPerSample(int audioBitsPerSample) {
		this.audioBitsPerSample = audioBitsPerSample;
		this.isSetAudioBitsPerSample = true;
	}

	public boolean getIsAudioInputBigEndian() { return isAudioInputBigEndian; }
	public void setIsAudioInputBigEndian(boolean audioInputBigEndian) {
		isAudioInputBigEndian = audioInputBigEndian;
		this.isSetAudioInputBigEndian = true;
	}

	public @NonNull RtpPacketType getAudioCodec() { return audioCodec; }
	public void setAudioCodec(@NonNull RtpPacketType audioCodec) {
		this.audioCodec = audioCodec;
		this.isSetAudioCodec = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtpSenderPcm clone() {
		try {
			return (ParamsThreadRtpSenderPcm)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetAudioChannelCount, "audioChannelCount");
		requireIsSet(isSetAudioBitsPerSample, "audioBitsPerSample");
		requireIsSet(isSetAudioInputBigEndian, "audioInputBigEndian");
		requireIsSet(isSetAudioCodec, "audioCodec");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (audioChannelCount < 1 || audioChannelCount > 2) {  // @TODO
			throw new IllegalArgumentException(errPrefix + "audioChannelCount must be 1 or 2");
		}
		if (audioBitsPerSample != 8 && audioBitsPerSample != 16) {
			throw new IllegalArgumentException(errPrefix + "audioBitsPerSample must be 8 or 16");
		}
		requireNonNull(audioCodec, "audioCodec");
		if (! audioCodec.isPcmAudio()) {
			throw new IllegalArgumentException(errPrefix + "Unsupported Audio Codec: " + audioCodec);
		}
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtpSenderPcm.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static <X> void requireNonNull(X v, String name) {
		final String errPrefix = ParamsThreadRtpSenderPcm.class.getSimpleName() + ": ";

		if (v == null) {
			throw new IllegalArgumentException(errPrefix + name + " must not be null");
		}
	}

}

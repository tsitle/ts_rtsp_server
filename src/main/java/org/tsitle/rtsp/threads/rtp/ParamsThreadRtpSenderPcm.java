package org.tsitle.rtsp.threads.rtp;

import org.tsitle.rtsp.packets.rtp.RtpPacketType;

public class ParamsThreadRtpSenderPcm {

	/** Audio samples per frame as required for RTP */
	private int rtpAudioSpf;
	private boolean isSetRtpAudioSpf;
	/** Audio sample rate in Hz */
	private int audioSampleRateHz;
	private boolean isSetAudioSampleRateHz;
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
	private RtpPacketType audioCodec;
	private boolean isSetAudioCodec;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getRtpAudioSpf() { return rtpAudioSpf; }
	public void setRtpAudioSpf(int rtpAudioSpf) {
		this.rtpAudioSpf = rtpAudioSpf;
		this.isSetRtpAudioSpf = true;
	}

	public int getAudioSampleRateHz() { return audioSampleRateHz; }
	public void setAudioSampleRateHz(int audioSampleRateHz) {
		this.audioSampleRateHz = audioSampleRateHz;
		this.isSetAudioSampleRateHz = true;
	}

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

	public RtpPacketType getAudioCodec() { return audioCodec; }
	public void setAudioCodec(RtpPacketType audioCodec) {
		this.audioCodec = audioCodec;
		this.isSetAudioCodec = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public ParamsThreadRtpSenderPcm clone() {
		try {
			return (ParamsThreadRtpSenderPcm)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetRtpAudioSpf, "rtpAudioSpf");
		requireIsSet(isSetAudioSampleRateHz, "audioSampleRateHz");
		requireIsSet(isSetAudioChannelCount, "audioChannelCount");
		requireIsSet(isSetAudioBitsPerSample, "audioBitsPerSample");
		requireIsSet(isSetAudioInputBigEndian, "audioInputBigEndian");
		requireIsSet(isSetAudioCodec, "audioCodec");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (rtpAudioSpf <= 0) {
			throw new IllegalArgumentException(errPrefix + "rtpAudioSpf must be > 0");
		}
		if (audioSampleRateHz <= 0 || audioSampleRateHz > 96000) {
			throw new IllegalArgumentException(errPrefix + "audioSampleRateHz must be > 0 and <= 96000");
		}
		if (audioChannelCount < 1 || audioChannelCount > 2) {
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

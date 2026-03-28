package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;

public class ParamsThreadRtpSenderAudioCommon implements Cloneable {

	/** Audio samples per frame as required for RTP */
	private int rtpAudioSpf;
	private boolean isSetRtpAudioSpf;
	/** Audio sample rate in Hz */
	private int audioSampleRateHz;
	private boolean isSetAudioSampleRateHz;

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

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtpSenderAudioCommon clone() {
		try {
			return (ParamsThreadRtpSenderAudioCommon)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetRtpAudioSpf, "rtpAudioSpf");
		requireIsSet(isSetAudioSampleRateHz, "audioSampleRateHz");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (rtpAudioSpf <= 0) {
			throw new IllegalArgumentException(errPrefix + "rtpAudioSpf must be > 0");
		}
		if (audioSampleRateHz <= 0 || audioSampleRateHz > 96000) {
			throw new IllegalArgumentException(errPrefix + "audioSampleRateHz must be > 0 and <= 96000");
		}
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtpSenderAudioCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

}

package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;

public class ParamsThreadRtpSenderAudioCommon implements Cloneable {

	/** Audio samples per frame as required for RTP */
	private int rtpAudioSpf;
	private boolean isSetRtpAudioSpf;
	/** Audio samplerate in Hz */
	private int audioSamplerateHz;
	private boolean isSetAudioSamplerateHz;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getRtpAudioSpf() { return rtpAudioSpf; }
	public void setRtpAudioSpf(int value) {
		this.rtpAudioSpf = value;
		this.isSetRtpAudioSpf = true;
	}

	public int getAudioSamplerateHz() { return audioSamplerateHz; }
	public void setAudioSamplerateHz(int value) {
		this.audioSamplerateHz = value;
		this.isSetAudioSamplerateHz = true;
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
		requireIsSet(isSetAudioSamplerateHz, "audioSamplerateHz");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (rtpAudioSpf <= 0) {
			throw new IllegalArgumentException(errPrefix + "rtpAudioSpf must be > 0");
		}
		if (audioSamplerateHz <= 0 || audioSamplerateHz > 96000) {
			throw new IllegalArgumentException(errPrefix + "audioSamplerateHz must be > 0 and <= 96000");
		}
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtpSenderAudioCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

}

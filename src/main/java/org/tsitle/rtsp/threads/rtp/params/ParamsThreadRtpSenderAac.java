package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadRtpSenderAac implements Cloneable {

	/** Audio sample rate in Hz */
	private int audioSampleRateHz;
	private boolean isSetAudioSampleRateHz;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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
	public @NonNull ParamsThreadRtpSenderAac clone() {
		try {
			return (ParamsThreadRtpSenderAac)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetAudioSampleRateHz, "audioSampleRateHz");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (audioSampleRateHz <= 0 || audioSampleRateHz > 96000) {
			throw new IllegalArgumentException(errPrefix + "audioSampleRateHz must be > 0 and <= 96000");
		}
	}

	private static void requireIsSet(boolean v, @SuppressWarnings("SameParameterValue") String name) {
		final String errPrefix = ParamsThreadRtpSenderPcm.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

}

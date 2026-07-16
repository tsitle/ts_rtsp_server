package org.tsitle.rtsp_server.threads.rtp.params;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;

public class ParamsThreadRtpSenderAudioCommon implements Cloneable {

	/** Audio samples per frame as required for RTP */
	private int rtpAudioSpf;
	private boolean isSetRtpAudioSpf;
	/** Audio samplerate */
	private @NonNull SampleRateEnum audioSamplerate = SampleRateEnum.UNKNOWN;
	private boolean isSetAudioSamplerate;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getRtpAudioSpf() { return rtpAudioSpf; }
	public void setRtpAudioSpf(int value) {
		this.rtpAudioSpf = value;
		this.isSetRtpAudioSpf = true;
	}

	public @NonNull SampleRateEnum getAudioSamplerate() { return audioSamplerate; }
	public void setAudioSamplerate(@NonNull SampleRateEnum value) {
		this.audioSamplerate = value;
		this.isSetAudioSamplerate = true;
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
		requireIsSet(isSetAudioSamplerate, "audioSamplerate");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (rtpAudioSpf <= 0) {
			throw new IllegalArgumentException(errPrefix + "rtpAudioSpf must be > 0");
		}
		if (audioSamplerate == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException(errPrefix + "audioSamplerate must be valid");
		}
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadRtpSenderAudioCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

}

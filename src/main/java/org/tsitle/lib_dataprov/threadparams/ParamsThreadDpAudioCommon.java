package org.tsitle.lib_dataprov.threadparams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;

public class ParamsThreadDpAudioCommon implements Cloneable {

	/** Audio samples per frame and channel */
	private int audioSpf;
	private boolean isSetAudioSpf;
	/** Audio samplerate */
	private @NonNull SampleRateEnum audioSamplerate = SampleRateEnum.UNKNOWN;
	private boolean isSetAudioSamplerate;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getAudioSpf() { return audioSpf; }
	public void setAudioSpf(int value) {
		this.audioSpf = value;
		this.isSetAudioSpf = true;
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
	public @NonNull ParamsThreadDpAudioCommon clone() {
		try {
			return (ParamsThreadDpAudioCommon)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
		requireIsSet(isSetAudioSpf, "audioSpf");
		requireIsSet(isSetAudioSamplerate, "audioSamplerate");
	}

	private void validateParamValues() {
		final String errPrefix = getClass().getSimpleName() + ": ";

		if (audioSamplerate == SampleRateEnum.UNKNOWN) {
			throw new IllegalArgumentException(errPrefix + "audioSamplerate must be valid");
		}
	}

	private static void requireIsSet(boolean v, String name) {
		final String errPrefix = ParamsThreadDpAudioCommon.class.getSimpleName() + ": ";

		if (! v) {
			throw new IllegalStateException(errPrefix + name + " must be set!");
		}
	}

}

package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;

public class ParamsThreadRtpSenderAudioCommon implements Cloneable {

	// ... common fields for audio ...

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	// ... getters and setters ...

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
	}

	private void validateParamValues() {
	}

}

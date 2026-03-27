package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadRtpSenderVideoCommon implements Cloneable {

	// ... common fields for video ...

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	// ... getters and setters ...

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtpSenderVideoCommon clone() {
		try {
			return (ParamsThreadRtpSenderVideoCommon)super.clone();
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

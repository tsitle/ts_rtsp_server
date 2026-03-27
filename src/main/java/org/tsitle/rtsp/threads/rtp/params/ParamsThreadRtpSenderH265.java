package org.tsitle.rtsp.threads.rtp.params;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadRtpSenderH265 implements Cloneable {

	// ... fields only for H265 ...

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtpSenderVideoCommon clone() {
		try {
			//noinspection UnnecessaryLocalVariable
			ParamsThreadRtpSenderVideoCommon clone = (ParamsThreadRtpSenderVideoCommon)super.clone();
			// ... clone mutable fields ...
			return clone;
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

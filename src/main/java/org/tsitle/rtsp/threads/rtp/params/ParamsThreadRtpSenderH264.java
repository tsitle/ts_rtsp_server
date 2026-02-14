package org.tsitle.rtsp.threads.rtp.params;

public class ParamsThreadRtpSenderH264 {

	// ... fields only for H264 ...

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public ParamsThreadRtpSenderVideoCommon clone() {
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

package org.tsitle.rtsp_server.threads.rtp.params;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadRtpSenderOpus implements Cloneable {

	// ... fields only for Opus ...

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	// ... getters and setters ...

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtpSenderOpus clone() {
		try {
			return (ParamsThreadRtpSenderOpus)super.clone();
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

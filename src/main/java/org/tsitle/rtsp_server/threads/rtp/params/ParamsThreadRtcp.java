package org.tsitle.rtsp_server.threads.rtp.params;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadRtcp extends ParamsThreadRtxp implements Cloneable {

	public ParamsThreadRtcp() {
		super(true, true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		super.validate();
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadRtcp clone() {
		return (ParamsThreadRtcp)super.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAllParamsSet() {
	}

	private void validateParamValues() {
	}

}

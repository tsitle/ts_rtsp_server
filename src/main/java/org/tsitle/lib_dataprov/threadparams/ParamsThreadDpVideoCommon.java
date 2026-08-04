package org.tsitle.lib_dataprov.threadparams;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadDpVideoCommon implements Cloneable {

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
	public @NonNull ParamsThreadDpVideoCommon clone() {
		try {
			return (ParamsThreadDpVideoCommon)super.clone();
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

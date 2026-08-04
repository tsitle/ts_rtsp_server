package org.tsitle.lib_dataprov.threadparams;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadDpH264 implements Cloneable {

	// ... fields only for H264 ...

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
			//noinspection UnnecessaryLocalVariable
			ParamsThreadDpVideoCommon clone = (ParamsThreadDpVideoCommon)super.clone();
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

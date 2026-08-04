package org.tsitle.lib_dataprov.threadparams;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadDpAc3 implements Cloneable {

	// ... fields only for AC-3 ...

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	// ... getters and setters ...

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadDpAc3 clone() {
		try {
			return (ParamsThreadDpAc3)super.clone();
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

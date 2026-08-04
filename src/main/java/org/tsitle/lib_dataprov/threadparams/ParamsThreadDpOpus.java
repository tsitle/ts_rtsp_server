package org.tsitle.lib_dataprov.threadparams;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadDpOpus implements Cloneable {

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
	public @NonNull ParamsThreadDpOpus clone() {
		try {
			return (ParamsThreadDpOpus)super.clone();
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

package org.tsitle.lib_dataprov.threadparams;

import org.jspecify.annotations.NonNull;

public final class ParamsThreadDpAac implements Cloneable {

	// ... fields only for AAC ...

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	// ... getters and setters ...

	// -----------------------------------------------------------------------------------------------------------------

	public void validate() {
		checkAllParamsSet();
		validateParamValues();
	}

	@Override
	public @NonNull ParamsThreadDpAac clone() {
		try {
			return (ParamsThreadDpAac)super.clone();
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

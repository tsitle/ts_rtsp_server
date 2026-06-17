package org.tsitle.lib.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

public final class RtspProtoDataCntGetSetParamNames {

	private boolean isWriteProtected = false;

	/** Parameter names */
	private final @NonNull Set<@NonNull String> paramNames = new HashSet<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isParamNamesEmpty() {
		return paramNames.isEmpty();
	}
	public @NonNull Set<@NonNull String> getParamNames() {
		return new HashSet<>(paramNames);
	}
	public void putAllParamNames(@NonNull Set<@NonNull String> value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.paramNames.addAll(value);
	}
	public void putParamName(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.paramNames.add(value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		paramNames.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntGetSetParamNames other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		paramNames.clear();
		paramNames.addAll(other.paramNames);
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	private static @NonNull String setToString(@NonNull Set<@NonNull String> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (String tmpEntry : input) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append(tmpEntry);
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"paramNames=" + setToString(paramNames) +
				"]";
	}

}

package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

public final class RtspProtoDataCntGetRequFeat {

	private boolean isWriteProtected = false;

	/** Feature names */
	private final @NonNull Set<@NonNull String> featureNames = new HashSet<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isFeatureNamesEmpty() {
		return featureNames.isEmpty();
	}
	public @NonNull Set<@NonNull String> getFeatureNames() {
		return new HashSet<>(featureNames);
	}
	public void putAllFeatureNames(@NonNull Set<@NonNull String> value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.featureNames.addAll(value);
	}
	@SuppressWarnings("unused")
	public void putFeatureName(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.featureNames.add(value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		featureNames.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntGetRequFeat other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		featureNames.clear();
		featureNames.addAll(other.featureNames);
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
				"featureNames=" + setToString(featureNames) +
				"]";
	}

}

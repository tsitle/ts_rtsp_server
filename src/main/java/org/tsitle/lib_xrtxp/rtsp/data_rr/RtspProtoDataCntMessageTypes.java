package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;

import java.util.HashSet;
import java.util.Set;

public final class RtspProtoDataCntMessageTypes {

	private boolean isWriteProtected = false;

	/** Message types */
	private final @NonNull Set<@NonNull RtspProtoMessageType> mts = new HashSet<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public boolean isMtsEmpty() {
		return mts.isEmpty();
	}
	public @NonNull Set<@NonNull RtspProtoMessageType> getMts() {
		return new HashSet<>(mts);
	}
	public void putAllMts(@NonNull Set<@NonNull RtspProtoMessageType> value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.mts.addAll(value);
	}
	public void putMt(@NonNull RtspProtoMessageType value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.mts.add(value);
	}
	public boolean containsMt(@NonNull RtspProtoMessageType value) {
		return mts.contains(value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		mts.clear();
	}

	public void copyFrom(@NonNull RtspProtoDataCntMessageTypes other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		mts.clear();
		mts.addAll(other.mts);
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	private static @NonNull String setToString(@NonNull Set<@NonNull RtspProtoMessageType> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (RtspProtoMessageType tmpEntry : input) {
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
				"mts=" + setToString(mts) +
				"]";
	}

}

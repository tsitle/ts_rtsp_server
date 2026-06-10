package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.util.HashSet;
import java.util.Set;

public final class RtspProtoDataCntMessageTypes {

	private boolean isWriteProtected = false;

	/** Message types */
	private final @NonNull Set<@NonNull RtspMessageType> mts = new HashSet<>();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isMtsEmpty() {
		return mts.isEmpty();
	}
	public @NonNull Set<@NonNull RtspMessageType> getMts() {
		return new HashSet<>(mts);
	}
	public void putAllMts(@NonNull Set<@NonNull RtspMessageType> value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.mts.addAll(value);
	}
	public void putMt(@NonNull RtspMessageType value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.mts.add(value);
	}
	public boolean containsMt(@NonNull RtspMessageType value) {
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

	private static @NonNull String setToString(@NonNull Set<@NonNull RtspMessageType> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (RtspMessageType tmpEntry : input) {
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

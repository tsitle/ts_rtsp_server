package org.tsitle.rtsp.threads.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;

public class RtspProtoIdBase implements Cloneable {

	private boolean isWriteProtected = false;

	private @NonNull String idStr = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected RtspProtoIdBase() { }

	protected RtspProtoIdBase(@NonNull String idStr) {
		this.idStr = idStr;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getIdStr() {
		return idStr;
	}

	public void setIdStr(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.idStr = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		idStr = "";
	}

	public void copyFrom(@NonNull RtspProtoIdBase other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		idStr = other.idStr;
	}

	public boolean isReadOnly() {
		return isWriteProtected;
	}
	public void writeProtect() {
		isWriteProtected = true;
	}

	public boolean isEmpty() {
		return idStr.isEmpty();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		RtspProtoIdBase that = (RtspProtoIdBase)obj;
		return idStr.equals(that.idStr);
	}

	@Override
	public int hashCode() {
		return idStr.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"id='" + idStr + "'" +
				"]";
	}

	@Override
	public RtspProtoIdBase clone() {
		try {
			return (RtspProtoIdBase)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}

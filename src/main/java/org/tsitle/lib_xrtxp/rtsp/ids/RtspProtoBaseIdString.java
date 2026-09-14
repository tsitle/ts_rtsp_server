package org.tsitle.lib_xrtxp.rtsp.ids;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Base class for string-based IDs
 */
public class RtspProtoBaseIdString implements Cloneable {

	protected boolean isWriteProtected = false;

	private @NonNull String idStr = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected RtspProtoBaseIdString() { }

	protected RtspProtoBaseIdString(@NonNull String idStr) {
		this.idStr = idStr;

		validate();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getIdStr() {
		if (idStr.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(idStr);
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

	public boolean isReadOnly() {
		return isWriteProtected;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	public boolean isEmpty() {
		return idStr.isBlank();
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
		RtspProtoBaseIdString that = (RtspProtoBaseIdString)obj;
		return idStr.equals(that.idStr);
	}

	@Override
	public int hashCode() {
		return idStr.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"id=" + (isEmpty() ? "unset" : "'" + idStr + "'") +
				"]";
	}

	@Override
	public RtspProtoBaseIdString clone() {
		try {
			return (RtspProtoBaseIdString)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void validate() {
		final String FNC_NAME = getClass().getSimpleName() + ".validate()";

		String orgId = getIdStr().orElse("");
		String sanitizedId = orgId.replaceAll("[^\\x20-\\x7E]", "");
		if (! sanitizedId.equals(orgId)) {
			throw new IllegalArgumentException(FNC_NAME + ": ID '" + sanitizedId + "' contains non-printable characters");
		}
	}

}

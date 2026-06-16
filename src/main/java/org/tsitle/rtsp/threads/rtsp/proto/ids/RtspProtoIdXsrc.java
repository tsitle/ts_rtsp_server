package org.tsitle.rtsp.threads.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Objects;
import java.util.Optional;

/**
 * SSRC/CSRC ID
 */
public final class RtspProtoIdXsrc implements Cloneable {

	private boolean isWriteProtected = false;

	/** SSRC/CSRC ID */
	private long xsrc = -1L;

	public RtspProtoIdXsrc() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspProtoIdXsrc of(long value32bit) throws RtspProtoNumberRangeException {
		RtspProtoIdXsrc resObj = new RtspProtoIdXsrc();
		resObj.setId32bit(value32bit);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getId32bit() {
		return (xsrc < 0 ? Optional.empty() : Optional.of(xsrc));
	}
	public void setId32bit(long value32bit) throws RtspProtoNumberRangeException {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		validateNonNeg32bit(value32bit);
		this.xsrc = value32bit;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (xsrc < 0);
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		xsrc = -1;
	}

	public void copyFrom(@NonNull RtspProtoIdXsrc other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		xsrc = other.xsrc;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoIdXsrc that)) {
			return false;
		}
		return (xsrc == that.xsrc);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(xsrc);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"xsrc=" + (xsrc >= 0 ? toHexString(true) : "unset") +
				"]";
	}

	public @NonNull String toHexString(boolean withPrefix) {
		if (isEmpty()) {
			return "";
		}
		return String.format("%s%08X", withPrefix ? "0x" : "", xsrc);
	}

	@Override
	public RtspProtoIdXsrc clone() {
		try {
			return (RtspProtoIdXsrc)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validateNonNeg32bit(long value) throws RtspProtoNumberRangeException {
		final String FNC_NAME = RtspProtoIdXsrc.class.getSimpleName() + ".validateNonNeg32bit()";

		if (value < 0L || value > 0xFFFFFFFFL) {
			throw new RtspProtoNumberRangeException(FNC_NAME + ": value must be non-negative and within 32-bit range (is=" + value + ")");
		}
	}

}

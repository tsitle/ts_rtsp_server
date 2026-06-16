package org.tsitle.rtsp.threads.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoBaseNumberNonNeg32bit;

import java.util.Optional;

/**
 * SSRC/CSRC ID
 */
public final class RtspProtoIdXsrc extends RtspProtoBaseNumberNonNeg32bit<RtspProtoIdXsrc> implements Cloneable {

	public RtspProtoIdXsrc() {
		super();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdXsrc ofEmpty() {
		return new RtspProtoIdXsrc();
	}

	public static RtspProtoIdXsrc of(long value32bit) throws RtspProtoNumberRangeException {
		RtspProtoIdXsrc resObj = new RtspProtoIdXsrc();
		resObj.setNumber32bit(value32bit);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getId32bit() {
		return getNumber32bit();
	}
	public void setId32bit(long value32bit) throws RtspProtoNumberRangeException {
		setNumber32bit(value32bit);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void copyFrom(@NonNull RtspProtoIdXsrc other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		theNumber = other.theNumber;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoIdXsrc that)) {
			return false;
		}
		return super.equalsOnlyValue(that);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"xsrc=" + (isEmpty() ? "unset" : toHexString(true)) +
				"]";
	}

	public @NonNull String toHexString(boolean withPrefix) {
		if (isEmpty()) {
			return "";
		}
		return String.format("%s%08X", withPrefix ? "0x" : "", theNumber);
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public RtspProtoIdXsrc clone() {
		RtspProtoIdXsrc cloned = new RtspProtoIdXsrc();
		cloned.copyFrom(this);
		if (isWriteProtected) {
			cloned.writeProtect();
		}
		return cloned;
	}

}

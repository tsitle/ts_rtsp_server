package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;

import java.util.Optional;

/**
 * Container for the Content Length of RTSP message bodies.
 */
public final class RtspProtoContentLength extends RtspProtoBaseNumberNonNeg32bit<RtspProtoContentLength> implements Cloneable {

	public RtspProtoContentLength() {
		super();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoContentLength ofEmpty() {
		return new RtspProtoContentLength();
	}

	public static RtspProtoContentLength ofZero() {
		RtspProtoContentLength resObj = new RtspProtoContentLength();
		resObj.theNumber = 0L;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getLen32bit() {
		return getNumber32bit();
	}
	public void setLen32bit(long value32bit) throws RtspProtoNumberRangeException {
		setNumber32bit(value32bit);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void copyFrom(@NonNull RtspProtoContentLength other) {
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
		if (! (o instanceof RtspProtoContentLength that)) {
			return false;
		}
		return super.equalsOnlyValue(that);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"length=" + toStringOnlyValue() +
				"]";
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public @NonNull RtspProtoContentLength clone() {
		RtspProtoContentLength cloned = new RtspProtoContentLength();
		cloned.copyFrom(this);
		if (isWriteProtected) {
			cloned.writeProtect();
		}
		return cloned;
	}

}

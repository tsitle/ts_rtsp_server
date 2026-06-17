package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Optional;

/**
 * Container for an RTP Timestamp
 */
public final class RtspProtoRtpTimestamp extends RtspProtoBaseNumberNonNeg32bit<RtspProtoRtpTimestamp> implements Cloneable {

	private RtspProtoRtpTimestamp() {
		super();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoRtpTimestamp ofEmpty() {
		return new RtspProtoRtpTimestamp();
	}

	public static RtspProtoRtpTimestamp ofZero() {
		RtspProtoRtpTimestamp resObj = new RtspProtoRtpTimestamp();
		resObj.theNumber = 0L;
		return resObj;
	}

	public static RtspProtoRtpTimestamp of(long value32bit) throws RtspProtoNumberRangeException {
		RtspProtoRtpTimestamp resObj = new RtspProtoRtpTimestamp();
		resObj.setTs32bit(value32bit);
		return resObj;
	}

	public static RtspProtoRtpTimestamp withOverflow(long value64bit) {
		RtspProtoRtpTimestamp resObj = new RtspProtoRtpTimestamp();
		int tmpInt = (int)value64bit;
		resObj.theNumber = Integer.toUnsignedLong(tmpInt);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Add a value to this timestamp (with overflow) and return a new timestamp object
	 * @param value Value to add
	 * @return New timestamp object
	 */
	public @NonNull RtspProtoRtpTimestamp add(long value) {
		RtspProtoRtpTimestamp resObj;
		if (isEmpty()) {
			resObj = RtspProtoRtpTimestamp.ofZero();
		} else {
			resObj = this.clone();
		}
		resObj.theNumber += value;
		int tmpInt = (int)resObj.theNumber;
		resObj.theNumber = Integer.toUnsignedLong(tmpInt);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getTs32bit() {
		return getNumber32bit();
	}
	public void setTs32bit(long value32bit) throws RtspProtoNumberRangeException {
		setNumber32bit(value32bit);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void copyFrom(@NonNull RtspProtoRtpTimestamp other) {
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
		if (! (o instanceof RtspProtoRtpTimestamp that)) {
			return false;
		}
		return super.equalsOnlyValue(that);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"rtpTs=" + toStringOnlyValue() +
				"]";
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public RtspProtoRtpTimestamp clone() {
		RtspProtoRtpTimestamp cloned = new RtspProtoRtpTimestamp();
		cloned.copyFrom(this);
		if (isWriteProtected) {
			cloned.writeProtect();
		}
		return cloned;
	}

}

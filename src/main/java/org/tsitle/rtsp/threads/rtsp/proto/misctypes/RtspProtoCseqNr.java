package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Optional;

/**
 * CSeq number for RTSP messages
 */
public final class RtspProtoCseqNr extends RtspProtoNumberNonNeg32bitBase<RtspProtoCseqNr> implements Cloneable {

	public RtspProtoCseqNr() {
		super();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoCseqNr ofEmpty() {
		return new RtspProtoCseqNr();
	}

	public static RtspProtoCseqNr ofZero() {
		RtspProtoCseqNr resObj = new RtspProtoCseqNr();
		resObj.theNumber = 0L;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getCseq32bit() {
		return getNumber32bit();
	}
	public void setCseq32bit(long value32bit) throws RtspProtoNumberRangeException {
		setNumber32bit(value32bit);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void copyFrom(@NonNull RtspProtoCseqNr other) {
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
		if (! (o instanceof RtspProtoCseqNr that)) {
			return false;
		}
		return super.equalsOnlyValue(that);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"cseqNr=" + toStringOnlyValue() +
				"]";
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public RtspProtoCseqNr clone() {
		RtspProtoCseqNr cloned = new RtspProtoCseqNr();
		cloned.copyFrom(this);
		if (isWriteProtected) {
			cloned.writeProtect();
		}
		return cloned;
	}

}

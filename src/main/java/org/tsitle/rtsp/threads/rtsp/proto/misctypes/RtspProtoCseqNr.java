package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Objects;
import java.util.Optional;

public final class RtspProtoCseqNr implements Cloneable {

	private boolean isWriteProtected = false;

	/** CSeq number */
	private long cseqNr = -1L;

	public RtspProtoCseqNr() { }

	public RtspProtoCseqNr(long value32bit) {
		try {
			validateNonNeg32bit(value32bit);
			this.cseqNr = value32bit;
		} catch (RtspProtoNumberRangeException e) {
			// ignore
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getCseq32bit() {
		return (cseqNr < 0 ? Optional.empty() : Optional.of(cseqNr));
	}
	public void setCseq32bit(long value32bit) throws RtspProtoNumberRangeException {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		validateNonNeg32bit(value32bit);
		this.cseqNr = value32bit;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (cseqNr < 0);
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		cseqNr = -1;
	}

	public void copyFrom(@NonNull RtspProtoCseqNr other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		cseqNr = other.cseqNr;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoCseqNr that)) {
			return false;
		}
		return (cseqNr == that.cseqNr);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(cseqNr);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"cseqNr=" + (cseqNr >= 0 ? Long.toUnsignedString(cseqNr) : "unset") +
				"]";
	}

	@Override
	public RtspProtoCseqNr clone() {
		try {
			return (RtspProtoCseqNr)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validateNonNeg32bit(long value) throws RtspProtoNumberRangeException {
		final String FNC_NAME = RtspProtoCseqNr.class.getSimpleName() + ".validateNonNeg32bit()";

		if (value < 0L || value > 0xFFFFFFFFL) {
			throw new RtspProtoNumberRangeException(FNC_NAME + ": value must be non-negative and within 32-bit range (is=" + value + ")");
		}
	}

}

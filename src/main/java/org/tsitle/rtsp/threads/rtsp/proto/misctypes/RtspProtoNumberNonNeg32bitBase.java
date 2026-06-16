package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Objects;
import java.util.Optional;

public abstract class RtspProtoNumberNonNeg32bitBase<T extends RtspProtoNumberNonNeg32bitBase<T>> {

	protected boolean isWriteProtected = false;

	protected long theNumber = -1L;

	protected RtspProtoNumberNonNeg32bitBase() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (theNumber < 0L);
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		theNumber = -1L;
	}

	public abstract void copyFrom(@NonNull T other);

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public int hashCode() {
		return Objects.hashCode(theNumber);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected Optional<Long> getNumber32bit() {
		return (theNumber < 0 ? Optional.empty() : Optional.of(theNumber));
	}
	protected void setNumber32bit(long value32bit) throws RtspProtoNumberRangeException {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		validateNonNeg32bit(getClass().getSimpleName(), value32bit);
		this.theNumber = value32bit;
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected boolean equalsOnlyValue(T other) {
		return (theNumber == other.theNumber);
	}

	protected @NonNull String toStringOnlyValue() {
		return (theNumber >= 0L ? Long.toUnsignedString(theNumber) : "unset");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validateNonNeg32bit(@NonNull String className, long value) throws RtspProtoNumberRangeException {
		final String FNC_NAME = className + ".validateNonNeg32bit()";

		if (value < 0L || value > 0xFFFFFFFFL) {
			throw new RtspProtoNumberRangeException(FNC_NAME + ": value must be non-negative and within 32-bit range (is=" + value + ")");
		}
	}

}

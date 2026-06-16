package org.tsitle.rtsp.helpers;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

public final class TimestampEpochNs implements Cloneable {

	private boolean isWriteProtected = false;

	/** Timestamp as epoch in nanoseconds */
	private long epochNs = 0L;
	private boolean isSet = false;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static TimestampEpochNs ofEmpty() {
		return new TimestampEpochNs();
	}

	public static TimestampEpochNs ofNow() {
		TimestampEpochNs resObj = new TimestampEpochNs();
		resObj.epochNs = System.nanoTime();
		resObj.isSet = true;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getEpochNsUnsigned64bit() {
		return (isSet ? Optional.of(epochNs) : Optional.empty());
	}
	public void setEpochNsUnsigned64bit(long value64bit) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.epochNs = value64bit;
		this.isSet = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (! isSet);
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		epochNs = 0L;
		isSet = false;
	}

	public void copyFrom(@NonNull TimestampEpochNs other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		epochNs = other.epochNs;
		isSet = other.isSet;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof TimestampEpochNs that)) {
			return false;
		}
		return (epochNs == that.epochNs && isSet == that.isSet);
	}

	@Override
	public int hashCode() {
		return Objects.hash(epochNs, isSet);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"epochNs=" + (isSet ? Long.toUnsignedString(epochNs) : "unset") +
				"]";
	}

	@Override
	public TimestampEpochNs clone() {
		try {
			return (TimestampEpochNs)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}

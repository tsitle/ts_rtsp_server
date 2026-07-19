package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class TimestampEpochNs implements Cloneable {

	private boolean isWriteProtected = false;

	/** Timestamp as epoch in nanoseconds */
	private long epochNs = 0L;
	private boolean isSet = false;

	private TimestampEpochNs() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static TimestampEpochNs ofEmpty() {
		return new TimestampEpochNs();
	}

	public static TimestampEpochNs ofNow() {
		return ofInstant(Instant.now());
	}

	public static TimestampEpochNs ofInstant(@NonNull Instant value) {
		TimestampEpochNs resObj = new TimestampEpochNs();
		resObj.epochNs = value.getEpochSecond() * 1_000_000_000L + value.getNano();
		resObj.isSet = true;
		return resObj;
	}

	public static TimestampEpochNs ofEpochMsUnsigned64bit(long value64bit) {
		return ofEpochNsUnsigned64bit(value64bit * 1_000_000L);
	}

	public static TimestampEpochNs ofEpochNsUnsigned64bit(long value64bit) {
		TimestampEpochNs resObj = new TimestampEpochNs();
		resObj.epochNs = value64bit;
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

	public Optional<Instant> toInstant() {
		if (isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(
				Instant.ofEpochSecond(epochNs / 1_000_000_000L, epochNs % 1_000_000_000L)
			);
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

package org.tsitle.lib_xrtxp.common.types;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

public final class TimestampMonotonic implements Cloneable {

	private boolean isWriteProtected = false;

	/** Timestamp in nanoseconds */
	private long tsMonoNs = 0L;
	private boolean isSet = false;

	private TimestampMonotonic() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull TimestampMonotonic ofEmpty() {
		return new TimestampMonotonic();
	}

	public static @NonNull TimestampMonotonic ofNow() {
		return ofNsUnsigned64bit(System.nanoTime());
	}

	public static @NonNull TimestampMonotonic ofMsUnsigned64bit(long value64bit) {
		return ofNsUnsigned64bit(value64bit * 1_000_000L);
	}

	public static @NonNull TimestampMonotonic ofNsUnsigned64bit(long value64bit) {
		TimestampMonotonic resObj = new TimestampMonotonic();
		resObj.setNsUnsigned64bit(value64bit);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public void setToNow() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		tsMonoNs = System.nanoTime();
		isSet = true;
	}

	public Optional<Long> getNsUnsigned64bit() {
		return (isSet ? Optional.of(tsMonoNs) : Optional.empty());
	}
	public void setNsUnsigned64bit(long value64bit) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.tsMonoNs = value64bit;
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
		tsMonoNs = 0L;
		isSet = false;
	}

	public void copyFrom(@NonNull TimestampMonotonic other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		tsMonoNs = other.tsMonoNs;
		isSet = other.isSet;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof TimestampMonotonic that)) {
			return false;
		}
		return (tsMonoNs == that.tsMonoNs && isSet == that.isSet);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tsMonoNs, isSet);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"tsMonoNs=" + (isSet ? Long.toUnsignedString(tsMonoNs) : "unset") +
				"]";
	}

	@Override
	public @NonNull TimestampMonotonic clone() {
		try {
			return (TimestampMonotonic)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}

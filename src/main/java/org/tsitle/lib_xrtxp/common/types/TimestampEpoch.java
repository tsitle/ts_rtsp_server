package org.tsitle.lib_xrtxp.common.types;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

public final class TimestampEpoch implements Cloneable {

	private boolean isWriteProtected = false;

	/** Timestamp as epoch in nanoseconds */
	private long epochNs = 0L;
	private boolean isSet = false;

	private TimestampEpoch() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull TimestampEpoch ofEmpty() {
		return new TimestampEpoch();
	}

	public static @NonNull TimestampEpoch ofNow() {
		return ofInstant(Instant.now());
	}

	public static @NonNull TimestampEpoch ofInstant(@NonNull Instant value) {
		TimestampEpoch resObj = new TimestampEpoch();
		resObj.setToInstant(value);
		return resObj;
	}

	public static @NonNull TimestampEpoch ofEpochMsUnsigned64bit(long value64bit) {
		return ofEpochNsUnsigned64bit(value64bit * 1_000_000L);
	}

	public static @NonNull TimestampEpoch ofEpochNsUnsigned64bit(long value64bit) {
		TimestampEpoch resObj = new TimestampEpoch();
		resObj.setEpochNsUnsigned64bit(value64bit);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public void setToNow() {
		setToInstant(Instant.now());
	}

	public void setToInstant(@NonNull Instant value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		epochNs = value.getEpochSecond() * 1_000_000_000L + value.getNano();
		isSet = true;
	}

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

	public void copyFrom(@NonNull TimestampEpoch other) {
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
		if (! (o instanceof TimestampEpoch that)) {
			return false;
		}
		return (epochNs == that.epochNs && isSet == that.isSet);
	}

	@Override
	public int hashCode() {
		return Objects.hash(epochNs, isSet);
	}

	public @NonNull String toIso8601StyleString() {
		if (! isSet) {
			return "unset";
		}
		//
		Instant tmpInst = toInstant().orElseThrow();
		ZonedDateTime tmpZdt = tmpInst.atZone(ZoneOffset.UTC);
		int fldYear = tmpZdt.getYear();
		int fldMonth = tmpZdt.getMonthValue();
		int fldDay = tmpZdt.getDayOfMonth();
		int fldHour = tmpZdt.getHour();
		int fldMinute = tmpZdt.getMinute();
		int fldSecond = tmpZdt.getSecond();
		int fldNano = tmpZdt.getNano();
		// 2026-03-27T19:54:50.975Z
		return String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
				fldYear, fldMonth, fldDay, fldHour, fldMinute, fldSecond, fldNano / 1_000_000);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"epochNs=" + (isSet ? Long.toUnsignedString(epochNs) + " (" + toIso8601StyleString() + ")" : "unset") +
				"]";
	}

	@Override
	public @NonNull TimestampEpoch clone() {
		try {
			return (TimestampEpoch)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}

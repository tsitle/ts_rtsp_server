package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Container for 64-bit NTP timestamps
 */
public final class NtpTimestamp implements Cloneable {

	/** Offset in seconds between the NTP epoch (1900-01-01) and the Unix epoch (1970-01-01) */
	public static final long NTP_EPOCH_OFFSET_SECONDS = 2_208_988_800L;

	// -----------------------------------------------------

	private boolean isWriteProtected = false;

	private long ntpSecondsSince1900_32bit = -1L;
	private long ntpFraction_32bit = -1L;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static NtpTimestamp ofEmpty() {
		return new NtpTimestamp();
	}

	public static NtpTimestamp of(long ntpSeconds32bit, long ntpFraction32bit) throws RtspProtoNumberRangeException {
		validateNonNeg32bit("ntpSeconds32bit", ntpSeconds32bit);
		validateNonNeg32bit("ntpFraction32bit", ntpFraction32bit);
		NtpTimestamp resObj = new NtpTimestamp();
		resObj.ntpSecondsSince1900_32bit = ntpSeconds32bit;
		resObj.ntpFraction_32bit = ntpFraction32bit;
		return resObj;
	}

	/**
	 * Return an NTP timestamp representing the current time.
	 * @return NTP timestamp
	 */
	public static @NonNull NtpTimestamp ofNow() {
		return ofInstant(Instant.now());
	}

	/**
	 * Converts a Java {@link Instant} to a 64-bit NTP timestamp.
	 *
	 * @param javaTs the instant to convert
	 * @return 64-bit NTP timestamp (upper 32 bits = seconds, lower 32 bits = fraction)
	 */
	public static @NonNull NtpTimestamp ofInstant(@NonNull Instant javaTs) {
		long secondsSince1900 = (javaTs.getEpochSecond() + NTP_EPOCH_OFFSET_SECONDS);

		// Convert nanoseconds to 32-bit fraction: fraction = nanos * 2^32 / 1e9
		long nanos = javaTs.getNano();
		long fraction = (nanos * 0x1_0000_0000L) / 1_000_000_000L;

		return NtpTimestamp.withOverflow(secondsSince1900, fraction);
	}

	public static NtpTimestamp withOverflow(long ntpSeconds32bit, long ntpFraction32bit) {
		NtpTimestamp resObj = new NtpTimestamp();
		int tmpSecInt = (int)ntpSeconds32bit;
		resObj.ntpSecondsSince1900_32bit = Integer.toUnsignedLong(tmpSecInt);
		int tmpFracInt = (int)ntpFraction32bit;
		resObj.ntpFraction_32bit = Integer.toUnsignedLong(tmpFracInt);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Converts this timestamp to an Instant.
	 * @return The timestamp as an Instant
	 */
	public @NonNull Instant toInstant() {
		if (isEmpty()) {
			throw new IllegalArgumentException("timestamp is empty");
		}
		long unixSeconds = (ntpSecondsSince1900_32bit - NTP_EPOCH_OFFSET_SECONDS);

		// nanos = fraction * 1e9 / 2^32
		long nanos = ((ntpFraction_32bit * 1_000_000_000L) >>> 32);

		return Instant.ofEpochSecond(unixSeconds, nanos);
	}

	/**
	 * Adds nanoseconds to this timestamp.
	 * @param nanosToAdd  Nanoseconds to add (can be negative)
	 * @return The adjusted 64-bit NTP timestamp
	 */
	public @NonNull NtpTimestamp addNanos(long nanosToAdd) {
		if (isEmpty()) {
			throw new IllegalArgumentException("timestamp is empty");
		}
		// Convert nanosToAdd into whole seconds + remaining nanos
		long extraSeconds = nanosToAdd / 1_000_000_000L;
		long remainingNanos = nanosToAdd % 1_000_000_000L;

		// Handle negative remaining nanos
		if (remainingNanos < 0) {
			remainingNanos += 1_000_000_000L;
			extraSeconds -= 1;
		}

		// Convert remaining nanos to NTP fractional units
		long fractionToAdd = (remainingNanos * 0x1_0000_0000L) / 1_000_000_000L;

		// Add fractions; handle carry into seconds
		long newFraction = ntpFraction_32bit + fractionToAdd;
		long carry = newFraction >>> 32;  // 1 if the fraction overflowed, 0 otherwise
		newFraction &= 0xFFFF_FFFFL;

		long newSeconds = ntpSecondsSince1900_32bit + extraSeconds + carry;

		return NtpTimestamp.withOverflow(newSeconds, newFraction);
	}

	/**
	 * Returns {@code (ntpTimestampB - ntpTimestampThis)} as a signed duration in nanoseconds.<br />
	 * Assumes both timestamps are within the same NTP era (i.e., no wraparound of the 32-bit seconds field).
	 * @param ntpTimestampB 64-bit NTP timestamp (upper 32 bits = seconds, lower 32 bits = fraction)
	 * @return Difference in nanoseconds (can be negative)
	 */
	public long diffNanos(@NonNull NtpTimestamp ntpTimestampB) {
		if (isEmpty()) {
			throw new IllegalArgumentException("timestamp is empty");
		}
		if (ntpTimestampB.isEmpty()) {
			throw new IllegalArgumentException("ntpTimestampB is empty");
		}
		long bSec = ntpTimestampB.getSeconds32bit().orElseThrow();
		long bFrac = ntpTimestampB.getFraction32bit().orElseThrow();

		long secDiff = bSec - ntpSecondsSince1900_32bit;  // seconds
		long fracDiff = bFrac - ntpFraction_32bit;  // 32-bit fraction units (signed in long range)

		// Convert fraction-diff to nanos: (fracDiff * 1e9) / 2^32
		// Using >> 32 is equivalent to dividing by 2^32 (with sign), and stays within long range here.
		long fracNanos = (fracDiff * 1_000_000_000L) >> 32;

		return secDiff * 1_000_000_000L + fracNanos;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Long> getSeconds32bit() {
		if (isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(ntpSecondsSince1900_32bit);
	}

	public Optional<Long> getFraction32bit() {
		if (isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(ntpFraction_32bit);
	}

	public Optional<Long> getTsAsUnsigned64bit() {
		if (isEmpty()) {
			return Optional.empty();
		}
		long tmpRes = (ntpSecondsSince1900_32bit << 32) | (ntpFraction_32bit & 0xFFFF_FFFFL);
		return Optional.of(tmpRes);
	}
	public void setTsAsUnsigned64bit(long value64bit) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		ntpSecondsSince1900_32bit = (value64bit >>> 32) & 0xFFFF_FFFFL;
		ntpFraction_32bit = (value64bit & 0xFFFF_FFFFL);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (ntpSecondsSince1900_32bit < 0L || ntpFraction_32bit < 0L);
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		ntpSecondsSince1900_32bit = -1L;
		ntpFraction_32bit = -1L;
	}

	public void copyFrom(@NonNull NtpTimestamp other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		ntpSecondsSince1900_32bit = other.ntpSecondsSince1900_32bit;
		ntpFraction_32bit = other.ntpFraction_32bit;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof NtpTimestamp that)) {
			return false;
		}
		return (ntpSecondsSince1900_32bit == that.ntpSecondsSince1900_32bit && ntpFraction_32bit == that.ntpFraction_32bit);
	}

	@Override
	public int hashCode() {
		return Objects.hash(ntpSecondsSince1900_32bit, ntpFraction_32bit);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"seconds=" + (isEmpty() ? "unset" : Long.toUnsignedString(ntpSecondsSince1900_32bit)) +
				", fraction=" + (isEmpty() ? "unset" : Long.toUnsignedString(ntpFraction_32bit)) +
				"]";
	}

	@Override
	public NtpTimestamp clone() {
		try {
			return (NtpTimestamp)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validateNonNeg32bit(@NonNull String desc, long value) throws RtspProtoNumberRangeException {
		final String FNC_NAME = NtpTimestamp.class.getSimpleName() + ".validateNonNeg32bit()";

		if (value < 0L || value > 0xFFFFFFFFL) {
			throw new RtspProtoNumberRangeException(FNC_NAME + ": " + desc + " must be non-negative and within 32-bit range " +
					"(is=" + value + ")");
		}
	}

}

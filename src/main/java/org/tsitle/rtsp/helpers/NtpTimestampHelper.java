package org.tsitle.rtsp.helpers;

import java.time.Instant;

/**
 * NTP timestamp helper.
 */
public final class NtpTimestampHelper {

	/** Offset in seconds between the NTP epoch (1900-01-01) and the Unix epoch (1970-01-01) */
	private static final long NTP_EPOCH_OFFSET_SECONDS = 2_208_988_800L;

	private NtpTimestampHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Converts a Java {@link Instant} to a 64-bit NTP timestamp.
	 *
	 * @param javaTs the instant to convert
	 * @return 64-bit NTP timestamp (upper 32 bits = seconds, lower 32 bits = fraction)
	 */
	public static long instantToNtpTimestamp(Instant javaTs) {
		long secondsSince1900 = (javaTs.getEpochSecond() + NTP_EPOCH_OFFSET_SECONDS);

		// Convert nanoseconds to 32-bit fraction: fraction = nanos * 2^32 / 1e9
		long nanos = javaTs.getNano();
		long fraction = (nanos * 0x1_0000_0000L) / 1_000_000_000L;

		return (secondsSince1900 << 32) | (fraction & 0xFFFF_FFFFL);
	}

	/**
	 * This is only for use in RTCP packets, where the 32-bit timestamp is used.
	 * @param javaTs Instant
	 * @return 32-bit NTP timestamp (top 16 bits: integer seconds, bottom 16 bits: fractional seconds)
	 */
	public static long instantTo32bitNtpTimestamp(Instant javaTs) {
		long unixSeconds = javaTs.getEpochSecond();
		long ntpSeconds = unixSeconds + NTP_EPOCH_OFFSET_SECONDS;

		// NTP fractional part: 32-bit fraction of a second
		long nanos = javaTs.getNano();
		long ntpFraction32 = (nanos * 0x1_0000_0000L) / 1_000_000_000L;

		// Middle 32 bits = (low 16 bits of the seconds) << 16 | (high 16 bits of the fraction)
		long middle32 =
				((ntpSeconds & 0xFFFFL) << 16) |
						((ntpFraction32 >>> 16) & 0xFFFFL);

		return middle32 & 0xFFFF_FFFFL;
	}

	public static Instant ntpTimestampToInstant(long ntpTimestamp) {
		long ntpSeconds = (ntpTimestamp >>> 32) & 0xFFFF_FFFFL;
		long ntpFraction = (ntpTimestamp & 0xFFFF_FFFFL);

		long unixSeconds = (ntpSeconds - 2_208_988_800L);

		// nanos = fraction * 1e9 / 2^32
		long nanos = ((ntpFraction * 1_000_000_000L) >>> 32);

		return Instant.ofEpochSecond(unixSeconds, nanos);
	}

	/**
	 * Adds nanoseconds to a 64-bit NTP timestamp.
	 * @param ntpTimestamp The original 64-bit NTP timestamp
	 * @param nanosToAdd  Nanoseconds to add (can be negative)
	 * @return The adjusted 64-bit NTP timestamp
	 */
	public static long addNanosToNtpTimestamp(long ntpTimestamp, long nanosToAdd) {
		// Extract the current seconds and fraction
		long seconds  = (ntpTimestamp >>> 32) & 0xFFFF_FFFFL;
		long fraction = ntpTimestamp & 0xFFFF_FFFFL;

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
		long newFraction = fraction + fractionToAdd;
		long carry = newFraction >>> 32;  // 1 if the fraction overflowed, 0 otherwise
		newFraction &= 0xFFFF_FFFFL;

		long newSeconds = seconds + extraSeconds + carry;

		return (newSeconds << 32) | newFraction;
	}

	/**
	 * Returns {@code (ntpTimestampB - ntpTimestampA)} as a signed duration in nanoseconds.<br />
	 * Assumes both timestamps are within the same NTP era (i.e., no wraparound of the 32-bit seconds field).
	 * @param ntpTimestampA 64-bit NTP timestamp (upper 32 bits = seconds, lower 32 bits = fraction)
	 * @param ntpTimestampB 64-bit NTP timestamp (upper 32 bits = seconds, lower 32 bits = fraction)
	 * @return Difference in nanoseconds (can be negative)
	 */
	public static long diffNanos(long ntpTimestampA, long ntpTimestampB) {
		long aSec = (ntpTimestampA >>> 32) & 0xFFFF_FFFFL;
		long aFrac = ntpTimestampA & 0xFFFF_FFFFL;

		long bSec = (ntpTimestampB >>> 32) & 0xFFFF_FFFFL;
		long bFrac = ntpTimestampB & 0xFFFF_FFFFL;

		long secDiff = bSec - aSec;  // seconds
		long fracDiff = bFrac - aFrac;  // 32-bit fraction units (signed in long range)

		// Convert fraction-diff to nanos: (fracDiff * 1e9) / 2^32
		// Using >> 32 is equivalent to dividing by 2^32 (with sign), and stays within long range here.
		long fracNanos = (fracDiff * 1_000_000_000L) >> 32;

		return secDiff * 1_000_000_000L + fracNanos;
	}

}

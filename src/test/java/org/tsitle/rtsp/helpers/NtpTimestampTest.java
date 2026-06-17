package org.tsitle.rtsp.helpers;

import org.junit.jupiter.api.Test;
import org.tsitle.lib.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class NtpTimestampTest {

	@Test
	void ntp_test1() {
		Instant expInstant = Instant.parse("2023-01-01T00:00:00.000Z");
		NtpTimestamp ntp1 = NtpTimestamp.ofInstant(expInstant);
		Instant actualInstant1 = ntp1.toInstant();

		assertEquals(expInstant, actualInstant1);

		long ntp2 = toNtpTime(expInstant.toEpochMilli());

		assertEquals(ntp1.getTsAsUnsigned64bit().orElseThrow(), ntp2);

		long epochMillis = getTime(ntp2);
		Instant actualInstant2 = Instant.ofEpochMilli(epochMillis);

		assertEquals(expInstant.toEpochMilli(), actualInstant2.toEpochMilli());
	}

	@Test
	void ntp_test2() {
		Instant expInstant = Instant.now();
		NtpTimestamp ntp1 = NtpTimestamp.ofInstant(expInstant);
		Instant actualInstant1 = ntp1.toInstant();

		Duration delta = Duration.between(expInstant, actualInstant1);
		assertTrue(delta.toMillis() < 1);

		long ntp2 = toNtpTime(expInstant.toEpochMilli());

		long epochMillis = getTime(ntp2);
		Instant actualInstant2 = Instant.ofEpochMilli(epochMillis);

		long delta2 = Math.abs(expInstant.toEpochMilli() - actualInstant2.toEpochMilli());
		assertTrue(delta2 < 1000L);
	}

	@Test
	void ntp_test3() {
		Instant expInstant = Instant.parse("2026-01-01T00:00:00.000Z");
		NtpTimestamp ntp1 = NtpTimestamp.ofInstant(expInstant);
		NtpTimestamp ntp2 = ntp1.addNanos(1033L);
		long delta1 = ntp1.diffNanos(ntp2);
		assertEquals(1032L, delta1);  // 1ns less than expected due to a rounding error

		NtpTimestamp ntp3 = ntp2.addNanos(45_102_030L);
		long delta2 = ntp1.diffNanos(ntp3);
		assertEquals(45_102_030L + 1032L, delta2);  // 1ns less than expected due to a rounding error

		Instant actualInstant = ntp3.toInstant();
		Duration delta = Duration.between(expInstant, actualInstant);
		assertTrue(delta.toMillis() >= 45 && delta.toMillis() <= 46);
		assertTrue(delta.toNanos() >= 45_102_030L + 1032L && delta.toNanos() <= 45_102_030L + 1033L);
	}

	@Test
	void ntp_test4() throws RtspProtoNumberRangeException {
		final Instant expInstant = Instant.parse("2026-03-26T06:46:20.134876999Z");
		final long ntpExpectedLong = Long.parseUnsignedLong("17108986676413680783");
		final NtpTimestamp ntpExpectedObj = NtpTimestamp.ofEmpty();
		ntpExpectedObj.setTsAsUnsigned64bit(ntpExpectedLong);
		final NtpTimestamp ntpActual = NtpTimestamp.ofInstant(expInstant);
		final long deltaAbs = Math.abs(ntpExpectedLong - ntpActual.getTsAsUnsigned64bit().orElseThrow());
		final long deltaNanosAbs = Math.abs(ntpExpectedObj.diffNanos(ntpActual));

		long expSec = ntpExpectedObj.getSeconds32bit().orElseThrow();
		long expFrac = ntpExpectedObj.getFraction32bit().orElseThrow();
		assertEquals(3983496380L, expSec);
		assertEquals(579292303L, expFrac);

		NtpTimestamp anotherTest = NtpTimestamp.of(expSec, expFrac);
		assertEquals(ntpExpectedObj, anotherTest);

		assertTrue(deltaAbs < 5L);  // small difference expected due to a rounding error
		assertTrue(deltaNanosAbs < 2L);  // 1ns difference expected due to a rounding error
	}

	@Test
	void testEmpty() {
		final NtpTimestamp testObj = NtpTimestamp.ofEmpty();
		assertTrue(testObj.isEmpty());
	}

	@Test
	void testWithOverflow() throws RtspProtoNumberRangeException {
		final long expSec = 3983496380L;
		final long expFrac = 579292303L;
		final NtpTimestamp testObj = NtpTimestamp.of(expSec, expFrac);
		final NtpTimestamp anotherTest = NtpTimestamp.withOverflow(expSec, expFrac);
		assertEquals(testObj, anotherTest);
		assertFalse(testObj.isEmpty());
	}

	@Test
	void testClone() throws RtspProtoNumberRangeException {
		final NtpTimestamp testObj = NtpTimestamp.of(3983496380L, 579292303L);
		final NtpTimestamp anotherTest = testObj.clone();
		assertEquals(testObj, anotherTest);

		anotherTest.setTsAsUnsigned64bit(100L);
		assertNotEquals(testObj, anotherTest);
	}

	@Test
	void testWriteProtect() throws RtspProtoNumberRangeException {
		final NtpTimestamp testObj = NtpTimestamp.of(3983496380L, 579292303L);
		testObj.writeProtect();
		assertThrows(IllegalStateException.class, () -> testObj.setTsAsUnsigned64bit(200L));
	}

	@Test
	void testMin() {
		assertDoesNotThrow(() -> NtpTimestamp.of(0L, 0L));
	}

	@Test
	void testMax() {
		assertDoesNotThrow(() -> NtpTimestamp.of(0xFFFFFFFFL, 0xFFFFFFFFL));
	}

	@Test
	void testOutOfRange() {
		assertThrows(RtspProtoNumberRangeException.class, () -> NtpTimestamp.of(0x100000000L, 1001L));
		assertThrows(RtspProtoNumberRangeException.class, () -> NtpTimestamp.of(2002L, 0x100000000L));
		assertThrows(RtspProtoNumberRangeException.class, () -> NtpTimestamp.of(-1L, 1001L));
		assertThrows(RtspProtoNumberRangeException.class, () -> NtpTimestamp.of(2002L, -1L));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Baseline NTP time if bit-0=0 is 7-Feb-2036 @ 06:28:16 UTC
	 */
	private static final long msb0baseTime = 2085978496000L;
	/**
	 * Baseline NTP time if bit-0=1 is 1-Jan-1900 @ 01:00:00 UTC
	 */
	private static final long msb1baseTime = -2208988800000L;

	/**
	 * Converts Java time to 64-bit NTP time representation.
	 *
	 * @param millis Java time
	 * @return NTP timestamp representation of Java time value.
	 */
	private static long toNtpTime(final long millis) {
		final boolean useBase1 = millis < msb0baseTime; // time < Feb-2036
		final long baseTimeMillis;
		if (useBase1) {
			baseTimeMillis = millis - msb1baseTime; // dates <= Feb-2036
		} else {
			// if base0 needed for dates >= Feb-2036
			baseTimeMillis = millis - msb0baseTime;
		}

		long seconds = baseTimeMillis / 1000;
		final long fraction = baseTimeMillis % 1000 * 0x100000000L / 1000;

		if (useBase1) {
			seconds |= 0x80000000L; // set high-order bit if msb1baseTime 1900 used
		}

		return seconds << 32 | fraction;
	}

	/**
	 * Gets a Java standard time from a 64-bit NTP timestamp.
	 * <br />
	 * Note that java time (milliseconds), by definition, has less precision than NTP time (picoseconds) so converting NTP timestamp to Java time and back to NTP
	 * timestamp loses precision. For example, Tue, Dec 17 2002 09:07:24.810 EST is represented by a single Java-based time value of f22cd1fc8a, but its NTP
	 * equivalent are all values ranging from c1a9ae1c.cf5c28f5 to c1a9ae1c.cf9db22c.
	 *
	 * @param ntpTimeValue the input time
	 * @return the number of milliseconds since January 1, 1970, 00:00:00 GMT represented by this NTP timestamp value.
	 */
	private static long getTime(final long ntpTimeValue) {
		final long seconds = ntpTimeValue >>> 32 & 0xffffffffL; // high-order 32-bits
		long fraction = ntpTimeValue & 0xffffffffL; // low-order 32-bits

		// Use round-off on fractional part to preserve going to lower precision
		fraction = Math.round(1000D * fraction / 0x100000000L);

		/*
		 * If the most significant bit (MSB) on the seconds field is set we use a different time base. The following text is a quote from RFC-2030 (SNTP v4):
		 *
		 * If bit 0 is set, the UTC time is in the range 1968-2036 and UTC time is reckoned from 0h 0m 0s UTC on 1 January 1900. If bit 0 is not set, the time
		 * is in the range 2036-2104 and UTC time is reckoned from 6h 28m 16s UTC on 7 February 2036.
		 */
		final long msb = seconds & 0x80000000L;
		if (msb == 0) {
			// use base: 7-Feb-2036 @ 06:28:16 UTC
			return msb0baseTime + seconds * 1000 + fraction;
		}
		// use base: 1-Jan-1900 @ 01:00:00 UTC
		return msb1baseTime + seconds * 1000 + fraction;
	}

}

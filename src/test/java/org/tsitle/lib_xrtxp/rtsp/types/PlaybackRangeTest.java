package org.tsitle.lib_xrtxp.rtsp.types;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoPlaybackRange;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PlaybackRangeTest {

	@Test
	void testToAbsTimeString() {
		Instant tmpInstStart = Instant.parse("1996-11-08T14:37:20.25Z");
		TimestampEpochNs epochStart = TimestampEpochNs.ofInstant(tmpInstStart);
		final double startAbsSecs = (double) epochStart.getEpochNsUnsigned64bit().orElseThrow() / 1_000_000_000.0;

		//
		RtspProtoPlaybackRange range = RtspProtoPlaybackRange.ofAbsolute(startAbsSecs, 0.0, -1.0);
		String result = range.toAbsClockString().orElseThrow();
		assertEquals("clock=19961108T143720.25Z-", result);

		assertEquals(startAbsSecs, range.getAbsoluteTimeStartSecsAsDouble().orElseThrow());
		assertEquals(epochStart, range.getAbsoluteTimeStartAsTimestamp().orElseThrow());
		assertTrue(range.getAbsoluteTimeEndSecsAsDouble().isEmpty());
		assertTrue(range.getAbsoluteTimeEndAsTimestamp().isEmpty());

		//
		range = RtspProtoPlaybackRange.ofAbsolute(startAbsSecs, 11.34, -1.0);
		result = range.toAbsClockString().orElseThrow();
		assertEquals("clock=19961108T143731.59Z-", result);

		assertEquals(startAbsSecs + 11.34, range.getAbsoluteTimeStartSecsAsDouble().orElseThrow());
		TimestampEpochNs tmpEpochStartPlus = TimestampEpochNs.ofEpochNsUnsigned64bit(epochStart.getEpochNsUnsigned64bit().orElseThrow() + (long) (11.34 * 1_000_000_000.0));
		assertEquals(tmpEpochStartPlus, range.getAbsoluteTimeStartAsTimestamp().orElseThrow());
		assertTrue(range.getAbsoluteTimeEndSecsAsDouble().isEmpty());
		assertTrue(range.getAbsoluteTimeEndAsTimestamp().isEmpty());

		//
		range = RtspProtoPlaybackRange.ofAbsolute(startAbsSecs, 11.34, 60.0);
		result = range.toAbsClockString().orElseThrow();
		assertEquals("clock=19961108T143731.59Z-19961108T143820.25Z", result);

		assertEquals(startAbsSecs + 60.0, range.getAbsoluteTimeEndSecsAsDouble().orElseThrow());
		TimestampEpochNs tmpEpochEndPlus = TimestampEpochNs.ofEpochNsUnsigned64bit(epochStart.getEpochNsUnsigned64bit().orElseThrow() + (long) (60.0 * 1_000_000_000.0));
		assertEquals(tmpEpochEndPlus, range.getAbsoluteTimeEndAsTimestamp().orElseThrow());

		//
		range = RtspProtoPlaybackRange.ofAbsolute(epochStart, 11.34, 60.0);
		result = range.toAbsClockString().orElseThrow();
		assertEquals("clock=19961108T143731.59Z-19961108T143820.25Z", result);

		// -----------------------------------------

		Instant tmpInstEnd = Instant.parse("1996-11-08T14:38:20.25Z");
		TimestampEpochNs epochEnd = TimestampEpochNs.ofInstant(tmpInstEnd);

		//
		range = RtspProtoPlaybackRange.ofAbsolute(epochStart, epochEnd);
		result = range.toAbsClockString().orElseThrow();
		assertEquals("clock=19961108T143720.25Z-19961108T143820.25Z", result);
	}

	// -------------------------------------------------------

	@Test
	void testToSmpteString() {
		RtspProtoPlaybackRange range = RtspProtoPlaybackRange.ofRelative(0.0, -1.0);
		assertThrows(RuntimeException.class, range::toSmpteString);
	}

	// -------------------------------------------------------

	@Test
	void testToNptString_secs() {
		RtspProtoPlaybackRange range = RtspProtoPlaybackRange.ofRelative(10.123, 3.0);
		String result = range.toNptString_secs();
		assertEquals("npt=10.123-", result);

		//
		range = RtspProtoPlaybackRange.ofRelative(10.123, -1.0);
		result = range.toNptString_secs();
		assertEquals("npt=10.123-", result);

		assertEquals(10.123, range.getRelativeTimeStartSecs());
		assertTrue(range.getRelativeTimeEndSecs().isEmpty());

		//
		range = RtspProtoPlaybackRange.ofRelative(0.0, 10.123);
		result = range.toNptString_secs();
		assertEquals("npt=0-10.123", result);

		assertEquals(0.0, range.getRelativeTimeStartSecs());
		assertEquals(10.123, range.getRelativeTimeEndSecs().orElseThrow());

		//
		double tmpDbl = 10.123 + (60.0 * 3.0);
		range = RtspProtoPlaybackRange.ofRelative(0.0, tmpDbl);
		result = range.toNptString_secs();
		assertEquals("npt=0-" + String.format("%.3f", tmpDbl).replace(",", "."), result);

		//
		tmpDbl = 10.123 + (60.0 * 3.0) + (60.0 * 60.0 * 2.0);
		range = RtspProtoPlaybackRange.ofRelative(0.0, tmpDbl);
		result = range.toNptString_secs();
		assertEquals("npt=0-" + String.format("%.3f", tmpDbl).replace(",", "."), result);
	}

	@Test
	void testToNptString_hoursMinutesSecsMs() {
		RtspProtoPlaybackRange range = RtspProtoPlaybackRange.ofRelative(10.123, 3.0);
		String result = range.toNptString_hoursMinutesSecsMs();
		assertEquals("npt=0:00:10.122-", result);

		range = RtspProtoPlaybackRange.ofRelative(10.123, -1.0);
		result = range.toNptString_hoursMinutesSecsMs();
		assertEquals("npt=0:00:10.122-", result);

		range = RtspProtoPlaybackRange.ofRelative(0.0, 10.123);
		result = range.toNptString_hoursMinutesSecsMs();
		assertEquals("npt=0:00:00.0-0:00:10.122", result);

		range = RtspProtoPlaybackRange.ofRelative(0.0, 10.123 + (60.0 * 3.0));
		result = range.toNptString_hoursMinutesSecsMs();
		assertEquals("npt=0:00:00.0-0:03:10.122", result);

		range = RtspProtoPlaybackRange.ofRelative(0.0, 10.123 + (60.0 * 3.0) + (60.0 * 60.0 * 2.0));
		result = range.toNptString_hoursMinutesSecsMs();
		assertEquals("npt=0:00:00.0-2:03:10.122", result);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void testParseAbsTime() throws Exception {
		RtspProtoPlaybackRange range = RtspProtoPlaybackRange.parseString("clock=19960213T143205Z-;time=19970123T143720Z");
		double tmpStartDbl = range.getAbsoluteTimeStartSecsAsDouble().orElseThrow();
		TimestampEpochNs epochStart = TimestampEpochNs.ofEpochMsUnsigned64bit((long) (tmpStartDbl * 1_000.0));

		assertEquals("1996-02-13T14:32:05.000Z", epochStart.toIso8601StyleString());
		assertTrue(range.getAbsoluteTimeEndSecsAsDouble().isEmpty());

		//
		range = RtspProtoPlaybackRange.parseString("clock=19961108T143731.59Z-19961108T143820.25Z");
		tmpStartDbl = range.getAbsoluteTimeStartSecsAsDouble().orElseThrow();
		epochStart = TimestampEpochNs.ofEpochMsUnsigned64bit((long) (tmpStartDbl * 1_000.0));
		double tmpEndDbl = range.getAbsoluteTimeEndSecsAsDouble().orElseThrow();
		TimestampEpochNs epochEnd = TimestampEpochNs.ofEpochMsUnsigned64bit((long) (tmpEndDbl * 1_000.0));

		assertEquals("1996-11-08T14:37:31.590Z", epochStart.toIso8601StyleString());
		assertEquals("1996-11-08T14:38:20.250Z", epochEnd.toIso8601StyleString());
	}

	// -------------------------------------------------------

	@Test
	void testParseNpt() throws Exception {
		RtspProtoPlaybackRange range = RtspProtoPlaybackRange.parseString("npt=10.123-");
		assertEquals(10.123, range.getRelativeTimeStartSecs());
		assertTrue(range.getRelativeTimeEndSecs().isEmpty());

		//
		range = RtspProtoPlaybackRange.parseString("npt=now-");
		assertEquals(0.0, range.getRelativeTimeStartSecs());
		assertTrue(range.getRelativeTimeEndSecs().isEmpty());

		//
		range = RtspProtoPlaybackRange.parseString("npt=now-9876.543");
		assertEquals(0.0, range.getRelativeTimeStartSecs());
		assertEquals(9876.543, range.getRelativeTimeEndSecs().orElseThrow());

		//
		range = RtspProtoPlaybackRange.parseString("npt=555-9876.543");
		assertEquals(555.0, range.getRelativeTimeStartSecs());
		assertEquals(9876.543, range.getRelativeTimeEndSecs().orElseThrow());

		//
		range = RtspProtoPlaybackRange.parseString("npt=555.543-");
		assertEquals(555.543, range.getRelativeTimeStartSecs());
		assertTrue(range.getRelativeTimeEndSecs().isEmpty());
	}

}

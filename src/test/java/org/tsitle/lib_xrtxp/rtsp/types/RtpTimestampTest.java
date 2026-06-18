package org.tsitle.lib_xrtxp.rtsp.types;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;

import static org.junit.jupiter.api.Assertions.*;

class RtpTimestampTest {

	@Test
	void testEmpty() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.ofEmpty();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getTs32bit().isPresent());
	}

	@Test
	void testZero() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.ofZero();
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(0, testObj.getTs32bit().orElseThrow());
	}

	@Test
	void testOne() throws RtspProtoNumberRangeException {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.of(1);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(1, testObj.getTs32bit().orElseThrow());
	}

	@Test
	void testMax() throws RtspProtoNumberRangeException {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.of(0xFFFFFFFFL);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(0xFFFFFFFFL, testObj.getTs32bit().orElseThrow());
	}

	@Test
	void testOutOfRange() {
		assertThrows(RtspProtoNumberRangeException.class, () -> RtspProtoRtpTimestamp.of(-1L));
		assertThrows(RtspProtoNumberRangeException.class, () -> RtspProtoRtpTimestamp.of(0x100000000L));
	}

	@Test
	void testOverflow1() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.withOverflow(0xFFFFFFFFL);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(0xFFFFFFFFL, testObj.getTs32bit().orElseThrow());
	}

	@Test
	void testOverflow2() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.withOverflow(0x100000000L);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(0L, testObj.getTs32bit().orElseThrow());
	}

	@Test
	void testOverflow3() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.withOverflow(0xFFFFFFFFL + 11L);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(10L, testObj.getTs32bit().orElseThrow());
	}

	@Test
	void testAdd1() {
		RtspProtoRtpTimestamp inpObj = RtspProtoRtpTimestamp.withOverflow(0xFFFFFFFFL);
		RtspProtoRtpTimestamp testObj = inpObj.add(0L);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(0xFFFFFFFFL, testObj.getTs32bit().orElseThrow());
		assertEquals(testObj, inpObj);
	}

	@Test
	void testAdd2() {
		RtspProtoRtpTimestamp inpObj = RtspProtoRtpTimestamp.withOverflow(0xFFFFFFFFL);
		RtspProtoRtpTimestamp testObj = inpObj.add(1L);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(0L, testObj.getTs32bit().orElseThrow());
	}

	@Test
	void testAdd3() {
		RtspProtoRtpTimestamp inpObj = RtspProtoRtpTimestamp.withOverflow(0xFFFFFFFFL);
		RtspProtoRtpTimestamp testObj = inpObj.add(11L);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(10L, testObj.getTs32bit().orElseThrow());
		assertNotEquals(testObj, inpObj);
	}

	@Test
	void testWriteProtect() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.withOverflow(0x100000000L);
		testObj.writeProtect();

		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());
		assertEquals(0L, testObj.getTs32bit().orElseThrow());
		assertDoesNotThrow(() -> testObj.add(1));
		assertThrows(IllegalStateException.class, () -> testObj.setTs32bit(1L));
	}

	@Test
	void testClone1() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.withOverflow(0x100000000L);
		testObj.writeProtect();

		RtspProtoRtpTimestamp cloned = testObj.clone();
		assertEquals(testObj, cloned);

		assertThrows(IllegalStateException.class, () -> cloned.setTs32bit(1L));
	}

	@Test
	void testClone2() throws RtspProtoNumberRangeException {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.withOverflow(0x100000000L);

		RtspProtoRtpTimestamp cloned = testObj.clone();
		assertEquals(testObj, cloned);

		cloned.setTs32bit(1L);
		assertNotEquals(testObj, cloned);
	}

	@Test
	void testClear() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.withOverflow(0x100000000L);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getTs32bit().isPresent());

		testObj.clear();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getTs32bit().isPresent());
	}

	@Test
	void testCopyFrom1() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.ofEmpty();
		assertTrue(testObj.isEmpty());

		RtspProtoRtpTimestamp copiedObj = RtspProtoRtpTimestamp.ofEmpty();
		copiedObj.copyFrom(testObj);
		assertTrue(copiedObj.isEmpty());
	}

	@Test
	void testCopyFrom2() {
		RtspProtoRtpTimestamp testObj = RtspProtoRtpTimestamp.withOverflow(1001L);
		assertFalse(testObj.isEmpty());

		RtspProtoRtpTimestamp copiedObj = RtspProtoRtpTimestamp.ofEmpty();
		assertNotEquals(testObj, copiedObj);
		copiedObj.copyFrom(testObj);
		assertFalse(copiedObj.isEmpty());
		assertEquals(testObj, copiedObj);
		assertEquals(testObj.getTs32bit().orElseThrow(), copiedObj.getTs32bit().orElseThrow());
	}

}

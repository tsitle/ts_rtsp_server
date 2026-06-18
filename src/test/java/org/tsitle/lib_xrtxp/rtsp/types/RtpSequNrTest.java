package org.tsitle.lib_xrtxp.rtsp.types;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;

import static org.junit.jupiter.api.Assertions.*;

class RtpSequNrTest {

	@Test
	void testEmpty() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.ofEmpty();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getSeqNr16bit().isPresent());
	}

	@Test
	void testOne() throws RtspProtoNumberRangeException {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.of(1);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(1, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	void testMax() throws RtspProtoNumberRangeException {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.of(0xFFFF);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0xFFFF, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	void testOutOfRange() {
		assertThrows(RtspProtoNumberRangeException.class, () -> RtspProtoRtpSeqNr.of(-1));
		assertThrows(RtspProtoNumberRangeException.class, () -> RtspProtoRtpSeqNr.of(0x10000));
	}

	@Test
	void testOverflow1() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0xFFFF);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0xFFFF, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	void testOverflow2() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	void testOverflow3() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0xFFFF + 11);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(10, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	void testIncrement() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0xFFFF);
		testObj.increment();
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	void testWriteProtect() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);
		testObj.writeProtect();

		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0, testObj.getSeqNr16bit().orElseThrow());
		assertThrows(IllegalStateException.class, testObj::increment);
	}

	@Test
	void testClone1() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);
		testObj.writeProtect();

		RtspProtoRtpSeqNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		assertThrows(IllegalStateException.class, () -> cloned.setSeqNr16bit(1));
	}

	@Test
	void testClone2() throws RtspProtoNumberRangeException {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);

		RtspProtoRtpSeqNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		cloned.setSeqNr16bit(1);
		assertNotEquals(testObj, cloned);
	}

	@Test
	void testClear() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());

		testObj.clear();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getSeqNr16bit().isPresent());
	}

	@Test
	void testCopyFrom1() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.ofEmpty();
		assertTrue(testObj.isEmpty());

		RtspProtoRtpSeqNr copiedObj = RtspProtoRtpSeqNr.ofEmpty();
		copiedObj.copyFrom(testObj);
		assertTrue(copiedObj.isEmpty());
	}

	@Test
	void testCopyFrom2() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(1001);
		assertFalse(testObj.isEmpty());

		RtspProtoRtpSeqNr copiedObj = RtspProtoRtpSeqNr.ofEmpty();
		assertNotEquals(testObj, copiedObj);
		copiedObj.copyFrom(testObj);
		assertFalse(copiedObj.isEmpty());
		assertEquals(testObj, copiedObj);
		assertEquals(testObj.getSeqNr16bit().orElseThrow(), copiedObj.getSeqNr16bit().orElseThrow());
	}

}

package org.tsitle.rtsp.proto.types;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRtpSeqNr;

import static org.junit.jupiter.api.Assertions.*;

public class RtpSequNrTest {

	@Test
	public void testEmpty() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.ofEmpty();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getSeqNr16bit().isPresent());
	}

	@Test
	public void testOne() throws RtspProtoNumberRangeException {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.of(1);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(1, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	public void testMax() throws RtspProtoNumberRangeException {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.of(0xFFFF);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0xFFFF, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	public void testOutOfRange() {
		assertThrows(RtspProtoNumberRangeException.class, () -> RtspProtoRtpSeqNr.of(-1));
		assertThrows(RtspProtoNumberRangeException.class, () -> RtspProtoRtpSeqNr.of(0x10000));
	}

	@Test
	public void testOverflow1() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0xFFFF);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0xFFFF, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	public void testOverflow2() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	public void testOverflow3() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0xFFFF + 11);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(10, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	public void testIncrement() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0xFFFF);
		testObj.increment();
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0, testObj.getSeqNr16bit().orElseThrow());
	}

	@Test
	public void testWriteProtect() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);
		testObj.writeProtect();

		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());
		assertEquals(0, testObj.getSeqNr16bit().orElseThrow());
		assertThrows(IllegalStateException.class, testObj::increment);
	}

	@Test
	public void testClone1() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);
		testObj.writeProtect();

		RtspProtoRtpSeqNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		assertThrows(IllegalStateException.class, () -> cloned.setSeqNr16bit(1));
	}

	@Test
	public void testClone2() throws RtspProtoNumberRangeException {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);

		RtspProtoRtpSeqNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		cloned.setSeqNr16bit(1);
		assertNotEquals(testObj, cloned);
	}

	@Test
	public void testClear() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.withOverflow(0x10000);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getSeqNr16bit().isPresent());

		testObj.clear();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getSeqNr16bit().isPresent());
	}

	@Test
	public void testCopyFrom1() {
		RtspProtoRtpSeqNr testObj = RtspProtoRtpSeqNr.ofEmpty();
		assertTrue(testObj.isEmpty());

		RtspProtoRtpSeqNr copiedObj = RtspProtoRtpSeqNr.ofEmpty();
		copiedObj.copyFrom(testObj);
		assertTrue(copiedObj.isEmpty());
	}

	@Test
	public void testCopyFrom2() {
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

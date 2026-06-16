package org.tsitle.rtsp.proto.types;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoCseqNr;

import static org.junit.jupiter.api.Assertions.*;

public class CseqNrTest {

	@Test
	public void testEmpty() {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getCseq32bit().isPresent());
	}

	@Test
	public void testZero() {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofZero();
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getCseq32bit().isPresent());
		assertEquals(0, testObj.getCseq32bit().orElseThrow());
	}

	@Test
	public void testOne() throws RtspProtoNumberRangeException {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		testObj.setCseq32bit(1L);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getCseq32bit().isPresent());
		assertEquals(1, testObj.getCseq32bit().orElseThrow());
	}

	@Test
	public void testMax() throws RtspProtoNumberRangeException {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		testObj.setCseq32bit(0xFFFFFFFFL);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getCseq32bit().isPresent());
		assertEquals(0xFFFFFFFFL, testObj.getCseq32bit().orElseThrow());
	}

	@Test
	public void testOutOfRange() {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		assertThrows(RtspProtoNumberRangeException.class, () -> testObj.setCseq32bit(-1L));
		assertThrows(RtspProtoNumberRangeException.class, () -> testObj.setCseq32bit(0x100000000L));
	}

	@Test
	public void testIncrement() throws RtspProtoNumberRangeException {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		testObj.setCseq32bit(0xFFFFFFFFL);
		testObj.increment();
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getCseq32bit().isPresent());
		assertEquals(0L, testObj.getCseq32bit().orElseThrow());
	}

	@Test
	public void testWriteProtect() throws RtspProtoNumberRangeException {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		testObj.setCseq32bit(9009L);
		testObj.writeProtect();

		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getCseq32bit().isPresent());
		assertEquals(9009L, testObj.getCseq32bit().orElseThrow());
		assertThrows(IllegalStateException.class, () -> testObj.setCseq32bit(1L));
	}

	@Test
	public void testClone1() throws RtspProtoNumberRangeException {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		testObj.setCseq32bit(9009L);
		testObj.writeProtect();

		RtspProtoCseqNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		assertThrows(IllegalStateException.class, () -> cloned.setCseq32bit(1L));
	}

	@Test
	public void testClone2() throws RtspProtoNumberRangeException {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		testObj.setCseq32bit(9009L);

		RtspProtoCseqNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		cloned.setCseq32bit(1L);
		assertNotEquals(testObj, cloned);
	}

	@Test
	public void testClear() throws RtspProtoNumberRangeException {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		testObj.setCseq32bit(9009L);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getCseq32bit().isPresent());

		testObj.clear();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getCseq32bit().isPresent());
	}

	@Test
	public void testCopyFrom1() {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		assertTrue(testObj.isEmpty());

		RtspProtoCseqNr copiedObj = RtspProtoCseqNr.ofEmpty();
		copiedObj.copyFrom(testObj);
		assertTrue(copiedObj.isEmpty());
	}

	@Test
	public void testCopyFrom2() throws RtspProtoNumberRangeException {
		RtspProtoCseqNr testObj = RtspProtoCseqNr.ofEmpty();
		testObj.setCseq32bit(9009L);
		assertFalse(testObj.isEmpty());

		RtspProtoCseqNr copiedObj = RtspProtoCseqNr.ofEmpty();
		assertNotEquals(testObj, copiedObj);
		copiedObj.copyFrom(testObj);
		assertFalse(copiedObj.isEmpty());
		assertEquals(testObj, copiedObj);
		assertEquals(testObj.getCseq32bit().orElseThrow(), copiedObj.getCseq32bit().orElseThrow());
	}

}

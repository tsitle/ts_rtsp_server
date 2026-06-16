package org.tsitle.rtsp.proto.types;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSocketPortNr;

import static org.junit.jupiter.api.Assertions.*;

public class SocketPortNrTest {

	@Test
	public void testEmpty() {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.ofEmpty();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getPort16bit().isPresent());
	}

	@Test
	public void testMin() throws RtspProtoNumberRangeException {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.of(1);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getPort16bit().isPresent());
		assertEquals(1, testObj.getPort16bit().orElseThrow());
	}

	@Test
	public void testMax() throws RtspProtoNumberRangeException {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.of(0xFFFF);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getPort16bit().isPresent());
		assertEquals(0xFFFF, testObj.getPort16bit().orElseThrow());
	}

	@Test
	public void testOutOfRange() {
		assertThrows(RtspProtoNumberRangeException.class, () -> RtspProtoSocketPortNr.of(0));
		assertThrows(RtspProtoNumberRangeException.class, () -> RtspProtoSocketPortNr.of(0x10000));
	}

	@Test
	public void testWriteProtect() throws RtspProtoNumberRangeException {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.of(509);
		testObj.writeProtect();

		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getPort16bit().isPresent());
		assertEquals(509, testObj.getPort16bit().orElseThrow());
		assertThrows(IllegalStateException.class, () -> testObj.setPort16bit(100));
	}

	@Test
	public void testClone1() throws RtspProtoNumberRangeException {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.of(509);
		testObj.writeProtect();

		RtspProtoSocketPortNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		assertThrows(IllegalStateException.class, () -> cloned.setPort16bit(1));
	}

	@Test
	public void testClone2() throws RtspProtoNumberRangeException {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.of(509);

		RtspProtoSocketPortNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		cloned.setPort16bit(1);
		assertNotEquals(testObj, cloned);
	}

	@Test
	public void testClear() throws RtspProtoNumberRangeException {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.of(509);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getPort16bit().isPresent());

		testObj.clear();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getPort16bit().isPresent());
	}

	@Test
	public void testCopyFrom1() {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.ofEmpty();
		assertTrue(testObj.isEmpty());

		RtspProtoSocketPortNr copiedObj = RtspProtoSocketPortNr.ofEmpty();
		copiedObj.copyFrom(testObj);
		assertTrue(copiedObj.isEmpty());
	}

	@Test
	public void testCopyFrom2() throws RtspProtoNumberRangeException {
		RtspProtoSocketPortNr testObj = RtspProtoSocketPortNr.of(509);
		assertFalse(testObj.isEmpty());

		RtspProtoSocketPortNr copiedObj = RtspProtoSocketPortNr.ofEmpty();
		assertNotEquals(testObj, copiedObj);
		copiedObj.copyFrom(testObj);
		assertFalse(copiedObj.isEmpty());
		assertEquals(testObj, copiedObj);
		assertEquals(testObj.getPort16bit().orElseThrow(), copiedObj.getPort16bit().orElseThrow());
	}

}

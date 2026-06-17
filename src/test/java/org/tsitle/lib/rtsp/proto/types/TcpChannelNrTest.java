package org.tsitle.lib.rtsp.proto.types;

import org.junit.jupiter.api.Test;
import org.tsitle.lib.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoTcpChannelNr;

import static org.junit.jupiter.api.Assertions.*;

public class TcpChannelNrTest {

	@Test
	public void testEmpty() {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getChannel8bit().isPresent());
	}

	@Test
	public void testMin() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(0);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getChannel8bit().isPresent());
		assertEquals(0, testObj.getChannel8bit().orElseThrow());
	}

	@Test
	public void testMax() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(255);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getChannel8bit().isPresent());
		assertEquals(255, testObj.getChannel8bit().orElseThrow());
	}

	@Test
	public void testOutOfRange() {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		assertThrows(RtspProtoNumberRangeException.class, () -> testObj.setChannel8bit(-1));
		assertThrows(RtspProtoNumberRangeException.class, () -> testObj.setChannel8bit(256));
	}

	@Test
	public void testWriteProtect() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);
		testObj.writeProtect();

		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getChannel8bit().isPresent());
		assertEquals(77, testObj.getChannel8bit().orElseThrow());
		assertThrows(IllegalStateException.class, () -> testObj.setChannel8bit(9));
	}

	@Test
	public void testClone1() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);
		testObj.writeProtect();

		RtspProtoTcpChannelNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		assertThrows(IllegalStateException.class, () -> cloned.setChannel8bit(1));
	}

	@Test
	public void testClone2() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);

		RtspProtoTcpChannelNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		cloned.setChannel8bit(1);
		assertNotEquals(testObj, cloned);
	}

	@Test
	public void testClear() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getChannel8bit().isPresent());

		testObj.clear();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getChannel8bit().isPresent());
	}

	@Test
	public void testCopyFrom1() {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		assertTrue(testObj.isEmpty());

		RtspProtoTcpChannelNr copiedObj = RtspProtoTcpChannelNr.ofEmpty();
		copiedObj.copyFrom(testObj);
		assertTrue(copiedObj.isEmpty());
	}

	@Test
	public void testCopyFrom2() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);
		assertFalse(testObj.isEmpty());

		RtspProtoTcpChannelNr copiedObj = RtspProtoTcpChannelNr.ofEmpty();
		assertNotEquals(testObj, copiedObj);
		copiedObj.copyFrom(testObj);
		assertFalse(copiedObj.isEmpty());
		assertEquals(testObj, copiedObj);
		assertEquals(testObj.getChannel8bit().orElseThrow(), copiedObj.getChannel8bit().orElseThrow());
	}

}

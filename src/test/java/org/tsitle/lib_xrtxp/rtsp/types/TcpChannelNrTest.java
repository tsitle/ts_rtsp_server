package org.tsitle.lib_xrtxp.rtsp.types;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;

import static org.junit.jupiter.api.Assertions.*;

class TcpChannelNrTest {

	@Test
	void testEmpty() {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getChannel8bit().isPresent());
	}

	@Test
	void testMin() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(0);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getChannel8bit().isPresent());
		assertEquals(0, testObj.getChannel8bit().orElseThrow());
	}

	@Test
	void testMax() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(255);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getChannel8bit().isPresent());
		assertEquals(255, testObj.getChannel8bit().orElseThrow());
	}

	@Test
	void testOutOfRange() {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		assertThrows(RtspProtoNumberRangeException.class, () -> testObj.setChannel8bit(-1));
		assertThrows(RtspProtoNumberRangeException.class, () -> testObj.setChannel8bit(256));
	}

	@Test
	void testWriteProtect() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);
		testObj.writeProtect();

		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getChannel8bit().isPresent());
		assertEquals(77, testObj.getChannel8bit().orElseThrow());
		assertThrows(IllegalStateException.class, () -> testObj.setChannel8bit(9));
	}

	@Test
	void testClone1() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);
		testObj.writeProtect();

		RtspProtoTcpChannelNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		assertThrows(IllegalStateException.class, () -> cloned.setChannel8bit(1));
	}

	@Test
	void testClone2() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);

		RtspProtoTcpChannelNr cloned = testObj.clone();
		assertEquals(testObj, cloned);

		cloned.setChannel8bit(1);
		assertNotEquals(testObj, cloned);
	}

	@Test
	void testClear() throws RtspProtoNumberRangeException {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		testObj.setChannel8bit(77);
		assertFalse(testObj.isEmpty());
		assertTrue(testObj.getChannel8bit().isPresent());

		testObj.clear();
		assertTrue(testObj.isEmpty());
		assertFalse(testObj.getChannel8bit().isPresent());
	}

	@Test
	void testCopyFrom1() {
		RtspProtoTcpChannelNr testObj = RtspProtoTcpChannelNr.ofEmpty();
		assertTrue(testObj.isEmpty());

		RtspProtoTcpChannelNr copiedObj = RtspProtoTcpChannelNr.ofEmpty();
		copiedObj.copyFrom(testObj);
		assertTrue(copiedObj.isEmpty());
	}

	@Test
	void testCopyFrom2() throws RtspProtoNumberRangeException {
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

package org.tsitle.lib_xrtxp.kmd;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKdr;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import static org.junit.jupiter.api.Assertions.*;

class SrtxpKmdTest {

	@Test
	void simpleCloneMikey() throws RtspProtoNumberRangeException {
		SrtxpKmd kmd = SrtxpKmd.createForMikeyWithDefaults(
				SrtxpMki.ofAutoSized(1001L),
				RtspProtoIdXsrc.of(0xDEADBEEFL),
				SrtxpKdr.ofAutoSized(2002L)
			);

		compareKmds(kmd);
	}

	@Test
	void simpleCloneSdes() throws RtspProtoNumberRangeException {
		SrtxpKmd kmd = SrtxpKmd.createForLegacySdesWithDefaults(987, RtspProtoIdXsrc.of(0x874301FAL));

		compareKmds(kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void testMki_empty() {
		SrtxpMki mki = SrtxpMki.ofEmpty();
		assertTrue(mki.isEmpty());
		assertTrue(mki.getValue().isEmpty());

		mki = SrtxpMki.ofAutoSized(0L);  // <-- not empty
		assertFalse(mki.isEmpty());
		assertFalse(mki.getValue().isEmpty());

		mki = SrtxpMki.of(1000L, 0);  // <-- empty
		assertTrue(mki.isEmpty());
		assertTrue(mki.getValue().isEmpty());

		BufferExt buf = BufferExt.decodeHexString("0x0000000000000000");  // <-- not empty
		mki = SrtxpMki.ofBufferBigEndian(buf);
		assertFalse(mki.isEmpty());
		assertFalse(mki.getValue().isEmpty());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void testKdr_empty() {
		SrtxpKdr kdr = SrtxpKdr.ofEmpty();
		assertTrue(kdr.isEmpty());
		assertTrue(kdr.getValue().isEmpty());

		kdr = SrtxpKdr.ofAutoSized(0L);  // <-- empty
		assertTrue(kdr.isEmpty());
		assertTrue(kdr.getValue().isEmpty());

		kdr = SrtxpKdr.of(1000L, 0);  // <-- empty
		assertTrue(kdr.isEmpty());
		assertTrue(kdr.getValue().isEmpty());

		BufferExt buf = BufferExt.decodeHexString("0x0000000000000000");  // <-- empty
		kdr = SrtxpKdr.ofBufferBigEndian(buf);
		assertTrue(kdr.isEmpty());
		assertTrue(kdr.getValue().isEmpty());
	}

	@Test
	void testKdr_buffer1() {
		BufferExt buf = BufferExt.decodeHexString("0x0000000000000001");
		SrtxpKdr kdr = SrtxpKdr.ofBufferBigEndian(buf);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(8, kdr.getSizeBytes());
		assertEquals(1L, kdr.getValue().orElseThrow());
	}

	@Test
	void testKdr_buffer2() {
		BufferExt bufOrg = BufferExt.decodeHexString("0x1000000000000000");
		SrtxpKdr kdr = SrtxpKdr.ofBufferBigEndian(bufOrg);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(8, kdr.getSizeBytes());
		assertEquals(Long.parseUnsignedLong("1152921504606846976"), kdr.getValue().orElseThrow());

		BufferExt bufTest = kdr.toBufferExtBigEndian();
		assertEquals(bufOrg, bufTest);
	}

	@Test
	void testKdr_buffer3() {
		BufferExt bufOrg = BufferExt.decodeHexString("0x10000000");
		SrtxpKdr kdr = SrtxpKdr.ofBufferBigEndian(bufOrg);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(4, kdr.getSizeBytes());
		assertEquals(Long.parseUnsignedLong("268435456"), kdr.getValue().orElseThrow());

		BufferExt bufTest = kdr.toBufferExtBigEndian();
		assertEquals(bufOrg, bufTest);
	}

	@Test
	void testKdr_buffer4() {
		BufferExt bufOrg = BufferExt.decodeHexString("0x1000");
		SrtxpKdr kdr = SrtxpKdr.ofBufferBigEndian(bufOrg);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(2, kdr.getSizeBytes());
		assertEquals(Long.parseUnsignedLong("4096"), kdr.getValue().orElseThrow());

		BufferExt bufTest = kdr.toBufferExtBigEndian();
		assertEquals(bufOrg, bufTest);
	}

	@Test
	void testKdr_buffer5() {
		BufferExt bufOrg = BufferExt.decodeHexString("0x10");
		SrtxpKdr kdr = SrtxpKdr.ofBufferBigEndian(bufOrg);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(1, kdr.getSizeBytes());
		assertEquals(Long.parseUnsignedLong("16"), kdr.getValue().orElseThrow());

		BufferExt bufTest = kdr.toBufferExtBigEndian();
		assertEquals(bufOrg, bufTest);
	}

	@Test
	void testKdr_1byte() {
		SrtxpKdr kdr = SrtxpKdr.ofAutoSized(255L);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(1, kdr.getSizeBytes());
		assertEquals(255L, kdr.getValue().orElseThrow());
	}

	@Test
	void testKdr_2bytes() {
		SrtxpKdr kdr = SrtxpKdr.ofAutoSized(256L);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(2, kdr.getSizeBytes());
		assertEquals(256L, kdr.getValue().orElseThrow());

		kdr = SrtxpKdr.ofAutoSized(65535L);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(2, kdr.getSizeBytes());
		assertEquals(65535L, kdr.getValue().orElseThrow());
	}

	@Test
	void testKdr_4bytes() {
		SrtxpKdr kdr = SrtxpKdr.ofAutoSized(65536L);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(4, kdr.getSizeBytes());
		assertEquals(65536L, kdr.getValue().orElseThrow());

		kdr = SrtxpKdr.ofAutoSized(4294967295L);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(4, kdr.getSizeBytes());
		assertEquals(4294967295L, kdr.getValue().orElseThrow());
	}

	@Test
	void testKdr_8bytes() {
		SrtxpKdr kdr = SrtxpKdr.ofAutoSized(4294967296L);
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(8, kdr.getSizeBytes());
		assertEquals(4294967296L, kdr.getValue().orElseThrow());

		kdr = SrtxpKdr.ofAutoSized(Long.parseUnsignedLong("18446744073709551615"));
		assertFalse(kdr.isEmpty());
		assertTrue(kdr.getValue().isPresent());
		assertEquals(8, kdr.getSizeBytes());
		assertEquals(Long.parseUnsignedLong("18446744073709551615"), kdr.getValue().orElseThrow());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void compareKmds(@NonNull SrtxpKmd kmd) {
		SrtxpKmd clonedKmd = kmd.clone();

		assertNotSame(kmd, clonedKmd);
		assertEquals(kmd.getMetaIsForLegacySdes(), clonedKmd.getMetaIsForLegacySdes());
		assertEquals(kmd.getMetaTagForLegacySdes(), clonedKmd.getMetaTagForLegacySdes());
		assertEquals(kmd.encrKeyLen(), clonedKmd.encrKeyLen());
		assertEquals(kmd.masterKey(), clonedKmd.masterKey());
		assertEquals(kmd.masterSalt(), clonedKmd.masterSalt());
		assertEquals(kmd.getMasterKeyAndSaltAsBase64(), clonedKmd.getMasterKeyAndSaltAsBase64());
		assertEquals(kmd.authKeyLen(), clonedKmd.authKeyLen());
		assertEquals(kmd.authTagLen(), clonedKmd.authTagLen());
		assertEquals(kmd.mki(), clonedKmd.mki());
		assertEquals(kmd.ssrcId(), clonedKmd.ssrcId());
		assertEquals(kmd.kdr(), clonedKmd.kdr());

		//noinspection SimplifiableAssertion
		assertTrue(kmd.equals(clonedKmd));

		assertEquals(kmd.hashCode(), clonedKmd.hashCode());

		assertEquals(kmd.toString(), clonedKmd.toString());
	}

}

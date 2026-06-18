package org.tsitle.lib_xrtxp.kmd;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.kmd.types.DynInteger;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import static org.junit.jupiter.api.Assertions.*;

class SrtxpKmdTest {

	@Test
	void simpleCloneMikey() throws RtspProtoNumberRangeException {
		SrtxpKmd kmd = SrtxpKmd.createWithDefaults(
				1001L,
				RtspProtoIdXsrc.of(0xDEADBEEFL),
				DynInteger.createWithAutoSize(2002L)
			);

		compareKmds(kmd);
	}

	@Test
	void simpleCloneSdes() throws RtspProtoNumberRangeException {
		SrtxpKmd kmd = SrtxpKmd.createForLegacySdes(RtspProtoIdXsrc.of(0x874301FAL));

		compareKmds(kmd);
	}

	private static void compareKmds(@NonNull SrtxpKmd kmd) {
		SrtxpKmd clonedKmd = kmd.clone();

		assertNotSame(kmd, clonedKmd);
		assertEquals(kmd.isForLegacySdes(), clonedKmd.isForLegacySdes());
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

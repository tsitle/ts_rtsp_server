package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SrtxpKmdTest {

	@Test
	void simpleCloneMikey() {
		SrtxpKmd kmd = SrtxpKmd.createWithDefaults(
				1001L,
				0xDEADBEEF,
				DynInteger.createWithAutoSize(2002L)
			);

		compareKmds(kmd);
	}

	@Test
	void simpleCloneSdes() {
		SrtxpKmd kmd = SrtxpKmd.createForLegacySdes(0x874301FA);

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

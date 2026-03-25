package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.security.constants.KeySizes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class MikeyTest {

	@Test
	void encodeMsgRoundtrip1() throws Exception {
		final String inputMsgB64 = "AQAFALTv+/IBAAAEjxR/AAAAAAsA7W0MlxQSOBEKEIB1A+mz0UfnXMDKIEGhgZwBAAAAGwABAQEBEAIBAQMBFAQBDgcBAQgBAQoBAQsBCgAAACcAIQAeaP9/KzYKBaw4GmfXhK5jKD2ITMQKYOvSyn/gHwZwBAn6QCEA";

		SrtxpKmd kmd = MikeyParser.parseKeyMgmtData(inputMsgB64);
		final BufferExt expMasterEncKey = Common.createBufferFromHex("68FF7F2B360A05AC381A67D784AE6328");
		final BufferExt expMasterSalt = Common.createBufferFromHex("3D884CC40A60EBD2CA7FE01F0670");
		final int expAuthKeyLength = KeySizes.AUTH_KEY_SIZE_160;
		final BufferExt expMasterKeyIdentifier = Common.createBufferFromHex("09FA4021");
		final int expSsrcId = 0x048F147F;

		assertEquals(expMasterEncKey, kmd.masterKey());
		assertEquals(expMasterSalt, kmd.masterSalt());
		assertEquals(expAuthKeyLength, kmd.authKeyLen());
		assertEquals(expMasterKeyIdentifier, kmd.mki());
		assertEquals(expSsrcId, kmd.ssrcId());

		//
		final String outputMsgB64 = MikeyGenerator.generate(kmd);

		kmd = MikeyParser.parseKeyMgmtData(outputMsgB64);

		assertEquals(expMasterEncKey, kmd.masterKey());
		assertEquals(expMasterSalt, kmd.masterSalt());
		assertEquals(expAuthKeyLength, kmd.authKeyLen());
		assertEquals(expMasterKeyIdentifier, kmd.mki());
		assertEquals(expSsrcId, kmd.ssrcId());
	}

	@Test
	void encodeMsgRoundtrip2() throws Exception {
		final int expSsrcId = 0x8F147FAB;

		SrtxpKmd kmdExp = SrtxpKmd.createWithDefaults(expSsrcId);
		final String outputMsgB64 = MikeyGenerator.generate(kmdExp);
		SrtxpKmd kmdActual = MikeyParser.parseKeyMgmtData(outputMsgB64);

		assertEquals(kmdExp, kmdActual);
	}

	@Test
	void encodeMsgRoundtrip3() throws Exception {
		final int expSsrcId = 0x147FAB12;
		final BufferExt expMasterKeyIdentifier = Common.createBufferFromHex("09FA4021DEADBEEF0123");

		SrtxpKmd kmdPre = SrtxpKmd.createWithDefaults(expSsrcId);
		SrtxpKmd kmdExp = new SrtxpKmd(
				kmdPre.masterKey(),
				kmdPre.masterSalt(),
				KeySizes.AUTH_KEY_SIZE_080,
				expMasterKeyIdentifier,
				expSsrcId
			);
		final String outputMsgB64 = MikeyGenerator.generate(kmdExp);
		SrtxpKmd kmdActual = MikeyParser.parseKeyMgmtData(outputMsgB64);

		assertEquals(kmdExp, kmdActual);
	}

	@Test
	void encodeMsgRoundtrip4() throws Exception {
		final int expSsrcId = 0x147FAB12;
		final BufferExt expMasterKeyIdentifier = Common.createBufferFromHex("09FA4021DEADBEEF0123");

		SrtxpKmd kmdPre = SrtxpKmd.createWithDefaults(expSsrcId);

		SrtxpKmd kmdInpA = new SrtxpKmd(
				kmdPre.masterKey(),
				kmdPre.masterSalt(),
				KeySizes.AUTH_KEY_SIZE_080,
				expMasterKeyIdentifier,
				expSsrcId
			);
		final String outputMsgB64a = MikeyGenerator.generate(kmdInpA);
		SrtxpKmd kmdResA = MikeyParser.parseKeyMgmtData(outputMsgB64a);

		SrtxpKmd kmdInpB = new SrtxpKmd(
				kmdPre.masterKey(),
				kmdPre.masterSalt(),
				KeySizes.AUTH_KEY_SIZE_080,
				expMasterKeyIdentifier,
				expSsrcId + 1
			);
		final String outputMsgB64b = MikeyGenerator.generate(kmdInpB);
		SrtxpKmd kmdResB = MikeyParser.parseKeyMgmtData(outputMsgB64b);

		assertNotEquals(kmdResA, kmdResB);
	}

}

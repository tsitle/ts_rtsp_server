package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.security.constants.KeySizes;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MikeyTest {

	@Test
	void decodeMsg() throws Exception {
		final String msgB64 = "AQAFALTv+/IBAAAEjxR/AAAAAAsA7W0MlxQSOBEKEIB1A+mz0UfnXMDKIEGhgZwBAAAAGwABAQEBEAIBAQMBFAQBDgcBAQgBAQoBAQsBCgAAACcAIQAeaP9/KzYKBaw4GmfXhK5jKD2ITMQKYOvSyn/gHwZwBAn6QCEA";

		final SrtxpKmd kmd = MikeyParser.parseKeyMgmtData(msgB64);
		final BufferExt expMasterEncKey = Common.createBufferFromHex("68FF7F2B360A05AC381A67D784AE6328");
		final BufferExt expMasterSalt = Common.createBufferFromHex("3D884CC40A60EBD2CA7FE01F0670");
		final int expAuthKeyLength = KeySizes.AUTH_KEY_SIZE_160;
		final BufferExt expMasterKeyIdentifier = Common.createBufferFromHex("09FA4021");

		assertEquals(expMasterEncKey, kmd.masterKey());
		assertEquals(expMasterSalt, kmd.masterSalt());
		assertEquals(expAuthKeyLength, kmd.authKeyLen());
		assertEquals(expMasterKeyIdentifier, kmd.mki());
	}

}

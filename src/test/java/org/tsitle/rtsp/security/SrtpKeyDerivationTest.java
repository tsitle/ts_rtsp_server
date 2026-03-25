package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class SrtpKeyDerivationTest {

	@Test
	void rfc3711_rtp_vector_should_match() throws SrtpSecurityException {
		SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(0xABCDEF12);

		final BufferExt expectedEncKey = Common.createBufferFromHex("C61E7A93744F39EE10734AFE3FF7A087");
		final BufferExt expectedAuthKey_080 = Common.createBufferFromHex("CEBE321F6FF7716B6FD4");
		final BufferExt expectedAuthKey_160 = Common.createBufferFromHex("CEBE321F6FF7716B6FD4AB49AF256A156D38BAA4");
		final BufferExt expectedSalt = Common.createBufferFromHex("30CBBC08863D8C85D49DB34A9AE1");

		assertEquals(expectedEncKey, rtpKeys.encKey(), "RTP enc key mismatch");
		//noinspection ConstantValue
		if (Common.AUTH_KEY_SIZE_FOR_ALL_TESTS == KeySizes.AUTH_KEY_SIZE_080) {
			assertEquals(expectedAuthKey_080, rtpKeys.authKey(), "RTP auth key mismatch");
		} else {
			assertEquals(expectedAuthKey_160, rtpKeys.authKey(), "RTP auth key mismatch");
		}
		assertEquals(expectedSalt, rtpKeys.salt(), "RTP salt mismatch");
	}

	@Test
	void rfc3711_rtcp_vector_should_match() throws SrtpSecurityException {
		SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(0xABCDEF12);

		final BufferExt expectedEncKey = Common.createBufferFromHex("4C1AA45A81F73D61C800BBB00FBB1EAA");
		final BufferExt expectedAuthKey_080 = Common.createBufferFromHex("8D54534FEB49AE8E7993");
		final BufferExt expectedAuthKey_160 = Common.createBufferFromHex("8D54534FEB49AE8E7993A6BD0B844FC323A93DFD");
		final BufferExt expectedSalt = Common.createBufferFromHex("9581C7AD87B3E530BF3E4454A8B3");

		assertEquals(expectedEncKey, rtcpKeys.encKey(), "RTCP enc key mismatch");
		//noinspection ConstantValue
		if (Common.AUTH_KEY_SIZE_FOR_ALL_TESTS == KeySizes.AUTH_KEY_SIZE_080) {
			assertEquals(expectedAuthKey_080, rtcpKeys.authKey(), "RTCP auth key mismatch");
		} else {
			assertEquals(expectedAuthKey_160, rtcpKeys.authKey(), "RTCP auth key mismatch");
		}
		assertEquals(expectedSalt, rtcpKeys.salt(), "RTCP salt mismatch");
	}

	@Test
	void should_match_independent_reference_for_rtp_and_rtcp() throws Exception {
		byte[] masterKey = new byte[16];
		byte[] masterSalt = new byte[14];
		new SecureRandom().nextBytes(masterKey);
		new SecureRandom().nextBytes(masterSalt);

		//
		final int ssrcId = 0xABCDEF12;
		SessionKeys rtpKeys = Common.createSessionKeysNonDefRtp(ssrcId, Common.createBufferFromBa(masterKey), Common.createBufferFromBa(masterSalt));
		SessionKeys rtcpKeys = Common.createSessionKeysNonDefRtcp(ssrcId, Common.createBufferFromBa(masterKey), Common.createBufferFromBa(masterSalt));

		// Independent reference (RFC 3711, key_derivation_rate=0 => r=0)
		byte[] refRtpEnc = refDerive(masterKey, masterSalt, (byte) 0x00, 16);
		byte[] refRtpAuth = refDerive(masterKey, masterSalt, (byte) 0x01, Common.AUTH_KEY_SIZE_FOR_ALL_TESTS);
		byte[] refRtpSalt = refDerive(masterKey, masterSalt, (byte) 0x02, 14);
		byte[] refRtcpEnc = refDerive(masterKey, masterSalt, (byte) 0x03, 16);
		byte[] refRtcpAuth = refDerive(masterKey, masterSalt, (byte) 0x04, Common.AUTH_KEY_SIZE_FOR_ALL_TESTS);
		byte[] refRtcpSalt = refDerive(masterKey, masterSalt, (byte) 0x05, 14);

		assertEquals(Common.createBufferFromBa(refRtpEnc), rtpKeys.encKey(), "RTP enc mismatch");
		assertEquals(Common.createBufferFromBa(refRtpAuth), rtpKeys.authKey(), "RTP auth mismatch");
		assertEquals(Common.createBufferFromBa(refRtpSalt), rtpKeys.salt(), "RTP salt mismatch");

		assertEquals(Common.createBufferFromBa(refRtcpEnc), rtcpKeys.encKey(), "RTCP enc mismatch");
		assertEquals(Common.createBufferFromBa(refRtcpAuth), rtcpKeys.authKey(), "RTCP auth mismatch");
		assertEquals(Common.createBufferFromBa(refRtcpSalt), rtcpKeys.salt(), "RTCP salt mismatch");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static byte[] refDerive(byte[] masterKey, byte[] masterSalt, byte label, int outLen) throws Exception {
		byte[] iv = buildIv(masterSalt, label, 0L); // r=0
		Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new IvParameterSpec(iv));
		return cipher.doFinal(new byte[outLen]);
	}

	private static byte[] buildIv(byte[] masterSalt, byte label, @SuppressWarnings("SameParameterValue") long r48) {
		// x is 14 bytes (112 bits), IV is x || 0x0000 (x * 2^16)
		byte[] iv = new byte[16];
		System.arraycopy(masterSalt, 0, iv, 0, 14);

		// key_id right-aligned in 112 bits: occupies bytes [7..13] as <label || r(48)>
		iv[7] ^= label;
		iv[8] ^= (byte) ((r48 >>> 40) & 0xFF);
		iv[9] ^= (byte) ((r48 >>> 32) & 0xFF);
		iv[10] ^= (byte) ((r48 >>> 24) & 0xFF);
		iv[11] ^= (byte) ((r48 >>> 16) & 0xFF);
		iv[12] ^= (byte) ((r48 >>> 8) & 0xFF);
		iv[13] ^= (byte) (r48 & 0xFF);

		return iv;
	}

}

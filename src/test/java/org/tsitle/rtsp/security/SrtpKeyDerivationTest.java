package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;
import org.tsitle.lib.rtsp.proto.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdXsrc;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class SrtpKeyDerivationTest {

	@SuppressWarnings("ConstantValue")
	@Test
	void rfc3711_rtp_vector_should_match() throws SrtxpSecurityException, RtspProtoNumberRangeException {
		SessionKeys rtpKeys = Common.createSessionKeysDefaultRtp(RtspProtoIdXsrc.of(0xABCDEF12L));

		final BufferExt expectedEncKey_128 = BufferExt.decodeHexString("0xC61E7A93744F39EE10734AFE3FF7A087");
		final BufferExt expectedEncKey_256 = BufferExt.decodeHexString("0x163E3C392D9CD97AC1B621097628F62BDE51B3E6540F74036094F0B8243BAFCA");
		final BufferExt expectedAuthKey_enc128_160 = BufferExt.decodeHexString("0xCEBE321F6FF7716B6FD4AB49AF256A156D38BAA4");
		final BufferExt expectedAuthKey_enc256_160 = BufferExt.decodeHexString("0x2535AD8800A26BDF4B44595ADDF974132E5071D2");
		final BufferExt expectedSalt_enc128 = BufferExt.decodeHexString("0x30CBBC08863D8C85D49DB34A9AE1");
		final BufferExt expectedSalt_enc256 = BufferExt.decodeHexString("0x2F0DD6D4C67A8F13252DB94CB327");

		if (Common.ENCR_KEY_SIZE_FOR_ALL_TESTS == KeySizes.AES_KEY_SIZE_128) {
			assertEquals(expectedEncKey_128, rtpKeys.encKey(), "RTP enc key mismatch");
			if (Common.AUTH_KEY_SIZE_FOR_ALL_TESTS == KeySizes.AUTH_KEY_SIZE_080) {
				expectedAuthKey_enc128_160.setUsed(KeySizes.AUTH_KEY_SIZE_080);
			}
			assertEquals(expectedAuthKey_enc128_160, rtpKeys.authKey(), "RTP auth key mismatch");
			assertEquals(expectedSalt_enc128, rtpKeys.salt(), "RTP salt mismatch");
		} else {
			assertEquals(expectedEncKey_256, rtpKeys.encKey(), "RTP enc key mismatch");
			if (Common.AUTH_KEY_SIZE_FOR_ALL_TESTS == KeySizes.AUTH_KEY_SIZE_080) {
				expectedAuthKey_enc256_160.setUsed(KeySizes.AUTH_KEY_SIZE_080);
			}
			assertEquals(expectedAuthKey_enc256_160, rtpKeys.authKey(), "RTP auth key mismatch");
			assertEquals(expectedSalt_enc256, rtpKeys.salt(), "RTP salt mismatch");
		}
	}

	@SuppressWarnings("ConstantValue")
	@Test
	void rfc3711_rtcp_vector_should_match() throws SrtxpSecurityException, RtspProtoNumberRangeException {
		SessionKeys rtcpKeys = Common.createSessionKeysDefaultRtcp(RtspProtoIdXsrc.of(0xABCDEF12L));

		final BufferExt expectedEncKey_128 = BufferExt.decodeHexString("4C1AA45A81F73D61C800BBB00FBB1EAA");
		final BufferExt expectedEncKey_256 = BufferExt.decodeHexString("2D3D918C03825CF2C091ABA8EAFBC090F2609985446270ED3F610C22B479291B");
		final BufferExt expectedAuthKey_enc128_160 = BufferExt.decodeHexString("8D54534FEB49AE8E7993A6BD0B844FC323A93DFD");
		final BufferExt expectedAuthKey_enc256_160 = BufferExt.decodeHexString("CD09E179C29B756329F3438B3DEC9EFE7145E33E");
		final BufferExt expectedSalt_enc128 = BufferExt.decodeHexString("9581C7AD87B3E530BF3E4454A8B3");
		final BufferExt expectedSalt_enc256 = BufferExt.decodeHexString("31EAF346E35590AB054000CB2092");

		if (Common.ENCR_KEY_SIZE_FOR_ALL_TESTS == KeySizes.AES_KEY_SIZE_128) {
			assertEquals(expectedEncKey_128, rtcpKeys.encKey(), "RTCP enc key mismatch");
			if (Common.AUTH_KEY_SIZE_FOR_ALL_TESTS == KeySizes.AUTH_KEY_SIZE_080) {
				expectedAuthKey_enc128_160.setUsed(KeySizes.AUTH_KEY_SIZE_080);
			}
			assertEquals(expectedAuthKey_enc128_160, rtcpKeys.authKey(), "RTCP auth key mismatch");
			assertEquals(expectedSalt_enc128, rtcpKeys.salt(), "RTCP salt mismatch");
		} else {
			assertEquals(expectedEncKey_256, rtcpKeys.encKey(), "RTCP enc key mismatch");
			if (Common.AUTH_KEY_SIZE_FOR_ALL_TESTS == KeySizes.AUTH_KEY_SIZE_080) {
				expectedAuthKey_enc256_160.setUsed(KeySizes.AUTH_KEY_SIZE_080);
			}
			assertEquals(expectedAuthKey_enc256_160, rtcpKeys.authKey(), "RTCP auth key mismatch");
			assertEquals(expectedSalt_enc256, rtcpKeys.salt(), "RTCP salt mismatch");
		}
	}

	@Test
	void should_match_independent_reference_for_rtp_and_rtcp() throws Exception {
		byte[] masterKey = new byte[Common.ENCR_KEY_SIZE_FOR_ALL_TESTS];
		byte[] masterSalt = new byte[KeySizes.SALT_SIZE];
		new SecureRandom().nextBytes(masterKey);
		new SecureRandom().nextBytes(masterSalt);

		//
		final RtspProtoIdXsrc ssrcId = RtspProtoIdXsrc.of(0xABCDEF12L);
		SessionKeys rtpKeys = Common.createSessionKeysNonDefRtp(
				new BufferExt(masterKey),
				new BufferExt(masterSalt),
				Common.AUTH_KEY_SIZE_FOR_ALL_TESTS,
				Common.AUTH_TAG_SIZE_FOR_ALL_TESTS,
				ssrcId
			);
		SessionKeys rtcpKeys = Common.createSessionKeysNonDefRtcp(
				new BufferExt(masterKey),
				new BufferExt(masterSalt),
				Common.AUTH_KEY_SIZE_FOR_ALL_TESTS,
				Common.AUTH_TAG_SIZE_FOR_ALL_TESTS,
				ssrcId
			);

		// Independent reference (RFC-3711, key_derivation_rate=0 => r=0)
		byte[] refRtpEnc = refDerive(masterKey, masterSalt, (byte) 0x00, Common.ENCR_KEY_SIZE_FOR_ALL_TESTS);
		byte[] refRtpAuth = refDerive(masterKey, masterSalt, (byte) 0x01, Common.AUTH_KEY_SIZE_FOR_ALL_TESTS);
		byte[] refRtpSalt = refDerive(masterKey, masterSalt, (byte) 0x02, KeySizes.SALT_SIZE);
		byte[] refRtcpEnc = refDerive(masterKey, masterSalt, (byte) 0x03, Common.ENCR_KEY_SIZE_FOR_ALL_TESTS);
		byte[] refRtcpAuth = refDerive(masterKey, masterSalt, (byte) 0x04, Common.AUTH_KEY_SIZE_FOR_ALL_TESTS);
		byte[] refRtcpSalt = refDerive(masterKey, masterSalt, (byte) 0x05, KeySizes.SALT_SIZE);

		assertEquals(new BufferExt(refRtpEnc), rtpKeys.encKey(), "RTP enc mismatch");
		assertEquals(new BufferExt(refRtpAuth), rtpKeys.authKey(), "RTP auth mismatch");
		assertEquals(new BufferExt(refRtpSalt), rtpKeys.salt(), "RTP salt mismatch");

		assertEquals(new BufferExt(refRtcpEnc), rtcpKeys.encKey(), "RTCP enc mismatch");
		assertEquals(new BufferExt(refRtcpAuth), rtcpKeys.authKey(), "RTCP auth mismatch");
		assertEquals(new BufferExt(refRtcpSalt), rtcpKeys.salt(), "RTCP salt mismatch");
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
		byte[] iv = new byte[KeySizes.IV_SIZE];  // always 16 bytes, regardless of key size
		System.arraycopy(masterSalt, 0, iv, 0, KeySizes.SALT_SIZE);

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

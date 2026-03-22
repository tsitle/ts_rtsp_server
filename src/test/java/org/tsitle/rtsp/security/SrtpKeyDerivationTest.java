package org.tsitle.rtsp.security;

import org.junit.jupiter.api.Test;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SrtpKeyDerivationTest {

	private static final HexFormat HEX = HexFormat.of();

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Test
	void rfc3711_rtp_vector_should_match() throws SrtpSecurityException {
		// RFC 3711 key derivation test vector (AES-128 / HMAC-SHA1 / salt 112)
		byte[] masterKey = HEX.parseHex("E1F97A0D3E018BE0D64FA32C06DE4139");
		byte[] masterSalt = HEX.parseHex("0EC675AD498AFEEBB6960B3AABE6");

		SrtpKeyDerivation.SessionKeys rtp = SrtpKeyDerivation.deriveForRtp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

		assertArrayEquals(
				HEX.parseHex("C61E7A93744F39EE10734AFE3FF7A087"),
				rtp.encKey(),
				"RTP enc key mismatch"
			);
		assertArrayEquals(
				HEX.parseHex("CEBE321F6FF7716B6FD4AB49AF256A156D38BAA4"),
				rtp.authKey(),
				"RTP auth key mismatch"
			);
		assertArrayEquals(
				HEX.parseHex("30CBBC08863D8C85D49DB34A9AE1"),
				rtp.salt(),
				"RTP salt mismatch"
			);
	}

	@Test
	void should_match_independent_reference_for_rtp_and_rtcp() throws Exception {
		byte[] masterKey = new byte[16];
		byte[] masterSalt = new byte[14];
		new SecureRandom().nextBytes(masterKey);
		new SecureRandom().nextBytes(masterSalt);

		// Your implementation
		SrtpKeyDerivation.SessionKeys myRtp = SrtpKeyDerivation.deriveForRtp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);
		SrtpKeyDerivation.SessionKeys myRtcp = SrtpKeyDerivation.deriveForRtcp(masterKey, masterSalt, KeySizes.AUTH_KEY_SIZE_160);

		// Independent reference (RFC 3711, key_derivation_rate=0 => r=0)
		byte[] refRtpEnc = refDerive(masterKey, masterSalt, (byte) 0x00, 16);
		byte[] refRtpAuth = refDerive(masterKey, masterSalt, (byte) 0x01, 20);
		byte[] refRtpSalt = refDerive(masterKey, masterSalt, (byte) 0x02, 14);
		byte[] refRtcpEnc = refDerive(masterKey, masterSalt, (byte) 0x03, 16);
		byte[] refRtcpAuth = refDerive(masterKey, masterSalt, (byte) 0x04, 20);
		byte[] refRtcpSalt = refDerive(masterKey, masterSalt, (byte) 0x05, 14);

		assertArrayEquals(refRtpEnc, myRtp.encKey(), "RTP enc mismatch");
		assertArrayEquals(refRtpAuth, myRtp.authKey(), "RTP auth mismatch");
		assertArrayEquals(refRtpSalt, myRtp.salt(), "RTP salt mismatch");

		assertArrayEquals(refRtcpEnc, myRtcp.encKey(), "RTCP enc mismatch");
		assertArrayEquals(refRtcpAuth, myRtcp.authKey(), "RTCP auth mismatch");
		assertArrayEquals(refRtcpSalt, myRtcp.salt(), "RTCP salt mismatch");
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

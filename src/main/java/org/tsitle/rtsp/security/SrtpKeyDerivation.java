package org.tsitle.rtsp.security;

import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;
import org.tsitle.rtsp.security.constants.PrfDeriveLabel;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class SrtpKeyDerivation {

	public record SessionKeys(byte[] encKey, byte[] authKey, byte[] salt) { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Derive session keys for SRTP according to RFC-3711 Section 4.3.1
	 * @param masterKey Master key
	 * @param masterSalt Master salt
	 * @param authKeyLen Authentication key length
	 * @return Session keys
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public static SessionKeys deriveForRtp(byte[] masterKey, byte[] masterSalt, int authKeyLen) throws SrtpSecurityException {
		byte[] encKey = prf(masterKey, masterSalt, PrfDeriveLabel.PDL_RTP_ENC, KeySizes.AES_128_KEY_SIZE);
		byte[] authKey = prf(masterKey, masterSalt, PrfDeriveLabel.PDL_RTP_AUTH, authKeyLen);
		byte[] salt = prf(masterKey, masterSalt, PrfDeriveLabel.PDL_RTP_SALT, KeySizes.SALT_SIZE);

		return new SessionKeys(encKey, authKey, salt);
	}

	/**
	 * Derive session keys for SRTCP according to RFC-3711 Section 4.3.1
	 * @param masterKey Master key
	 * @param masterSalt Master salt
	 * @param authKeyLen Authentication key length
	 * @return Session keys
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public static SessionKeys deriveForRtcp(byte[] masterKey, byte[] masterSalt, int authKeyLen) throws SrtpSecurityException {
		byte[] encKey = prf(masterKey, masterSalt, PrfDeriveLabel.PDL_RTCP_ENC, KeySizes.AES_128_KEY_SIZE);
		byte[] authKey = prf(masterKey, masterSalt, PrfDeriveLabel.PDL_RTCP_AUTH, authKeyLen);
		byte[] salt = prf(masterKey, masterSalt, PrfDeriveLabel.PDL_RTCP_SALT, KeySizes.SALT_SIZE);

		return new SessionKeys(encKey, authKey, salt);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * PRF (Pseudo Random Function) for Key Derivation
	 */
	private static byte[] prf(
				byte[] masterKey,
				byte[] masterSalt,
				PrfDeriveLabel label,
				int outLen
			) throws SrtpSecurityException {
		if (masterKey.length != KeySizes.AES_128_KEY_SIZE) {
			throw new SrtpSecurityException("Invalid master key length, expected " + KeySizes.AES_128_KEY_SIZE + " bytes");
		}
		if (masterSalt.length != KeySizes.SALT_SIZE) {
			throw new SrtpSecurityException("Invalid master salt length, expected " + KeySizes.SALT_SIZE + " bytes");
		}

		/*
		 * Let r = index DIV key_derivation_rate -- (key_derivation_rate = 0 => r = 0) -- 'r' is 48-bit
		 * Let key_id = <label> || r -- 'key_id' is 48-bit
		 * Let x = key_id XOR master_salt, where key_id and master_salt are
		 *   aligned so that their least significant bits agree (right-alignment).
		 *
		 * Note that for a key_derivation_rate of 0, the application of the key derivation SHALL take place exactly once.
		 *
		 * PRF_n(k_master,x) SHALL be AES in Counter Mode as described in Section 4.1.1,
		 * applied to key k_master, and IV equal to (x*2^16),
		 * and with the output keystream truncated to the n first (left-most) bits.
		 */

		byte[] iv = buildKeyDerivationIv(masterSalt, label.getValue(), 0L);

		try {
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE,
					new SecretKeySpec(masterKey, "AES"),
					new IvParameterSpec(iv));

			byte[] zeros = new byte[outLen];
			return cipher.doFinal(zeros);
		} catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
				InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	private static byte[] buildKeyDerivationIv(
				byte[] masterSalt,
				byte label,
				@SuppressWarnings("SameParameterValue") long r48
			) {
		// x is 112 bits (14 bytes), IV is 128 bits (16 bytes)
		byte[] iv = new byte[KeySizes.AES_128_KEY_SIZE];

		// IV = x = master_salt
		System.arraycopy(masterSalt, 0, iv, 0, KeySizes.SALT_SIZE);

		// right-aligned key_id = <label>(8) || r(48) occupies x bytes [7..13]
		int offs = ((112 - 48) / 8) - 1;
		iv[offs++] ^= label;
		iv[offs++] ^= (byte) ((r48 >>> 40) & 0xFF);
		iv[offs++] ^= (byte) ((r48 >>> 32) & 0xFF);
		iv[offs++] ^= (byte) ((r48 >>> 24) & 0xFF);
		iv[offs++] ^= (byte) ((r48 >>> 16) & 0xFF);
		iv[offs++] ^= (byte) ((r48 >>> 8) & 0xFF);
		iv[offs] ^= (byte) (r48 & 0xFF);

		// iv[14] and iv[15] remain zero => x * 2^16
		return iv;
	}

}

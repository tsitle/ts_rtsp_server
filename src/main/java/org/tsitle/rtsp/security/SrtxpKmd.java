package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.constants.KeySizes;

import java.util.Objects;

/**
 * SRTxP Key Management Data
 */
public final class SrtxpKmd implements Cloneable {

	public static final int DEFAULT_ENCR_KEY_LEN = KeySizes.AES_KEY_SIZE_128;  // VLC requires 128-bit encr key
	public static final int DEFAULT_AUTH_KEY_LEN = KeySizes.AUTH_KEY_SIZE_160;  // VLC requires 160-bit auth key
	public static final int DEFAULT_AUTH_TAG_LEN = 10;  // VLC requires 10-byte auth tag
	public static final int DEFAULT_MKI_LEN = 4;  // VLC requires 4-byte MKI
	public static final long DEFAULT_KDR_PACKETS = 0;  // VLC requires a KDR of 0

	/** Encryption Key length */
	private final int encrKeyLen;
	/** Master AES-128 key (16 bytes) */
	private @NonNull BufferExt masterKey;
	/** Master Salt (14 bytes) */
	private @NonNull BufferExt masterSalt;
	/** Auth Key length */
	private final int authKeyLen;
	/** Auth Tag length */
	private final int authTagLen;
	/** Master Key Identifier */
	private @NonNull DynInteger mki;
	/** SSRC ID */
	private final int ssrcId;
	/**
	 * Key Derivation Rate for Session Keys (in packets).<br />
	 * 0^=derive once from master key/salt; >0^=derive every N packets.<br />
	 * The recommended value is 2^20 (= 1048576).<br />
	 * Note that the tested RTSP clients (VLC, FFplay/Lavf, OpenRTSP) do not support KDR.
	 */
	private @NonNull DynInteger kdr;

	/**
	 * Constructor.
	 * @param encrKeyLen Encryption Key length
	 * @param masterKey Master Key
	 * @param masterSalt Master Salt
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param mki Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 */
	public SrtxpKmd(
				int encrKeyLen,
				@NonNull BufferExt masterKey,
				@NonNull BufferExt masterSalt,
				int authKeyLen,
				int authTagLen,
				@NonNull DynInteger mki,
				int ssrcId,
				@NonNull DynInteger kdr
			) {
		this.encrKeyLen = encrKeyLen;
		this.masterKey = masterKey.clone();
		this.masterSalt = masterSalt.clone();
		this.authKeyLen = authKeyLen;
		this.authTagLen = authTagLen;
		this.mki = mki.clone();
		this.ssrcId = ssrcId;
		this.kdr = (kdr.isEmpty() || kdr.value() == 0L ? DynInteger.createEmpty() : kdr.clone());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new KMD object with default key sizes and random key/salt
	 * @param mkiValue Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithDefaults(long mkiValue, int ssrcId) {
		return createWithDefaults(
				mkiValue,
				ssrcId,
				DynInteger.createWithAutoSize(DEFAULT_KDR_PACKETS)
			);
	}

	/**
	 * Create a new KMD object with default key sizes and random key/salt
	 * @param mkiValue Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithDefaults(long mkiValue, int ssrcId, @NonNull DynInteger kdr) {
		return createWithCustomKeySizes(
				DEFAULT_ENCR_KEY_LEN,
				DEFAULT_AUTH_KEY_LEN,
				DEFAULT_AUTH_TAG_LEN,
				new DynInteger(mkiValue, DEFAULT_MKI_LEN),
				ssrcId,
				kdr
			);
	}

	/**
	 * Create a new KMD object with default key sizes and random key/salt for usage with the legacy SDES key management.<br />
	 * This is required for compatibility with older RTSP clients like FFplay using Lavf61.7.100.<br />
	 * The difference to a regular KMD is that no MKI will be used and KDR is set to zero.
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createForLegacySdes(int ssrcId) {
		return createWithCustomKeySizes(
				DEFAULT_ENCR_KEY_LEN,
				DEFAULT_AUTH_KEY_LEN,
				DEFAULT_AUTH_TAG_LEN,
				DynInteger.createEmpty(),
				ssrcId,
				DynInteger.createEmpty()
			);
	}

	/**
	 * Create a new KMD object with custom key sizes and random key/salt
	 * @param encrKeyLen Encryption Key length
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param mki Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithCustomKeySizes(
				int encrKeyLen,
				int authKeyLen,
				int authTagLen,
				@NonNull DynInteger mki,
				int ssrcId
			) {
		return createWithCustomKeySizes(
				encrKeyLen,
				authKeyLen,
				authTagLen,
				mki,
				ssrcId,
				DynInteger.createWithAutoSize(DEFAULT_KDR_PACKETS)
			);
	}

	/**
	 * Create a new KMD object with custom key sizes and random key/salt
	 * @param encrKeyLen Encryption Key length
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param mki Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithCustomKeySizes(
				int encrKeyLen,
				int authKeyLen,
				int authTagLen,
				@NonNull DynInteger mki,
				int ssrcId,
				@NonNull DynInteger kdr
			) {
		SrtxpKmd resObj = new SrtxpKmd(
				encrKeyLen,
				new BufferExt(),
				new BufferExt(),
				authKeyLen,
				authTagLen,
				mki,
				ssrcId,
				kdr
			);
		RandomHelper.getSecureRandomBytes(resObj.encrKeyLen, resObj.masterKey);
		/*for (int i = 0; i < resObj.encrKeyLen; i++) {
			resObj.masterKey.set(i, (byte)0x01);  // only for debugging
		}*/
		RandomHelper.getSecureRandomBytes(KeySizes.SALT_SIZE, resObj.masterSalt);
		/*for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			resObj.masterSalt.set(i, (byte)0x02);  // only for debugging
		}*/
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull SrtxpKmd clone() {
		try {
			SrtxpKmd clone = (SrtxpKmd) super.clone();
			clone.masterKey = this.masterKey.clone();
			clone.masterSalt = this.masterSalt.clone();
			clone.mki = this.mki.clone();
			clone.kdr = this.kdr.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public int encrKeyLen() {
		return encrKeyLen;
	}

	public @NonNull BufferExt masterKey() {
		return masterKey.clone();
	}

	public @NonNull BufferExt masterSalt() {
		return masterSalt.clone();
	}

	public @NonNull String getMasterKeyAndSaltAsBase64() {
		BufferExt tmpKmdMkMsBe = new BufferExt();
		tmpKmdMkMsBe.append(masterKey);
		tmpKmdMkMsBe.append(masterSalt);

		return tmpKmdMkMsBe.toBase64String();
	}

	public int authKeyLen() {
		return authKeyLen;
	}

	public int authTagLen() {
		return authTagLen;
	}

	public @NonNull DynInteger mki() {
		return mki.clone();
	}

	public int ssrcId() {
		return ssrcId;
	}

	public @NonNull DynInteger kdr() {
		return kdr.clone();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (SrtxpKmd) obj;
		return (this.encrKeyLen == that.encrKeyLen &&
				Objects.equals(this.masterKey, that.masterKey) &&
				Objects.equals(this.masterSalt, that.masterSalt) &&
				this.authKeyLen == that.authKeyLen &&
				this.authTagLen == that.authTagLen &&
				Objects.equals(this.mki, that.mki) &&
				this.ssrcId == that.ssrcId &&
				Objects.equals(this.kdr, that.kdr));
	}

	@Override
	public int hashCode() {
		return Objects.hash(encrKeyLen, masterKey, masterSalt, authKeyLen, authTagLen, mki, ssrcId, kdr);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"encrKeyLen=" + encrKeyLen +
				", masterKey=" + masterKey.toHexString(true) +
				", masterSalt=" + masterSalt.toHexString(true) +
				", authKeyLen=" + authKeyLen +
				", authTagLen=" + authTagLen +
				", mki=" + mki +
				", ssrcId=" + String.format("0x%08X", ssrcId) +
				", kdr=" + kdr +
				"]";
	}

}

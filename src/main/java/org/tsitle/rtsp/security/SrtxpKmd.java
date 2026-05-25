package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.constants.KeySizes;

import java.nio.ByteOrder;
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
	private @NonNull BufferExt mki;
	/** SSRC ID */
	private final int ssrcId;
	/**
	 * Key Derivation Rate (in packets).<br />
	 * 0^=derive once from master key/salt; >0^=derive new session keys every N packets.<br />
	 * Note that the tested RTSP clients (VLC, FFplay, OpenRTSP) do not support KDR.
	 */
	private final long kdr;

	/**
	 * Constructor.
	 * @param encrKeyLen Encryption Key length
	 * @param masterKey Master Key
	 * @param masterSalt Master Salt
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param mki Master Key Identifier (can be empty)
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 */
	public SrtxpKmd(
				int encrKeyLen,
				@NonNull BufferExt masterKey,
				@NonNull BufferExt masterSalt,
				int authKeyLen,
				int authTagLen,
				@NonNull BufferExt mki,
				int ssrcId,
				long kdr
			) {
		this.encrKeyLen = encrKeyLen;
		this.masterKey = masterKey.clone();
		this.masterSalt = masterSalt.clone();
		this.authKeyLen = authKeyLen;
		this.authTagLen = authTagLen;
		this.mki = mki.clone();
		this.ssrcId = ssrcId;
		this.kdr = kdr;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new KMD object with default key sizes and random values
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithDefaults(int ssrcId) {
		return createWithDefaults(ssrcId, DEFAULT_KDR_PACKETS);
	}

	/**
	 * Create a new KMD object with default key sizes and random values
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithDefaults(int ssrcId, long kdr) {
		return createWithCustomKeySizes(
				DEFAULT_ENCR_KEY_LEN,
				DEFAULT_AUTH_KEY_LEN,
				DEFAULT_AUTH_TAG_LEN,
				DEFAULT_MKI_LEN,
				ssrcId,
				kdr
			);
	}

	/**
	 * Create a new KMD object with default key sizes and random values for usage with the legacy SDES key management.<br />
	 * This is required for compatibility with older RTSP clients like FFplay using Lavf61.7.100.
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createForLegacySdes(int ssrcId) {
		return createForLegacySdes(ssrcId, DEFAULT_KDR_PACKETS);
	}

	/**
	 * Create a new KMD object with default key sizes and random values for usage with the legacy SDES key management.<br />
	 * This is required for compatibility with older RTSP clients like FFplay using Lavf61.7.100.
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 * @return New KMD object
	 */
	public static SrtxpKmd createForLegacySdes(int ssrcId, long kdr) {
		return createWithCustomKeySizes(
				DEFAULT_ENCR_KEY_LEN,
				DEFAULT_AUTH_KEY_LEN,
				DEFAULT_AUTH_TAG_LEN,
				0,
				ssrcId,
				kdr
			);
	}

	/**
	 * Create a new KMD object with custom key sizes and random values
	 * @param encrKeyLen Encryption Key length
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param mkiLen Master Key Identifier length (can be zero)
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithCustomKeySizes(
				int encrKeyLen,
				int authKeyLen,
				int authTagLen,
				int mkiLen,
				int ssrcId
			) {
		return createWithCustomKeySizes(
				encrKeyLen,
				authKeyLen,
				authTagLen,
				mkiLen,
				ssrcId,
				DEFAULT_KDR_PACKETS
			);
	}

	/**
	 * Create a new KMD object with custom key sizes and random values
	 * @param encrKeyLen Encryption Key length
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param mkiLen Master Key Identifier length (can be zero)
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithCustomKeySizes(
				int encrKeyLen,
				int authKeyLen,
				int authTagLen,
				int mkiLen,
				int ssrcId,
				long kdr
			) {
		SrtxpKmd resObj = new SrtxpKmd(
				encrKeyLen,
				new BufferExt(),
				new BufferExt(),
				authKeyLen,
				authTagLen,
				new BufferExt(),
				ssrcId,
				kdr
			);
		RandomHelper.getSecureRandomBytes(resObj.encrKeyLen, resObj.masterKey);
		RandomHelper.getSecureRandomBytes(KeySizes.SALT_SIZE, resObj.masterSalt);
		if (mkiLen > 0) {
			RandomHelper.getSecureRandomBytes(mkiLen, resObj.mki);
		}
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

	public int authKeyLen() {
		return authKeyLen;
	}

	public int authTagLen() {
		return authTagLen;
	}

	public @NonNull BufferExt mki() {
		return mki.clone();
	}

	@SuppressWarnings("unused")
	public long mkiAsLong() {
		long resI = 0;
		if (mkiLen() > 0 && mkiLen() <= Long.BYTES) {
			byte[] tmpBa = new byte[mkiLen()];
			mki.copyInto(0, tmpBa, 0, mkiLen());
			resI = bytesToLongNative(tmpBa);
		}
		return resI;
	}

	public int mkiLen() {
		return mki.getUsed();
	}

	public int ssrcId() {
		return ssrcId;
	}

	public long kdr() {
		return kdr;
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
				this.kdr == that.kdr);
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
				", mki=" + (mki.isEmpty() ? "empty" : mki.toHexString(true)) + " (len=" + mki.getUsed() + ")" +
				", ssrcId=" + String.format("0x%08X", ssrcId) +
				", kdr=" + kdr +
				"]";
	}

	private static long bytesToLongNative(byte[] src) {
		if (src == null) {
			throw new IllegalArgumentException("src must not be null");
		}
		if (src.length < 1 || src.length > Long.BYTES) {
			throw new IllegalArgumentException("src length must be in range 1..8");
		}

		long value = 0L;
		if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
			for (int i = 0; i < src.length; i++) {
				value |= ((long)src[i] & 0xFFL) << (i * 8);
			}
		} else {
			for (byte b : src) {
				value = (value << 8) | ((long)b & 0xFFL);
			}
		}
		return value;
	}

}

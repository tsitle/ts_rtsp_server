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

	public static final int DEFAULT_ENCR_KEY_LEN = KeySizes.AES_KEY_SIZE_128;
	public static final int DEFAULT_AUTH_KEY_LEN = KeySizes.AUTH_KEY_SIZE_160;
	public static final int DEFAULT_AUTH_TAG_LEN = 10;
	public static final int DEFAULT_SALT_LEN = KeySizes.SALT_SIZE;

	/** Encryption Key length */
	private int encrKeyLen;
	/** Master AES-128 key (16 bytes) */
	private @NonNull BufferExt masterKey;
	/** Master Salt (14 bytes) */
	private @NonNull BufferExt masterSalt;
	/** Auth Key length */
	private int authKeyLen;
	/** Auth Tag length */
	private int authTagLen;
	/** Master Key Identifier */
	private @NonNull BufferExt mki;
	/** SSRC ID */
	private int ssrcId;

	/**
	 * Constructor - initializes all fields to empty values.
	 */
	public SrtxpKmd() {
		this(
				0,
				new BufferExt(),
				new BufferExt(),
				0,
				0,
				new BufferExt(),
				0
			);
	}

	/**
	 * Constructor.
	 * @param encrKeyLen Encryption Key length
	 * @param masterKey Master Key
	 * @param masterSalt Master Salt
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param mki Master Key Identifier (can be empty)
	 * @param ssrcId SSRC ID
	 */
	public SrtxpKmd(
				int encrKeyLen,
				@NonNull BufferExt masterKey,
				@NonNull BufferExt masterSalt,
				int authKeyLen,
				int authTagLen,
				@NonNull BufferExt mki,
				int ssrcId
			) {
		this.encrKeyLen = encrKeyLen;
		this.masterKey = masterKey.clone();
		this.masterSalt = masterSalt.clone();
		this.authKeyLen = authKeyLen;
		this.authTagLen = authTagLen;
		this.mki = mki.clone();
		this.ssrcId = ssrcId;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new KMD object with default key sizes and random values
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithDefaults(int ssrcId) {
		return createWithCustomKeySizes(
				DEFAULT_ENCR_KEY_LEN,
				DEFAULT_AUTH_KEY_LEN,
				DEFAULT_AUTH_TAG_LEN,
				ssrcId
			);
	}

	/**
	 * Create a new KMD object with custom key sizes and random values
	 * @param encrKeyLen Encryption Key length
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createWithCustomKeySizes(
				int encrKeyLen,
				int authKeyLen,
				int authTagLen,
				int ssrcId
			) {
		SrtxpKmd resObj = new SrtxpKmd();
		resObj.encrKeyLen = encrKeyLen;
		RandomHelper.getSecureRandomBytes(resObj.encrKeyLen, resObj.masterKey);
		resObj.authKeyLen = authKeyLen;
		resObj.authTagLen = authTagLen;
		RandomHelper.getSecureRandomBytes(KeySizes.SALT_SIZE, resObj.masterSalt);
		RandomHelper.getSecureRandomBytes(4, resObj.mki);
		resObj.ssrcId = ssrcId;
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

	public int ssrcId() {
		return ssrcId;
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
				this.ssrcId == that.ssrcId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(encrKeyLen, masterKey, masterSalt, authKeyLen, authTagLen, mki, ssrcId);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"encrKeyLen=" + encrKeyLen +
				", masterKey=0x" + masterKey.toHexString() +
				", masterSalt=0x" + masterSalt.toHexString() +
				", authKeyLen=" + authKeyLen +
				", authTagLen=" + authTagLen +
				", mki=" + (mki.isEmpty() ? "empty" : "0x" + mki.toHexString()) +
				", ssrcId=" + String.format("0x%08X", ssrcId) +
				"]";
	}

}

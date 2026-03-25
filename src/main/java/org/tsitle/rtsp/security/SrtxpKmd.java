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

	public static final int DEFAULT_AUTH_KEY_LEN = KeySizes.AUTH_KEY_SIZE_160;

	/** Master AES-128 key (16 bytes) */
	private @NonNull BufferExt masterKey;
	/** Master Salt (14 bytes) */
	private @NonNull BufferExt masterSalt;
	/** Auth Key length */
	private int authKeyLen;
	/** Master Key Identifier */
	private @NonNull BufferExt mki;
	/** SSRC ID */
	private int ssrcId;

	public SrtxpKmd() {
		this(
				new BufferExt(),
				new BufferExt(),
				0,
				new BufferExt(),
				0
			);
	}

	/**
	 * Constructor.
	 * @param masterKey Master key
	 * @param masterSalt Master salt
	 * @param authKeyLen Authentication key length
	 * @param mki Master Key Identifier (can be empty)
	 * @param ssrcId SSRC ID
	 */
	public SrtxpKmd(
				@NonNull BufferExt masterKey,
				@NonNull BufferExt masterSalt,
				int authKeyLen,
				@NonNull BufferExt mki,
				int ssrcId
			) {
		this.masterKey = masterKey.clone();
		this.masterSalt = masterSalt.clone();
		this.authKeyLen = authKeyLen;
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
		SrtxpKmd resObj = new SrtxpKmd();
		RandomHelper.getSecureRandomBytes(KeySizes.AES_128_KEY_SIZE, resObj.masterKey);
		resObj.authKeyLen = DEFAULT_AUTH_KEY_LEN;
		RandomHelper.getSecureRandomBytes(KeySizes.SALT_SIZE, resObj.masterSalt);
		RandomHelper.getSecureRandomBytes(4, resObj.mki);
		resObj.ssrcId = ssrcId;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public SrtxpKmd clone() {
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

	public @NonNull BufferExt masterKey() {
		return masterKey;
	}

	public @NonNull BufferExt masterSalt() {
		return masterSalt;
	}

	public int authKeyLen() {
		return authKeyLen;
	}

	public @NonNull BufferExt mki() {
		return mki;
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
		return (Objects.equals(this.masterKey, that.masterKey) &&
				Objects.equals(this.masterSalt, that.masterSalt) &&
				this.authKeyLen == that.authKeyLen &&
				Objects.equals(this.mki, that.mki) &&
				this.ssrcId == that.ssrcId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(masterKey, masterSalt, authKeyLen, mki, ssrcId);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"masterKey=0x" + masterKey.toHexString() +
				", masterSalt=0x" + masterSalt.toHexString() +
				", authKeyLen=" + authKeyLen +
				", mki=" + (mki.isEmpty() ? "empty" : "0x" + mki.toHexString()) +
				", ssrcId=" + String.format("0x%08X", ssrcId) +
				"]";
	}

}

package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;

import java.util.Objects;

/**
 * SRTxP Key Management Data
 */
public final class SrtxpKmd implements Cloneable {

	/** Master AES-128 key (16 bytes) */
	private @NonNull BufferExt masterKey;
	/** Master Salt (14 bytes) */
	private @NonNull BufferExt masterSalt;
	/** Auth Key length */
	private final int authKeyLen;
	/** Master Key Identifier */
	private @NonNull BufferExt mki;

	public SrtxpKmd() {
		this(
				new BufferExt(),
				new BufferExt(),
				0,
				new BufferExt()
			);
	}

	/**
	 * Constructor.
	 * @param masterKey Master key
	 * @param masterSalt Master salt
	 * @param authKeyLen Authentication key length
	 * @param mki Master Key Identifier (can be empty)
	 */
	public SrtxpKmd(
				@NonNull BufferExt masterKey,
				@NonNull BufferExt masterSalt,
				int authKeyLen,
				@NonNull BufferExt mki
			) {
		this.masterKey = masterKey.clone();
		this.masterSalt = masterSalt.clone();
		this.authKeyLen = authKeyLen;
		this.mki = mki.clone();
	}

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

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (SrtxpKmd) obj;
		return Objects.equals(this.masterKey, that.masterKey) &&
				Objects.equals(this.masterSalt, that.masterSalt) &&
				this.authKeyLen == that.authKeyLen &&
				Objects.equals(this.mki, that.mki);
	}

	@Override
	public int hashCode() {
		return Objects.hash(masterKey, masterSalt, authKeyLen, mki);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"masterKey=0x" + masterKey.toHexString() +
				", masterSalt=0x" + masterSalt.toHexString() +
				", authKeyLen=" + authKeyLen +
				", mki=" + (mki.isEmpty() ? "empty" : "0x" + mki.toHexString()) +
				"]";
	}

}

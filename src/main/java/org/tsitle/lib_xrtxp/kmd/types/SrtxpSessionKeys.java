package org.tsitle.lib_xrtxp.kmd.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

import java.util.Objects;

/**
 * Session keys for RTP/SRTP and RTCP/SRTCP
 */
public final class SrtxpSessionKeys implements Cloneable {

	private @NonNull BufferExt encKey = new BufferExt();
	private @NonNull BufferExt authKey = new BufferExt();
	private @NonNull BufferExt salt = new BufferExt();

	/**
	 * Constructor.
	 * @param encKey AES-128 key (16 bytes)
	 * @param authKey HMAC-SHA1 key (10 or 20 bytes)
	 * @param salt Salt (14 bytes)
	 */
	public SrtxpSessionKeys(@NonNull BufferExt encKey, @NonNull BufferExt authKey, @NonNull BufferExt salt) {
		this.encKey.copyOf(encKey);
		this.authKey.copyOf(authKey);
		this.salt.copyOf(salt);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull SrtxpSessionKeys clone() {
		try {
			SrtxpSessionKeys clone = (SrtxpSessionKeys)super.clone();
			clone.encKey = this.encKey.clone();
			clone.authKey = this.authKey.clone();
			clone.salt = this.salt.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public @NonNull BufferExt encKey() {
		BufferExt resObj = new BufferExt();
		resObj.copyOf(encKey);
		return resObj;
	}

	public @NonNull BufferExt authKey() {
		BufferExt resObj = new BufferExt();
		resObj.copyOf(authKey);
		return resObj;
	}

	public @NonNull BufferExt salt() {
		BufferExt resObj = new BufferExt();
		resObj.copyOf(salt);
		return resObj;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (SrtxpSessionKeys)obj;
		return (
				Objects.equals(this.encKey, that.encKey) &&
				Objects.equals(this.authKey, that.authKey) &&
				Objects.equals(this.salt, that.salt)
			);
	}

	@Override
	public int hashCode() {
		return Objects.hash(encKey, authKey, salt);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"encKey=0x" + encKey.toHexString() + ", " +
				"authKey=0x" + authKey.toHexString() + ", " +
				"salt=0x" + salt.toHexString() +
				"]";
	}

}

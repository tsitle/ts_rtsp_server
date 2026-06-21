package org.tsitle.lib_xrtxp.kmd.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.helpers.RandomHelper;
import org.tsitle.lib_xrtxp.kmd.constants.KeySizes;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import java.util.Objects;
import java.util.Optional;

/**
 * SRTxP Key Management Data
 */
public final class SrtxpKmd implements Cloneable {

	public static final int DEFAULT_ENCR_KEY_LEN = KeySizes.AES_KEY_SIZE_128;  // VLC requires 128-bit encr key
	public static final int DEFAULT_AUTH_KEY_LEN = KeySizes.AUTH_KEY_SIZE_160;  // VLC requires 160-bit auth key
	public static final int DEFAULT_AUTH_TAG_LEN = 10;  // VLC requires 10-byte auth tag
	public static final int DEFAULT_MKI_LEN = 4;  // VLC requires 4-byte MKI
	public static final int DEFAULT_KDR_LEN = 4;  // KDR cannot exceed 4 bytes since it is limited to a 9-digit value
	public static final long DEFAULT_KDR_PACKETS = 0;  // VLC requires a KDR of 0

	public static final long MAX_KDR_PACKETS = 2147483648L;  // ^= 2^31
	public static final int MAX_KDR_EXPONENT = 31;  // ^= 2^x

	public static final int MAX_TAG_VALUE = 999_999_999;  // max. 9 digits per RFC

	/** Whether this KMD is for legacy SDES (RFC-4568) or for RTSP (RFC-2326/RFC-7826) */
	private final boolean metaIsForLegacySdes;
	/** Only for legacy SDES: Tag value (similar to MKI) */
	private final int metaTagValueForLegacySdes;
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
	private @NonNull SrtxpMki mki;
	/** SSRC ID */
	private @NonNull RtspProtoIdXsrc ssrcId;
	/**
	 * Key Derivation Rate for Session Keys (in packets).<br />
	 * 0^=derive once from master key/salt; >0^=derive every N packets.<br />
	 * The recommended value is 2^20 (= 1048576).<br />
	 * Note that the tested RTSP clients (VLC, FFplay/Lavf, OpenRTSP) do not support KDR.
	 */
	private @NonNull SrtxpKdr kdr;

	/**
	 * Constructor.
	 * @param metaIsForLegacySdes Whether this KMD is for legacy SDES (RFC-4568) or for RTSP (RFC-2326/RFC-7826)
	 * @param metaTagValueForLegacySdes Only for legacy SDES: Tag value (similar to MKI but only relevant in SDP)
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
				boolean metaIsForLegacySdes,
				int metaTagValueForLegacySdes,
				int encrKeyLen,
				@NonNull BufferExt masterKey,
				@NonNull BufferExt masterSalt,
				int authKeyLen,
				int authTagLen,
				@NonNull SrtxpMki mki,
				@NonNull RtspProtoIdXsrc ssrcId,
				@NonNull SrtxpKdr kdr
			) {
		if (metaTagValueForLegacySdes > SrtxpKmd.MAX_TAG_VALUE) {
			throw new IllegalArgumentException("Tag value exceeds maximum allowed value (is=" +
					Integer.toUnsignedString(metaTagValueForLegacySdes) + ", max=" + Integer.toUnsignedString(SrtxpKmd.MAX_TAG_VALUE) + ")");
		}
		if (! kdr.isEmpty() && kdr.getValue().orElseThrow() > SrtxpKmd.MAX_KDR_PACKETS) {
			throw new IllegalArgumentException("KDR value exceeds maximum allowed value (is=" +
					Long.toUnsignedString(kdr.getValue().orElseThrow()) + ", max=" +
					Long.toUnsignedString(SrtxpKmd.MAX_KDR_PACKETS) + ")");
		}

		this.metaIsForLegacySdes = metaIsForLegacySdes;
		this.metaTagValueForLegacySdes = metaTagValueForLegacySdes;
		this.encrKeyLen = encrKeyLen;
		this.masterKey = masterKey.clone();
		this.masterSalt = masterSalt.clone();
		this.authKeyLen = authKeyLen;
		this.authTagLen = authTagLen;
		this.mki = mki.clone();
		this.ssrcId = ssrcId.clone();
		this.ssrcId.writeProtect();
		this.kdr = (kdr.isEmpty() || kdr.getValue().orElseThrow() == 0L ? SrtxpKdr.ofEmpty() : kdr.clone());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new KMD object with default key sizes and random key/salt
	 * @param mkiValue Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createForMikeyWithDefaults(@NonNull SrtxpMki mkiValue, @NonNull RtspProtoIdXsrc ssrcId) {
		return createForMikeyWithDefaults(
				mkiValue,
				ssrcId,
				SrtxpKdr.of(DEFAULT_KDR_PACKETS, DEFAULT_KDR_LEN)
			);
	}

	/**
	 * Create a new KMD object with default key sizes and random key/salt
	 * @param mkiValue Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 * @return New KMD object
	 */
	public static SrtxpKmd createForMikeyWithDefaults(
				@NonNull SrtxpMki mkiValue,
				@NonNull RtspProtoIdXsrc ssrcId,
				@NonNull SrtxpKdr kdr
			) {
		return createForMikeyWithCustomKeySizes(
				DEFAULT_ENCR_KEY_LEN,
				DEFAULT_AUTH_KEY_LEN,
				DEFAULT_AUTH_TAG_LEN,
				mkiValue,
				ssrcId,
				kdr
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
	public static SrtxpKmd createForMikeyWithCustomKeySizes(
				int encrKeyLen,
				int authKeyLen,
				int authTagLen,
				@NonNull SrtxpMki mki,
				@NonNull RtspProtoIdXsrc ssrcId
			) {
		return createForMikeyWithCustomKeySizes(
				encrKeyLen,
				authKeyLen,
				authTagLen,
				mki,
				ssrcId,
				SrtxpKdr.of(DEFAULT_KDR_PACKETS, DEFAULT_KDR_LEN)
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
	public static SrtxpKmd createForMikeyWithCustomKeySizes(
				int encrKeyLen,
				int authKeyLen,
				int authTagLen,
				@NonNull SrtxpMki mki,
				@NonNull RtspProtoIdXsrc ssrcId,
				@NonNull SrtxpKdr kdr
			) {
		return createXxxWithCustomKeySizes(
				false,
				-1,
				encrKeyLen,
				authKeyLen,
				authTagLen,
				mki,
				ssrcId,
				kdr
			);
	}

	/**
	 * Create a new KMD object with default key sizes and random key/salt for usage with the legacy SDES key management.<br />
	 * This is required for compatibility with older RTSP clients like FFplay using Lavf61.7.100.<br />
	 * The difference to a regular KMD is that no MKI will be used and KDR is set to zero.
	 * @param metaTagValue Only for legacy SDES: Tag value (similar to MKI but only relevant in SDP)
	 * @param ssrcId SSRC ID
	 * @return New KMD object
	 */
	public static SrtxpKmd createForLegacySdesWithDefaults(int metaTagValue, @NonNull RtspProtoIdXsrc ssrcId) {
		return createXxxWithCustomKeySizes(
				true,
				metaTagValue,
				DEFAULT_ENCR_KEY_LEN,
				DEFAULT_AUTH_KEY_LEN,
				DEFAULT_AUTH_TAG_LEN,
				SrtxpMki.ofEmpty(),
				ssrcId,
				SrtxpKdr.ofEmpty()
			);
	}

	/**
	 * Create a new KMD object with default key sizes and random key/salt for usage with the legacy SDES key management.<br />
	 * This is required for compatibility with older RTSP clients like FFplay using Lavf61.7.100.<br />
	 * The difference to a regular KMD is that no MKI will be used and KDR is set to zero.
	 * @param metaTagValue Only for legacy SDES: Tag value (similar to MKI but only relevant in SDP)
	 * @param mki Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 * @return New KMD object
	 */
	public static SrtxpKmd createForLegacySdesWithDefaults(
				int metaTagValue,
				@NonNull SrtxpMki mki,
				@NonNull RtspProtoIdXsrc ssrcId,
				@NonNull SrtxpKdr kdr
			) {
		return createXxxWithCustomKeySizes(
				true,
				metaTagValue,
				DEFAULT_ENCR_KEY_LEN,
				DEFAULT_AUTH_KEY_LEN,
				DEFAULT_AUTH_TAG_LEN,
				mki,
				ssrcId,
				kdr
			);
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
			clone.ssrcId = this.ssrcId.clone();
			clone.kdr = this.kdr.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	public boolean getMetaIsForLegacySdes() {
		return metaIsForLegacySdes;
	}

	public Optional<Integer> getMetaTagForLegacySdes() {
		if (metaTagValueForLegacySdes < 0) {
			return Optional.empty();
		}
		return Optional.of(metaTagValueForLegacySdes);
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

	public @NonNull SrtxpMki mki() {
		return mki.clone();
	}

	public @NonNull RtspProtoIdXsrc ssrcId() {
		return ssrcId.clone();
	}

	public @NonNull SrtxpKdr kdr() {
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
		return (this.metaIsForLegacySdes == that.metaIsForLegacySdes &&
				this.metaTagValueForLegacySdes == that.metaTagValueForLegacySdes &&
				this.encrKeyLen == that.encrKeyLen &&
				Objects.equals(this.masterKey, that.masterKey) &&
				Objects.equals(this.masterSalt, that.masterSalt) &&
				this.authKeyLen == that.authKeyLen &&
				this.authTagLen == that.authTagLen &&
				Objects.equals(this.mki, that.mki) &&
				Objects.equals(this.ssrcId, that.ssrcId) &&
				Objects.equals(this.kdr, that.kdr));
	}

	@Override
	public int hashCode() {
		return Objects.hash(metaIsForLegacySdes, metaTagValueForLegacySdes, encrKeyLen,
				masterKey, masterSalt, authKeyLen, authTagLen, mki, ssrcId, kdr);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"metaIsForLegacySdes=" + (metaIsForLegacySdes ? "T" : "F") +
				", metaTagValue=" + (metaTagValueForLegacySdes >= 0 ? Integer.toUnsignedString(metaTagValueForLegacySdes) : "unset") +
				", encrKeyLen=" + encrKeyLen +
				", masterKey=" + masterKey.toHexString(true) +
				", masterSalt=" + masterSalt.toHexString(true) +
				", authKeyLen=" + authKeyLen +
				", authTagLen=" + authTagLen +
				", mki=" + mki +
				", ssrcId=" + ssrcId +
				", kdr=" + kdr +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new KMD object with custom key sizes and random key/salt
	 * @param metaIsForLegacySdes Whether this KMD is for legacy SDES (RFC-4568) or for RTSP (RFC-2326/RFC-7826)
	 * @param metaTagValue Only for legacy SDES: Tag value (similar to MKI but only relevant in SDP)
	 * @param encrKeyLen Encryption Key length
	 * @param authKeyLen Authentication Key length
	 * @param authTagLen Authentication Tag length
	 * @param mki Master Key Identifier
	 * @param ssrcId SSRC ID
	 * @param kdr Key Derivation Rate
	 * @return New KMD object
	 */
	private static SrtxpKmd createXxxWithCustomKeySizes(
				boolean metaIsForLegacySdes,
				int metaTagValue,
				int encrKeyLen,
				int authKeyLen,
				int authTagLen,
				@NonNull SrtxpMki mki,
				@NonNull RtspProtoIdXsrc ssrcId,
				@NonNull SrtxpKdr kdr
			) {
		SrtxpKmd resObj = new SrtxpKmd(
				metaIsForLegacySdes,
				metaTagValue,
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

}

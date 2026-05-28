package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Base class for Contexts for SRTP/RTP and SRTCP/RTCP encryption/decryption
 */
public abstract class SrtxpContextBase {

	protected static class CtxCipherAndMac {
		Cipher cipherObj = null;
		SecretKeySpec sksCipherObj = null;
		Mac macObj = null;
		SecretKeySpec sksMacObj = null;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Key Management Data */
	protected @NonNull SrtxpKmd ctxKmd;

	/** Session keys for RTP/SRTP */
	protected @Nullable SessionKeys ctxSessionKeysRtp = null;
	/** Session keys for RTCP/SRTCP */
	protected @Nullable SessionKeys ctxSessionKeysRtcp = null;
	/** Session keys re-derivation marker for RTP/SRTP */
	private long ctxSessionKeysRederivationRtp = -1L;
	/** Session keys re-derivation marker for RTCP/SRTCP */
	private long ctxSessionKeysRederivationRtcp = -1L;

	/** Cipher/CipherSks/Mac/MacSks objects */
	protected @NonNull CtxCipherAndMac ctxCam = new CtxCipherAndMac();

	protected final BufferExt cacheIvBuf = new BufferExt();
	protected final BufferExt cacheAuthTagBuf = new BufferExt();
	private final BufferExt cacheValidateMkiBuf = new BufferExt();
	private final BufferExt cacheAuthTagRcvdBuf = new BufferExt();
	private final BufferExt cacheAuthTagActualBuf = new BufferExt();

	/**
	 * Constructor.
	 * @param isRtp Is this a context for RTP?
	 * @param kmd Key Management Data
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	protected SrtxpContextBase(boolean isRtp, @NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		ctxKmd = kmd.clone();

		//
		if (kmd.encrKeyLen() != KeySizes.AES_KEY_SIZE_128 && kmd.encrKeyLen() != KeySizes.AES_KEY_SIZE_256) {
			throw new SrtxpSecurityException("Encryption Key length must be 16 or 32 bytes");
		}
		if (kmd.authKeyLen() != KeySizes.AUTH_KEY_SIZE_080 && kmd.authKeyLen() != KeySizes.AUTH_KEY_SIZE_160) {
			throw new SrtxpSecurityException("Auth Key length must be 10 or 20 bytes");
		}
		if (kmd.authTagLen() < 1) {
			throw new SrtxpSecurityException("Auth Tag length must be > 0");
		}
		if (kmd.authTagLen() > KeySizes.SHA1_SIZE_160) {  // Auth Tag cannot be longer than what SHA1-160 can output
			throw new SrtxpSecurityException("Auth Tag length must be <= " + KeySizes.SHA1_SIZE_160 + " bytes");
		}

		//
		sessionKeysRederivation(isRtp, 0L);

		//
		buildCamObject(ctxCam, isRtp ? ctxSessionKeysRtp : ctxSessionKeysRtcp);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the SSRC from the Key Management Data
	 * @return SSRC
	 */
	public int getSsrcId() {
		return ctxKmd.ssrcId();
	}

	/**
	 * Get the MKI from the Key Management Data
	 * @return MKI value
	 */
	@SuppressWarnings("unused")
	public Optional<Long> getMkiValue() {
		if (ctxKmd.mki().isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(ctxKmd.mki().value());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void sessionKeysRederivation(boolean isRtp, long packetIndex) throws SrtxpSecurityException {
		long markerLast = (isRtp ? ctxSessionKeysRederivationRtp : ctxSessionKeysRederivationRtcp);
		long markerNew;
		SessionKeys tmpSessionKeys;
		if (markerLast == -1L) {
			final Cipher cipherAesCtr = buildCipherObject();
			if (isRtp) {
				tmpSessionKeys = SrtxpKeyDerivation.deriveForRtp(cipherAesCtr, ctxKmd, 0L);
			} else {
				tmpSessionKeys = SrtxpKeyDerivation.deriveForRtcp(cipherAesCtr, ctxKmd, 0L);
			}
			markerNew = 0L;
		} else if (! ctxKmd.kdr().isEmpty() && ctxKmd.kdr().value() > 0L) {
			markerNew = packetIndex / ctxKmd.kdr().value();
			if (markerNew == markerLast) {
				return;
			}
			final Cipher cipherAesCtr = buildCipherObject();
			if (isRtp) {
				tmpSessionKeys = SrtxpKeyDerivation.deriveForRtp(cipherAesCtr, ctxKmd, packetIndex);
			} else {
				tmpSessionKeys = SrtxpKeyDerivation.deriveForRtcp(cipherAesCtr, ctxKmd, packetIndex);
			}
		} else {
			return;
		}
		//
		if (isRtp) {
			setRtpSessionKeys(tmpSessionKeys);
			ctxSessionKeysRederivationRtp = markerNew;
		} else {
			setRtcpSessionKeys(tmpSessionKeys);
			ctxSessionKeysRederivationRtcp = markerNew;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void encryptPayload(
				@NonNull BufferExt plainPacket,
				int pktHeaderSize,
				@NonNull BufferExt curIvBuf,
				@NonNull BufferExt outputEncrPacket
			) throws SrtxpSecurityException {
		outputEncrPacket.copyOf(plainPacket, 0, pktHeaderSize);
		outputEncrPacket.increaseSize(plainPacket.getUsed() + 64);  // reserve some extra memory for the AuthTag etc.

		try {
			ctxCam.cipherObj.init(
					Cipher.ENCRYPT_MODE,
					ctxCam.sksCipherObj,
					new IvParameterSpec(curIvBuf.getBaPtr(), 0, curIvBuf.getUsed())
				);

			ctxCam.cipherObj.doFinal(
					plainPacket.getBaPtr(),
					pktHeaderSize,
					plainPacket.getUsed() - pktHeaderSize,
					outputEncrPacket.getBaPtr(),
					pktHeaderSize
				);
			outputEncrPacket.setUsed(plainPacket.getUsed());
		} catch (ShortBufferException | IllegalBlockSizeException |
				InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
			throw new SrtxpSecurityException(e.getMessage());
		}
	}

	protected void decryptPayload(
				@NonNull BufferView encrPktView,
				int pktHeaderSize,
				@NonNull BufferExt curIvBuf,
				@NonNull BufferExt outputPlainPacket
			) throws SrtxpSecurityException {
		if (pktHeaderSize > 0) {
			outputPlainPacket.copyOf(encrPktView.getInternalBaPtr(), 0, pktHeaderSize);
		} else {
			outputPlainPacket.clear();
		}
		outputPlainPacket.increaseSize(encrPktView.getLength());

		try {
			ctxCam.cipherObj.init(
					Cipher.DECRYPT_MODE,
					ctxCam.sksCipherObj,
					new IvParameterSpec(curIvBuf.getBaPtr(), 0, curIvBuf.getUsed())
				);

			ctxCam.cipherObj.doFinal(
					encrPktView.getInternalBaPtr(),
					pktHeaderSize,
					encrPktView.getLength() - pktHeaderSize,
					outputPlainPacket.getBaPtr(),
					pktHeaderSize
				);
			//
			outputPlainPacket.setUsed(encrPktView.getLength());
		} catch (ShortBufferException | IllegalBlockSizeException |
				InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
			throw new SrtxpSecurityException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void computeAuthTagForRtp(
				@NonNull BufferView encrPktView,
				int roc,
				@NonNull BufferExt curAuthTagBuf
			) throws SrtxpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtxpSecurityException("Session Keys not set");
		}

		try {
			ctxCam.macObj.init(ctxCam.sksMacObj);

			ctxCam.macObj.update(encrPktView.getInternalBaPtr(), encrPktView.getOffset(), encrPktView.getLength());

			byte[] rocBytes = ByteBuffer.allocate(4)
					.order(ByteOrder.BIG_ENDIAN)
					.putInt(roc)
					.array();
			ctxCam.macObj.update(rocBytes);

			byte[] fullTag = ctxCam.macObj.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(ctxKmd.authTagLen());  // cut off what we don't need
		} catch (InvalidKeyException e) {
			throw new SrtxpSecurityException(e.getMessage());
		}
	}

	protected void computeAuthTagForRtcp(
				@NonNull BufferView encrPktView,
				@NonNull BufferExt curAuthTagBuf
			) throws SrtxpSecurityException {
		if (ctxSessionKeysRtcp == null) {
			throw new SrtxpSecurityException("Session Keys not set");
		}

		try {
			ctxCam.macObj.init(ctxCam.sksMacObj);

			ctxCam.macObj.update(encrPktView.getInternalBaPtr(), encrPktView.getOffset(), encrPktView.getLength());

			byte[] fullTag = ctxCam.macObj.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(ctxKmd.authTagLen());  // cut off what we don't need
		} catch (InvalidKeyException e) {
			throw new SrtxpSecurityException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void buildCamObject(@NonNull CtxCipherAndMac cam, @NonNull SessionKeys sessionKeys) throws SrtxpSecurityException {
		if (cam.cipherObj == null) {
			cam.cipherObj = buildCipherObject();
		}
		if (cam.sksCipherObj == null) {
			cam.sksCipherObj = buildSecretKeySpecObject(sessionKeys.encKey(), true);
		}
		if (cam.macObj == null) {
			cam.macObj = buildMacObject();
		}
		if (cam.sksMacObj == null) {
			cam.sksMacObj = buildSecretKeySpecObject(sessionKeys.authKey(), false);
		}
	}

	protected @NonNull SecretKeySpec buildSecretKeySpecObject(@NonNull BufferExt sessionKey, boolean isForEnc) throws SrtxpSecurityException {
		if (isForEnc) {
			validateSessionEncKey(sessionKey);
		} else {
			validateSessionAuthKey(sessionKey);
		}
		try {
			return new SecretKeySpec(sessionKey.getBaPtr(), 0, sessionKey.getUsed(), isForEnc ? "AES" : "HmacSHA1");
		} catch (IllegalArgumentException e) {
			throw new SrtxpSecurityException("Invalid key for SKS: " + e.getMessage());
		}
	}

	protected static @NonNull Mac buildMacObject() throws SrtxpSecurityException {
		try {
			return Mac.getInstance("HmacSHA1");
		} catch (NoSuchAlgorithmException e) {
			throw new SrtxpSecurityException("Could not build Mac object: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void validateAuthTag(
				@NonNull BufferView bufView,
				boolean isRtpPkt,
				int srtpRoc
			) throws SrtxpSecurityException {
		// copy Auth Tag from the received packet
		bufView.setOffset(bufView.getInternalBeLength() - ctxKmd.authTagLen());
		bufView.setLength(ctxKmd.authTagLen());
		bufView.copyViewIntoBe(cacheAuthTagRcvdBuf);

		// compute Auth Tag over: encrypted RTxP packet
		bufView.setOffset(0);
		bufView.setLength(bufView.getInternalBeLength() - ctxKmd.authTagLen() - ctxKmd.mki().sizeBytes());
		if (isRtpPkt) {
			computeAuthTagForRtp(bufView, srtpRoc, cacheAuthTagActualBuf);
		} else {
			computeAuthTagForRtcp(bufView, cacheAuthTagActualBuf);
		}

		// validate Auth Tag
		if (! cacheAuthTagActualBuf.equals(cacheAuthTagRcvdBuf)) {
			throw new SrtxpSecurityException("Invalid Auth Tag in SRT" + (isRtpPkt ? "" : "C") + "P packet (rcvd=" +
					cacheAuthTagRcvdBuf.toHexString() + ", exp=" + cacheAuthTagActualBuf.toHexString() + ")");
		}
	}

	protected void validateMki(@NonNull BufferView bufView, @NonNull String packetDesc) throws SrtxpSecurityException {
		final int orgLen = bufView.getLength();
		bufView.setLength(ctxKmd.mki().sizeBytes());
		bufView.copyViewIntoBe(cacheValidateMkiBuf);
		if (! ctxKmd.mki().equalsBufferBigEndian(cacheValidateMkiBuf)) {
			throw new SrtxpSecurityException("Invalid MKI in " + packetDesc + " packet: " +
					"is=" + cacheValidateMkiBuf.toHexString() + ", exp=" + ctxKmd.mki().toBufferExtBigEndian().toHexString());
		}
		bufView.setLength(orgLen);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** For internal use and Unit Tests */
	static @NonNull Cipher buildCipherObject() throws SrtxpSecurityException {
		try {
			return Cipher.getInstance("AES/CTR/NoPadding");
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new SrtxpSecurityException("Could not build Cipher object: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/** For internal use and Unit Tests */
	void setRtpSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtxpSecurityException {
		validateSessionKeys(sessionKeys);
		ctxSessionKeysRtp = sessionKeys.clone();
	}

	/** For internal use and Unit Tests */
	void setRtcpSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtxpSecurityException {
		validateSessionKeys(sessionKeys);
		ctxSessionKeysRtcp = sessionKeys.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/** For Unit Tests only */
	void setKmdMasterKeyIdentifier(@NonNull DynInteger mki) {
		ctxKmd = new SrtxpKmd(
				ctxKmd.encrKeyLen(),
				ctxKmd.masterKey().clone(),
				ctxKmd.masterSalt().clone(),
				ctxKmd.authKeyLen(),
				ctxKmd.authTagLen(),
				mki.clone(),
				ctxKmd.ssrcId(),
				ctxKmd.kdr().clone()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void validateSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtxpSecurityException {
		validateSessionEncKey(sessionKeys.encKey());
		if (sessionKeys.salt().getUsed() != KeySizes.SALT_SIZE) {
			throw new SrtxpSecurityException("Invalid RTP Session Salt length (expected " +
					KeySizes.SALT_SIZE + " bytes, got " + sessionKeys.salt().getUsed() + ")");
		}
		validateSessionAuthKey(sessionKeys.authKey());
	}

	private void validateSessionEncKey(@NonNull BufferExt sessionEncKey) throws SrtxpSecurityException {
		if (sessionEncKey.getUsed() != ctxKmd.encrKeyLen()) {
			throw new SrtxpSecurityException("Invalid RTP Session Encr Key length (expected " +
					ctxKmd.encrKeyLen() + " bytes, got " + sessionEncKey.getUsed() + ")");
		}
	}

	private void validateSessionAuthKey(@NonNull BufferExt sessionAuthKey) throws SrtxpSecurityException {
		if (ctxKmd.authKeyLen() <= 0) {
			throw new SrtxpSecurityException("Session Auth Key length not set");
		}
		if (sessionAuthKey.getUsed() != ctxKmd.authKeyLen()) {
			throw new SrtxpSecurityException("Invalid RTP Session Auth Key length (expected " +
					ctxKmd.authKeyLen() + " bytes, got " + sessionAuthKey.getUsed() + ")");
		}
	}

}

package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.constants.KeySizes;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

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

	protected @NonNull CtxCipherAndMac ctxCam = new CtxCipherAndMac();

	/**
	 * Constructor.
	 * @param isRtp Is this a context for RTP?
	 * @param kmd Key Management Data
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	protected SrtxpContextBase(boolean isRtp, @NonNull SrtxpKmd kmd) throws SrtpSecurityException {
		ctxKmd = kmd.clone();

		//
		final Cipher cipherAesCtr = buildCipherObject();

		//
		if (isRtp) {
			SessionKeys tmpSessionKeys = SrtpKeyDerivation.deriveForRtp(cipherAesCtr, ctxKmd);
			setRtpSessionKeys(tmpSessionKeys);
		} else {
			SessionKeys tmpSessionKeys = SrtpKeyDerivation.deriveForRtcp(cipherAesCtr, ctxKmd);
			setRtcpSessionKeys(tmpSessionKeys);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void encryptPayload(
				@NonNull Cipher cipherObj,
				@NonNull SecretKeySpec sksCipherObj,
				@NonNull BufferExt plainPacket,
				int pktHeaderSize,
				@NonNull BufferExt curIvBuf,
				@NonNull BufferExt outputEncrPacket
			) throws SrtpSecurityException {
		outputEncrPacket.copyOf(plainPacket, 0, pktHeaderSize);
		outputEncrPacket.increaseSize(plainPacket.getUsed() + 64);  // reserve some extra memory for the AuthTag etc.

		try {
			cipherObj.init(Cipher.ENCRYPT_MODE, sksCipherObj, new IvParameterSpec(curIvBuf.getBufPtr(), 0, curIvBuf.getUsed()));

			cipherObj.doFinal(
					plainPacket.getBufPtr(),
					pktHeaderSize,
					plainPacket.getUsed() - pktHeaderSize,
					outputEncrPacket.getBufPtr(),
					pktHeaderSize
				);
			outputEncrPacket.setUsed(plainPacket.getUsed());
		} catch (ShortBufferException | IllegalBlockSizeException |
				InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	protected void decryptPayload(
				@NonNull Cipher cipherObj,
				@NonNull SecretKeySpec sksCipherObj,
				@NonNull BufferView encrPktView,
				int pktHeaderSize,
				@NonNull BufferExt curIvBuf,
				@NonNull BufferExt outputPlainPacket
			) throws SrtpSecurityException {
		if (pktHeaderSize > 0) {
			outputPlainPacket.copyOf(encrPktView.getInternalBaPtr(), 0, pktHeaderSize);
		} else {
			outputPlainPacket.clear();
		}
		outputPlainPacket.increaseSize(encrPktView.getLength());

		try {
			cipherObj.init(
					Cipher.DECRYPT_MODE,
					sksCipherObj,
					new IvParameterSpec(curIvBuf.getBufPtr(), 0, curIvBuf.getUsed())
				);

			cipherObj.doFinal(
					encrPktView.getInternalBaPtr(),
					pktHeaderSize,
					encrPktView.getLength() - pktHeaderSize,
					outputPlainPacket.getBufPtr(),
					pktHeaderSize
				);
			//
			outputPlainPacket.setUsed(encrPktView.getLength());
		} catch (ShortBufferException | IllegalBlockSizeException |
				InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void computeAuthTagForRtp(
				@NonNull Mac macObj,
				@NonNull SecretKeySpec sksMacObj,
				@NonNull BufferView encrPktView,
				long packetIndex,
				@NonNull BufferExt curAuthTagBuf
			) throws SrtpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		try {
			macObj.init(sksMacObj);

			macObj.update(encrPktView.getInternalBaPtr(), encrPktView.getOffset(), encrPktView.getLength());

			byte[] rocBytes = ByteBuffer.allocate(4)
					.order(ByteOrder.BIG_ENDIAN)
					.putInt((int)(packetIndex >> 16))
					.array();
			macObj.update(rocBytes);

			byte[] fullTag = macObj.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // cut off what we don't need
		} catch (InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	protected void computeAuthTagForRtcp(
				@NonNull Mac macObj,
				@NonNull SecretKeySpec sksMacObj,
				@NonNull BufferView encrPktView,
				@NonNull BufferExt curAuthTagBuf
			) throws SrtpSecurityException {
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		try {
			macObj.init(sksMacObj);

			macObj.update(encrPktView.getInternalBaPtr(), encrPktView.getOffset(), encrPktView.getLength());

			byte[] fullTag = macObj.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // cut off what we don't need
		} catch (InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected @NonNull CtxCipherAndMac buildCamObject(@NonNull CtxCipherAndMac cam, @NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
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
		return cam;
	}

	protected @NonNull SecretKeySpec buildSecretKeySpecObject(@NonNull BufferExt sessionKey, boolean isForEnc) throws SrtpSecurityException {
		if (isForEnc) {
			validateSessionEncKey(sessionKey);
		} else {
			validateSessionAuthKey(sessionKey);
		}
		try {
			return new SecretKeySpec(sessionKey.getBufPtr(), 0, sessionKey.getUsed(), isForEnc ? "AES" : "HmacSHA1");
		} catch (IllegalArgumentException e) {
			throw new SrtpSecurityException("Invalid key for SKS: " + e.getMessage());
		}
	}

	protected static @NonNull Mac buildMacObject() throws SrtpSecurityException {
		try {
			return Mac.getInstance("HmacSHA1");
		} catch (NoSuchAlgorithmException e) {
			throw new SrtpSecurityException("Could not build Mac object: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected void validateAuthTag(
				@NonNull BufferView bufView,
				@NonNull CtxCipherAndMac camPtr,
				boolean isRtpPkt,
				long srtpPacketIndex
			) throws SrtpSecurityException {
		BufferExt authTagRcvd = new BufferExt();
		BufferExt authTagActual = new BufferExt();

		// copy Auth Tag from the received packet
		bufView.setOffset(bufView.getInternalBeLength() - KeySizes.AUTH_TAG_SIZE);
		bufView.setLength(KeySizes.AUTH_TAG_SIZE);
		bufView.copyViewIntoBe(authTagRcvd);

		// compute Auth Tag over: encrypted RTxP packet
		bufView.setOffset(0);
		bufView.setLength(bufView.getInternalBeLength() - KeySizes.AUTH_TAG_SIZE - ctxKmd.mki().getUsed());
		if (isRtpPkt) {
			computeAuthTagForRtp(
					camPtr.macObj,
					camPtr.sksMacObj,
					bufView,
					srtpPacketIndex,
					authTagActual
			);
		} else {
			computeAuthTagForRtcp(
					camPtr.macObj,
					camPtr.sksMacObj,
					bufView,
					authTagActual
				);
		}

		// validate Auth Tag
		if (! authTagActual.equals(authTagRcvd)) {
			throw new SrtpSecurityException("Invalid Auth Tag in SRT" + (isRtpPkt ? "" : "C") + "P packet (rcvd=" +
					authTagRcvd.toHexString() + ", exp=" + authTagActual.toHexString() + ")");
		}
	}

	protected void validateMki(@NonNull BufferView bufView, @NonNull String packetDesc) throws SrtpSecurityException {
		BufferExt tmpMkiBe = new BufferExt();
		final int orgLen = bufView.getLength();
		bufView.setLength(ctxKmd.mki().getUsed());
		bufView.copyViewIntoBe(tmpMkiBe);
		if (! ctxKmd.mki().equals(tmpMkiBe)) {
			throw new SrtpSecurityException("Invalid MKI in " + packetDesc + " packet: " +
					"is=" + tmpMkiBe.toHexString() + ", exp=" + ctxKmd.mki().toHexString());
		}
		bufView.setLength(orgLen);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** For internal use and Unit Tests */
	static @NonNull Cipher buildCipherObject() throws SrtpSecurityException {
		try {
			return Cipher.getInstance("AES/CTR/NoPadding");
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new SrtpSecurityException("Could not build Cipher object: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/** For internal use and Unit Tests */
	void setRtpSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
		validateSessionKeys(sessionKeys);
		ctxSessionKeysRtp = sessionKeys.clone();
	}

	/** For internal use and Unit Tests */
	void setRtcpSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
		validateSessionKeys(sessionKeys);
		ctxSessionKeysRtcp = sessionKeys.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/** For Unit Tests only */
	@SuppressWarnings("SameParameterValue")
	void setKmdAuthKeyLength(int authKeyLen) {
		ctxKmd = new SrtxpKmd(ctxKmd.masterKey().clone(), ctxKmd.masterSalt().clone(), authKeyLen, ctxKmd.mki().clone());
	}

	/** For Unit Tests only */
	void setKmdMasterKeyIdentifier(@NonNull BufferExt mki) {
		ctxKmd = new SrtxpKmd(ctxKmd.masterKey().clone(), ctxKmd.masterSalt().clone(), ctxKmd.authKeyLen(), mki);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void validateSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
		validateSessionEncKey(sessionKeys.encKey());
		if (sessionKeys.salt().getUsed() != KeySizes.SALT_SIZE) {
			throw new SrtpSecurityException("Invalid RTP Session Salt length (expected " +
					KeySizes.SALT_SIZE + " bytes, got " + sessionKeys.salt().getUsed() + ")");
		}
		validateSessionAuthKey(sessionKeys.authKey());
	}

	private static void validateSessionEncKey(@NonNull BufferExt sessionEncKey) throws SrtpSecurityException {
		if (sessionEncKey.getUsed() != KeySizes.AES_128_KEY_SIZE) {
			throw new SrtpSecurityException("Invalid RTP Session Encr Key length (expected " +
					KeySizes.AES_128_KEY_SIZE + " bytes, got " + sessionEncKey.getUsed() + ")");
		}
	}

	private void validateSessionAuthKey(@NonNull BufferExt sessionAuthKey) throws SrtpSecurityException {
		if (ctxKmd.authKeyLen() <= 0) {
			throw new SrtpSecurityException("Session Auth Key length not set");
		}
		if (sessionAuthKey.getUsed() != ctxKmd.authKeyLen()) {
			throw new SrtpSecurityException("Invalid RTP Session Auth Key length (expected " +
					ctxKmd.authKeyLen() + " bytes, got " + sessionAuthKey.getUsed() + ")");
		}
	}

}

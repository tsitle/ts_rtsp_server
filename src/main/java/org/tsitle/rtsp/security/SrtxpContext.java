package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketHeader;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketSR;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.security.constants.KeySizes;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Context for SRTP/RTP and SRTCP/RTCP encryption/decryption
 */
public class SrtxpContext implements Cloneable {

	private static class CtxCipherAndMac implements Cloneable {
		Cipher cipherObj = null;
		SecretKeySpec sksCipherObj = null;
		Mac macObj = null;
		SecretKeySpec sksMacObj = null;

		@Override
		public CtxCipherAndMac clone() {
			try {
				CtxCipherAndMac clone = (CtxCipherAndMac)super.clone();
				clone.cipherObj = null;
				clone.sksCipherObj = null;
				clone.macObj = null;
				clone.sksMacObj = null;
				return clone;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Size of the SRTCP Index Field in bytes */
	public static final int SRTCP_INDEX_FIELD_SIZE = 4;

	private static final int RTP_PLAIN_HEADER_SIZE = RtpPacketContainerBase.RTP_CONT_HEADER_SIZE;
	private static final int RTCP_PLAIN_HEADER_SIZE = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;

	/** Master AES-128 key (16 bytes) */
	private BufferExt ctxMasterEncKey = new BufferExt();
	/** Master SRTP salt (14 bytes) */
	private BufferExt ctxMasterSalt = new BufferExt();

	/** Session keys for RTP/SRTP */
	private @Nullable SessionKeys ctxSessionKeysRtp = null;
	/** Session keys for RTCP/SRTCP */
	private @Nullable SessionKeys ctxSessionKeysRtcp = null;

	/** For RTP encryption: Rollover counter */
	private long ctxStateRtpRocOutbound = 0;
	/** For SRTP decryption: Rollover counter */
	private long ctxStateRtpRocInbound = 0;
	/** For SRTP decryption: Last packet index */
	private long ctxStateSrtpLastIndex = -1;

	/** For RTCP encryption: Packet index */
	private int ctxStateRtcpIndex = 0;
	/** For SRTCP decryption: Sender SSRC */
	private int ctxStateSrtcpSsrc = 0;
	/** For SRTCP decryption: Last packet index */
	private int ctxStateSrtcpLastIndex = -1;

	/** Have we received a MIKEY message? */
	private boolean haveMikey = false;

	/** Master key identifier length */
	private int ctxMasterKeyIdentifierLength = 0;
	/** Master key identifier value */
	private int ctxMasterKeyIdentifierVal = 0;
	/** Auth key length */
	private int ctxAuthKeyLength = 0;

	private @NonNull CtxCipherAndMac ctxCamRtpEncr = new CtxCipherAndMac();
	private @NonNull CtxCipherAndMac ctxCamRtpDecr = new CtxCipherAndMac();
	private @NonNull CtxCipherAndMac ctxCamRtcpEncr = new CtxCipherAndMac();
	private @NonNull CtxCipherAndMac ctxCamRtcpDecr = new CtxCipherAndMac();

	public SrtxpContext() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse a MIKEY message according to RFC-3830 Section 6.2 (Key data transport payload aka KEMAC)
	 * and derive the session keys from it.<br />
	 * @param msgB64 Base64 encoded MIKEY message
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void setClientMikey(@NonNull String msgB64) throws SrtpSecurityException {
		if (haveMikey) {
			throw new SrtpSecurityException("Client MIKEY already set");
		}

		// parse MIKEY message
		MikeyParser.SrtpKeys keys = MikeyParser.parseKeyMgmtData(msgB64);
		ctxMasterEncKey.copyOf(keys.masterKey());
		ctxMasterSalt.copyOf(keys.masterSalt());
		ctxMasterKeyIdentifierLength = keys.mkiLen();
		ctxMasterKeyIdentifierVal = keys.mkiVal();
		ctxAuthKeyLength = keys.authKeyLen();

		//
		haveMikey = true;

		//
		final Cipher cipherAesCtr = buildCipherObject();

		//
		SessionKeys tmpSessionKeys = SrtpKeyDerivation.deriveForRtp(cipherAesCtr, ctxMasterEncKey, ctxMasterSalt, ctxAuthKeyLength);
		setRtpSessionKeys(tmpSessionKeys);

		//
		tmpSessionKeys = SrtpKeyDerivation.deriveForRtcp(cipherAesCtr, ctxMasterEncKey, ctxMasterSalt, ctxAuthKeyLength);
		setRtcpSessionKeys(tmpSessionKeys);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the extra packet length for encrypted SRTP packets
	 * @return Extra packet length
	 */
	public int getSrtpExtraPacketLength() throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		return (KeySizes.AUTH_TAG_SIZE + ctxMasterKeyIdentifierLength);
	}

	/**
	 * Get the extra packet length for encrypted SRTCP packets
	 * @return Extra packet length
	 */
	public int getSrtcpExtraPacketLength() throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		return (KeySizes.AUTH_TAG_SIZE + SRTCP_INDEX_FIELD_SIZE + ctxMasterKeyIdentifierLength);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Encrypt an RTP packet buffer according to RFC-3711 Section 3.1
	 * @param rtpPacketBuf RTP packet buffer
	 * @param hasCsrcList Whether the RTP packet header contains a CSRC list
	 * @param hasHeaderExtension Whether the RTP packet header contains a header extension
	 * @param hdSeqNr Sequence number of the RTP packet
	 * @param hdSsrcId SSRC ID of the RTP packet
	 * @param outputEncryptedPacketBuf Encrypted RTP packet buffer
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void protectRtp(
				@NonNull BufferExt rtpPacketBuf,
				boolean hasCsrcList,
				boolean hasHeaderExtension,
				short hdSeqNr,
				int hdSsrcId,
				@NonNull BufferExt outputEncryptedPacketBuf
			) throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		if (ctxSessionKeysRtp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		/*
		 * Encrypted SRTP format:
		 *   [RTP packet header plaintext][encrypted RTP payload][MKI][auth tag]
		 *
		 * If the RTP packet header includes a CSRC list, the CSRC list must not be encrypted.
		 * The same is true for the RTP packet header extension.
		 */

		if (hasCsrcList || hasHeaderExtension) {
			throw new SrtpSecurityException("Unsupported: RTP packet header contains CSRC list or header extension");
		}

		// SRTP packet index
		final long srtpPacketIndex = ((ctxStateRtpRocOutbound << 16) | ((long)hdSeqNr & 0xFFFFL));

		//
		final BufferExt curIvBuf = new BufferExt();
		final BufferExt curAuthTagBuf = new BufferExt();

		// build IV
		buildIvForRtp(srtpPacketIndex, hdSsrcId, curIvBuf);

		//
		CtxCipherAndMac camPtr = buildCamObject(ctxCamRtpEncr, ctxSessionKeysRtp);

		// encrypt payload
		encryptPayload(
				camPtr.cipherObj,
				camPtr.sksCipherObj,
				rtpPacketBuf,
				RTP_PLAIN_HEADER_SIZE,
				curIvBuf,
				outputEncryptedPacketBuf
			);

		// compute auth tag (10 bytes)
		computeAuthTagForRtp(camPtr.macObj, camPtr.sksMacObj, outputEncryptedPacketBuf, srtpPacketIndex, curAuthTagBuf);

		// append MKI (4 bytes)
		if (ctxMasterKeyIdentifierLength != 0) {
			byte[] tmpMkiBufArr = getMkiAsByteArray();
			outputEncryptedPacketBuf.append(tmpMkiBufArr);
		}

		// build final SRTP packet
		outputEncryptedPacketBuf.append(curAuthTagBuf);  // 10 bytes

		// update ROC if sequence wrapped
		if (hdSeqNr == (short)0xFFFF) {
			ctxStateRtpRocOutbound++;
		}
	}

	/**
	 * Decrypt an SRTP packet buffer according to RFC-3711 Section 3.1
	 * @param srtpPacketBuf SRTP packet buffer
	 * @param hdSeqNr Sequence number of the RTP packet
	 * @param hdSsrcId SSRC ID of the RTP packet
	 * @param outputDecryptedPacketBuf Decrypted RTP packet buffer
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void unprotectSrtp(
				@NonNull BufferExt srtpPacketBuf,
				short hdSeqNr,
				int hdSsrcId,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		if (ctxSessionKeysRtp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		if (srtpPacketBuf.getUsed() < RTP_PLAIN_HEADER_SIZE + getSrtpExtraPacketLength()) {
			throw new SrtpSecurityException("Invalid SRTP packet length: " +
					srtpPacketBuf.getUsed() + " < " + (RTP_PLAIN_HEADER_SIZE + getSrtpExtraPacketLength()) + " bytes");
		}

		//
		CtxCipherAndMac camPtr = buildCamObject(ctxCamRtpDecr, ctxSessionKeysRtp);

		//
		final BufferExt curIvBuf = new BufferExt();
		final BufferExt curAuthTagBufRcvd = new BufferExt();
		final BufferExt curAuthTagBufExp = new BufferExt();
		final BufferExt remaingEncrBuf = new BufferExt();

		// SRTP packet index
		final long srtpPacketIndex = ((ctxStateRtpRocInbound << 16) | ((long)hdSeqNr & 0xFFFFL));
		if (srtpPacketIndex <= ctxStateSrtpLastIndex) {
			throw new SrtpSecurityException("Invalid SRTP packet index: " + srtpPacketIndex + " <= " + ctxStateSrtpLastIndex);
		}
		ctxStateSrtpLastIndex = srtpPacketIndex;

		// Auth Tag
		curAuthTagBufRcvd.copyOf(
				srtpPacketBuf,
				srtpPacketBuf.getUsed() - KeySizes.AUTH_TAG_SIZE,
				KeySizes.AUTH_TAG_SIZE
			);
		remaingEncrBuf.copyOf(
				srtpPacketBuf,
				0,
				srtpPacketBuf.getUsed() - KeySizes.AUTH_TAG_SIZE - ctxMasterKeyIdentifierLength
			);
		computeAuthTagForRtp(camPtr.macObj, camPtr.sksMacObj, remaingEncrBuf, srtpPacketIndex, curAuthTagBufExp);

		//
		int inpSz = srtpPacketBuf.getUsed() - KeySizes.AUTH_TAG_SIZE;
		ByteBuffer inpBuf = ByteBuffer
				.wrap(srtpPacketBuf.getBufPtr(), 0, inpSz)
				.order(ByteOrder.BIG_ENDIAN);

		// MKI
		if (ctxMasterKeyIdentifierLength != 0) {
			inpBuf.position(inpSz - ctxMasterKeyIdentifierLength);
			int tmpMki = inpBuf.getInt();
			if (tmpMki != ctxMasterKeyIdentifierVal) {
				throw new SrtpSecurityException("Invalid MKI in SRTP packet");
			}
			inpSz -= ctxMasterKeyIdentifierLength;
		}

		// validate the Auth Tag
		if (! curAuthTagBufExp.equals(curAuthTagBufRcvd)) {
			throw new SrtpSecurityException("Invalid Auth Tag in SRTP packet (rcvd=" +
					curAuthTagBufRcvd.toHexString() + ", exp=" + curAuthTagBufExp.toHexString() + ")");
		}

		// build IV
		buildIvForRtp(srtpPacketIndex, hdSsrcId, curIvBuf);

		// decrypt RTP payload
		remaingEncrBuf.copyOf(srtpPacketBuf, 0, inpSz);
		decryptPayload(
				camPtr.cipherObj,
				camPtr.sksCipherObj,
				remaingEncrBuf,
				RTP_PLAIN_HEADER_SIZE,
				curIvBuf,
				outputDecryptedPacketBuf
			);

		// update ROC if sequence wrapped
		if (hdSeqNr == (short)0xFFFF) {
			ctxStateRtpRocInbound++;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Encrypt an RTCP packet buffer containing a compound RR packet according to RFC-3711 Section 3.4
	 * @param rtcpPacketBuf RTCP packet buffer
	 * @param ssrcId SSRC ID of the RTP stream
	 * @param outputEncryptedPacketBuf Encrypted RTCP packet buffer
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	@SuppressWarnings("unused")
	public void protectRtcpRrCompound(
				@NonNull BufferExt rtcpPacketBuf,
				int ssrcId,
				@NonNull BufferExt outputEncryptedPacketBuf
			) throws SrtpSecurityException {
		protectRtcpSrCompound(rtcpPacketBuf, ssrcId, outputEncryptedPacketBuf);
	}

	/**
	 * Encrypt an RTCP packet buffer containing a compound SR packet according to RFC-3711 Section 3.4
	 * @param rtcpPacketBuf RTCP packet buffer
	 * @param ssrcId SSRC ID of the RTP stream
	 * @param outputEncryptedPacketBuf Encrypted RTCP packet buffer
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void protectRtcpSrCompound(
				@NonNull BufferExt rtcpPacketBuf,
				int ssrcId,
				@NonNull BufferExt outputEncryptedPacketBuf
			) throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		if (rtcpPacketBuf.getUsed() < RTCP_PLAIN_HEADER_SIZE) {
			throw new SrtpSecurityException("Invalid RTCP packet length");
		}

		/*
		 * Encrypted SRTCP format:
		 *   [RTCP packet header plaintext][encrypted RTCP payload][SRTCP index/E-bit][MKI][auth tag]
		 *
		 * SRTCP may only use packets where the first part MUST be a sender report or a receiver report.
		 */

		// SRTCP index is 31 bits + 1 E-bit (encryption flag) in the MSB
		final int srtcpIndexOnly = (ctxStateRtcpIndex & 0x7FFFFFFF);
		final int srtcpIndexEbit = 0x80000000;
		final int srtcpIndexField = (srtcpIndexEbit | srtcpIndexOnly);

		//
		final BufferExt curIvBuf = new BufferExt();
		final BufferExt curAuthTagBuf = new BufferExt();

		// build IV
		buildIvForRtcp(srtcpIndexOnly, ssrcId, curIvBuf);

		//
		CtxCipherAndMac camPtr = buildCamObject(ctxCamRtcpEncr, ctxSessionKeysRtcp);

		// encrypt RTCP payload
		encryptPayload(
				camPtr.cipherObj,
				camPtr.sksCipherObj,
				rtcpPacketBuf,
				RTCP_PLAIN_HEADER_SIZE,
				curIvBuf,
				outputEncryptedPacketBuf
			);

		// append SRTCP index / E-bit (4 bytes)
		byte[] tmpIndexEbitBufArr = new byte[SRTCP_INDEX_FIELD_SIZE];
		ByteBuffer tmpIndexEbitBufObj = ByteBuffer.wrap(tmpIndexEbitBufArr).order(ByteOrder.BIG_ENDIAN);
		tmpIndexEbitBufObj.putInt(srtcpIndexField);
		outputEncryptedPacketBuf.append(tmpIndexEbitBufArr);

		// compute auth tag over: encrypted RTCP packet + SRTCP index/E-bit
		computeAuthTagForRtcp(camPtr.macObj, camPtr.sksMacObj, outputEncryptedPacketBuf, curAuthTagBuf);

		// append MKI (4 bytes)
		if (ctxMasterKeyIdentifierVal != 0) {
			byte[] tmpMkiBufArr = getMkiAsByteArray();
			outputEncryptedPacketBuf.append(tmpMkiBufArr);
		}

		// append auth tag (10 bytes for HMAC-SHA1-80)
		outputEncryptedPacketBuf.append(curAuthTagBuf);

		// advance RTCP index
		ctxStateRtcpIndex = ((ctxStateRtcpIndex + 1) & 0x7FFFFFFF);
	}

	/**
	 * Decrypt an SRTCP packet buffer (containing a compound SR/RR packet) according to RFC-3711 Section 3.4
	 * @param srtcpPacketBuf SRTCP packet buffer
	 * @param outputDecryptedPacketBuf Decrypted RTCP packet buffer
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void unprotectSrtcpCompound(
				@NonNull BufferExt srtcpPacketBuf,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		if (srtcpPacketBuf.getUsed() < RTCP_PLAIN_HEADER_SIZE + getSrtcpExtraPacketLength()) {
			throw new SrtpSecurityException("Invalid SRTCP packet length: " +
					srtcpPacketBuf.getUsed() + " < " + (RTCP_PLAIN_HEADER_SIZE + getSrtcpExtraPacketLength()) + " bytes");
		}

		/*
		 * Encrypted SRTCP format:
		 *   [RTCP packet header plaintext][encrypted RTCP payload][SRTCP index/E-bit][MKI][auth tag]
		 *
		 * SRTCP may use only packets where the first part MUST be a sender report or a receiver report.
		 */

		//
		CtxCipherAndMac camPtr = buildCamObject(ctxCamRtcpDecr, ctxSessionKeysRtcp);

		//
		final BufferExt curIvBuf = new BufferExt();
		final BufferExt curAuthTagBufRcvd = new BufferExt();
		final BufferExt curAuthTagBufExp = new BufferExt();
		final BufferExt remaingEncrBuf = new BufferExt();

		// Auth Tag
		curAuthTagBufRcvd.copyOf(
				srtcpPacketBuf,
				srtcpPacketBuf.getUsed() - KeySizes.AUTH_TAG_SIZE,
				KeySizes.AUTH_TAG_SIZE
			);
		remaingEncrBuf.copyOf(
				srtcpPacketBuf,
				0,
				srtcpPacketBuf.getUsed() - KeySizes.AUTH_TAG_SIZE - ctxMasterKeyIdentifierLength
			);
		computeAuthTagForRtcp(camPtr.macObj, camPtr.sksMacObj, remaingEncrBuf, curAuthTagBufExp);

		//
		int inpSz = srtcpPacketBuf.getUsed() - KeySizes.AUTH_TAG_SIZE;
		ByteBuffer inpBuf = ByteBuffer
				.wrap(srtcpPacketBuf.getBufPtr(), 0, inpSz)
				.order(ByteOrder.BIG_ENDIAN);

		// MKI
		if (ctxMasterKeyIdentifierLength != 0) {
			inpBuf.position(inpSz - ctxMasterKeyIdentifierLength);
			int tmpMki = inpBuf.getInt();
			if (tmpMki != ctxMasterKeyIdentifierVal) {
				throw new SrtpSecurityException("Invalid MKI in SRTCP packet");
			}
			inpSz -= ctxMasterKeyIdentifierLength;
		}

		// validate the Auth Tag
		if (! curAuthTagBufExp.equals(curAuthTagBufRcvd)) {
			throw new SrtpSecurityException("Invalid Auth Tag in SRTCP packet (rcvd=" +
					curAuthTagBufRcvd.toHexString() + ", exp=" + curAuthTagBufExp.toHexString() + ")");
		}

		// SRTCP index is 31 bits + 1 E-bit (encryption flag) in the MSB
		inpBuf.position(inpSz - SRTCP_INDEX_FIELD_SIZE);
		int tmpIndexField = inpBuf.getInt();
		int tmpIndexEbit = (tmpIndexField & 0x80000000);
		int tmpIndexOnly = (tmpIndexField & 0x7FFFFFFF);
		if (tmpIndexEbit != 0x80000000) {
			throw new SrtpSecurityException("Invalid E-bit in SRTCP packet");
		}
		if (ctxStateSrtcpLastIndex >= tmpIndexOnly) {
			throw new SrtpSecurityException("Invalid SRTCP packet index");
		}
		ctxStateSrtcpLastIndex = tmpIndexOnly;
		inpSz -= SRTCP_INDEX_FIELD_SIZE;

		// Sender SSRC
		inpBuf.position(RtcpPacketHeader.HEADER_SIZE);
		int tmpSenderSsrc = inpBuf.getInt();
		if (ctxStateSrtcpSsrc != 0 && tmpSenderSsrc != ctxStateSrtcpSsrc) {
			throw new SrtpSecurityException("Invalid Sender SSRC in SRTCP packet");
		}
		ctxStateSrtcpSsrc = tmpSenderSsrc;

		// build IV
		buildIvForRtcp(tmpIndexOnly, tmpSenderSsrc, curIvBuf);

		// decrypt RTCP payload
		remaingEncrBuf.copyOf(srtcpPacketBuf, 0, inpSz);
		decryptPayload(
				camPtr.cipherObj,
				camPtr.sksCipherObj,
				remaingEncrBuf,
				RTCP_PLAIN_HEADER_SIZE,
				curIvBuf,
				outputDecryptedPacketBuf
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public SrtxpContext clone() {
		try {
			SrtxpContext clone = (SrtxpContext)super.clone();

			clone.ctxMasterEncKey = this.ctxMasterEncKey.clone();
			clone.ctxMasterSalt = this.ctxMasterSalt.clone();

			if (this.ctxSessionKeysRtp != null) {
				clone.ctxSessionKeysRtp = this.ctxSessionKeysRtp.clone();
			}
			if (this.ctxSessionKeysRtcp != null) {
				clone.ctxSessionKeysRtcp = this.ctxSessionKeysRtcp.clone();
			}

			clone.ctxCamRtpEncr = this.ctxCamRtpEncr.clone();
			clone.ctxCamRtpDecr = this.ctxCamRtpDecr.clone();
			clone.ctxCamRtcpEncr = this.ctxCamRtcpEncr.clone();
			clone.ctxCamRtcpDecr = this.ctxCamRtcpDecr.clone();

			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void encryptPayload(
				@NonNull Cipher cipherObj,
				@NonNull SecretKeySpec sksCipherObj,
				@NonNull BufferExt plainPacket,
				int pktHeaderSize,
				@NonNull BufferExt curIvBuf,
				@NonNull BufferExt outputEncrPacket
			) throws SrtpSecurityException {
		outputEncrPacket.copyOf(plainPacket, 0, pktHeaderSize);
		outputEncrPacket.increaseSize(plainPacket.getUsed() + 64);  // reserve some extra memory for the AuthTag etc.
		outputEncrPacket.setUsed(plainPacket.getUsed());

		try {
			cipherObj.init(Cipher.ENCRYPT_MODE, sksCipherObj, new IvParameterSpec(curIvBuf.getBufPtr(), 0, curIvBuf.getUsed()));

			cipherObj.doFinal(
					plainPacket.getBufPtr(),
					pktHeaderSize,
					outputEncrPacket.getUsed() - pktHeaderSize,
					outputEncrPacket.getBufPtr(),
					pktHeaderSize
				);
		} catch (ShortBufferException | IllegalBlockSizeException |
				InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	private void decryptPayload(
				@NonNull Cipher cipherObj,
				@NonNull SecretKeySpec sksCipherObj,
				@NonNull BufferExt encrPacket,
				int pktHeaderSize,
				@NonNull BufferExt curIvBuf,
				@NonNull BufferExt outputPlainPacket
			) throws SrtpSecurityException {
		if (pktHeaderSize > 0) {
			outputPlainPacket.copyOf(encrPacket, 0, pktHeaderSize);
		} else {
			outputPlainPacket.clear();
		}
		outputPlainPacket.increaseSize(encrPacket.getUsed());
		outputPlainPacket.setUsed(encrPacket.getUsed());

		try {
			cipherObj.init(Cipher.DECRYPT_MODE, sksCipherObj, new IvParameterSpec(curIvBuf.getBufPtr(), 0, curIvBuf.getUsed()));

			cipherObj.doFinal(
					encrPacket.getBufPtr(),
					pktHeaderSize,
					outputPlainPacket.getUsed() - pktHeaderSize,
					outputPlainPacket.getBufPtr(),
					pktHeaderSize
				);
		} catch (ShortBufferException | IllegalBlockSizeException |
				InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void computeAuthTagForRtp(
				@NonNull Mac macObj,
				@NonNull SecretKeySpec sksMacObj,
				@NonNull BufferExt encrRtpPacket,
				long packetIndex,
				@NonNull BufferExt curAuthTagBuf
			) throws SrtpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		try {
			macObj.init(sksMacObj);

			macObj.update(encrRtpPacket.getBufPtr(), 0, encrRtpPacket.getUsed());

			byte[] rocBytes = ByteBuffer.allocate(4).putInt((int)(packetIndex >> 16)).array();
			macObj.update(rocBytes);

			byte[] fullTag = macObj.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // 80-bit tag
		} catch (InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	private void computeAuthTagForRtcp(
				@NonNull Mac macObj,
				@NonNull SecretKeySpec sksMacObj,
				@NonNull BufferExt encrRtcpPacket,
				@NonNull BufferExt curAuthTagBuf
			) throws SrtpSecurityException {
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		try {
			macObj.init(sksMacObj);

			macObj.update(encrRtcpPacket.getBufPtr(), 0, encrRtcpPacket.getUsed());

			byte[] fullTag = macObj.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // 80-bit tag
		} catch (InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildIvForRtp(long packetIndex, int ssrc, @NonNull BufferExt curIvBuf) throws SrtpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		final byte[] tmpIvBytes = new byte[KeySizes.AES_128_KEY_SIZE];
		Arrays.fill(tmpIvBytes, (byte)0);

		/*
		 * RFC 3711 AES-CM counter-block layout (big-endian):
		 * IV = (0x00000000 || SSRC || (packetIndex << 16)) XOR (sessionSalt || 0x0000)
		 *
		 * packetIndex = (ROC << 16) | SEQ (48-bit effective value).
		 *
		 * Byte layout before XOR:
		 *   [0..3]   = 0x00000000
		 *   [4..7]   = SSRC
		 *   [8..13]  = packetIndex (48 bits)
		 *   [14..15] = 0x0000
		 */
		ByteBuffer buf = ByteBuffer.wrap(tmpIvBytes).order(ByteOrder.BIG_ENDIAN);
		buf.putInt(0);
		buf.putInt(ssrc);
		buf.put((byte)((packetIndex >>> 40) & 0xFF));
		buf.put((byte)((packetIndex >>> 32) & 0xFF));
		buf.put((byte)((packetIndex >>> 24) & 0xFF));
		buf.put((byte)((packetIndex >>> 16) & 0xFF));
		buf.put((byte)((packetIndex >>> 8) & 0xFF));
		buf.put((byte)(packetIndex & 0xFF));
		buf.putShort((short)0);

		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			tmpIvBytes[i] ^= ctxSessionKeysRtp.salt().get(i);
		}

		curIvBuf.copyOf(tmpIvBytes);
	}

	private void buildIvForRtcp(int packetIndex, int ssrc, @NonNull BufferExt curIvBuf) throws SrtpSecurityException {
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		final byte[] tmpIvBytes = new byte[KeySizes.AES_128_KEY_SIZE];
		Arrays.fill(tmpIvBytes, (byte)0);

		/*
		 * RFC 3711 AES-CM counter-block layout (big-endian):
		 * IV = (0x00000000 || SSRC || (packetIndex << 16)) XOR (sessionSalt || 0x0000)
		 *
		 * Byte layout before XOR:
		 *   [0..3]   = 0x00000000
		 *   [4..7]   = SSRC
		 *   [8..9]   = 0x0000
		 *   [10..13] = packetIndex (31-bit value, E-bit stripped already)
		 *   [14..15] = 0x0000
		 */
		ByteBuffer buf = ByteBuffer.wrap(tmpIvBytes).order(ByteOrder.BIG_ENDIAN);
		buf.putInt(0);
		buf.putInt(ssrc);
		buf.putShort((short)0);
		buf.putInt(packetIndex);
		buf.putShort((short)0);

		// XOR first 112 bits with session salt
		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			tmpIvBytes[i] ^= ctxSessionKeysRtcp.salt().get(i);
		}

		curIvBuf.copyOf(tmpIvBytes);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private byte[] getMkiAsByteArray() throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		byte[] resBa = new byte[ctxMasterKeyIdentifierLength];
		ByteBuffer tmpMkiBufObj = ByteBuffer.wrap(resBa).order(ByteOrder.BIG_ENDIAN);
		tmpMkiBufObj.putInt(ctxMasterKeyIdentifierVal);
		return resBa;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull CtxCipherAndMac buildCamObject(@NonNull CtxCipherAndMac cam, @NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
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

	private @NonNull SecretKeySpec buildSecretKeySpecObject(@NonNull BufferExt sessionKey, boolean isForEnc) throws SrtpSecurityException {
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

	private static @NonNull Mac buildMacObject() throws SrtpSecurityException {
		try {
			return Mac.getInstance("HmacSHA1");
		} catch (NoSuchAlgorithmException e) {
			throw new SrtpSecurityException("Could not build Mac object: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull Cipher buildCipherObject() throws SrtpSecurityException {
		try {
			return Cipher.getInstance("AES/CTR/NoPadding");
		} catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new SrtpSecurityException("Could not build Cipher object: " + e.getMessage());
		}
	}

	void setRtpSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
		validateSessionKeys(sessionKeys);
		ctxSessionKeysRtp = sessionKeys.clone();
	}

	void setRtcpSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
		validateSessionKeys(sessionKeys);
		ctxSessionKeysRtcp = sessionKeys.clone();
	}

	void validateSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		validateSessionEncKey(sessionKeys.encKey());
		if (sessionKeys.salt().getUsed() != KeySizes.SALT_SIZE) {
			throw new SrtpSecurityException("Invalid RTP Session Salt length (expected " +
					KeySizes.SALT_SIZE + " bytes, got " + sessionKeys.salt().getUsed() + ")");
		}
		validateSessionAuthKey(sessionKeys.authKey());
	}

	static void validateSessionEncKey(@NonNull BufferExt sessionEncKey) throws SrtpSecurityException {
		if (sessionEncKey.getUsed() != KeySizes.AES_128_KEY_SIZE) {
			throw new SrtpSecurityException("Invalid RTP Session Encr Key length (expected " +
					KeySizes.AES_128_KEY_SIZE + " bytes, got " + sessionEncKey.getUsed() + ")");
		}
	}

	void validateSessionAuthKey(@NonNull BufferExt sessionAuthKey) throws SrtpSecurityException {
		if (ctxAuthKeyLength <= 0) {
			throw new SrtpSecurityException("Session Auth Key length not set");
		}
		if (sessionAuthKey.getUsed() != ctxAuthKeyLength) {
			throw new SrtpSecurityException("Invalid RTP Session Auth Key length (expected " +
					ctxAuthKeyLength + " bytes, got " + sessionAuthKey.getUsed() + ")");
		}
	}

}

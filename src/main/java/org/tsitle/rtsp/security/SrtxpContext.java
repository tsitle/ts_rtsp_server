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

	/** For RTP encryption: Rollover counter for RTP packets */
	private long ctxStateRtpRoc = 0;

	/** For RTCP encryption: Packet index for RTCP packets */
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
	 * @param seqNr Sequence number of the RTP packet
	 * @param ssrcId SSRC ID of the RTP packet
	 * @param outputEncryptedPacketBuf Encrypted RTP packet buffer
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void protectRtp(
				@NonNull BufferExt rtpPacketBuf,
				boolean hasCsrcList,
				boolean hasHeaderExtension,
				int seqNr,
				int ssrcId,
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
		final long srtpPacketIndex = ((ctxStateRtpRoc << 16) | seqNr);

		//
		final BufferExt curIvBuf = new BufferExt();
		final BufferExt curAuthTagBuf = new BufferExt();

		// build IV
		buildIvForRtp(srtpPacketIndex, ssrcId, curIvBuf);

		// encrypt payload
		encryptPayload(
				buildCipherObject(),
				rtpPacketBuf,
				RTP_PLAIN_HEADER_SIZE,
				ctxSessionKeysRtp.encKey(),
				curIvBuf,
				outputEncryptedPacketBuf
			);

		// compute auth tag (10 bytes)
		computeAuthTagForRtp(outputEncryptedPacketBuf, srtpPacketIndex, curAuthTagBuf);

		// append MKI (4 bytes)
		if (ctxMasterKeyIdentifierLength != 0) {
			byte[] tmpMkiBufArr = getMkiAsByteArray();
			outputEncryptedPacketBuf.append(tmpMkiBufArr);
		}

		// build final SRTP packet
		outputEncryptedPacketBuf.append(curAuthTagBuf);  // 10 bytes

		// update ROC if sequence wrapped
		if (seqNr == 0xFFFF) {
			ctxStateRtpRoc++;
		}
	}

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

		// encrypt RTCP payload
		encryptPayload(
				buildCipherObject(),
				rtcpPacketBuf,
				RTCP_PLAIN_HEADER_SIZE,
				ctxSessionKeysRtcp.encKey(),
				curIvBuf,
				outputEncryptedPacketBuf
			);

		// append SRTCP index / E-bit (4 bytes)
		byte[] tmpIndexEbitBufArr = new byte[SRTCP_INDEX_FIELD_SIZE];
		ByteBuffer tmpIndexEbitBufObj = ByteBuffer.wrap(tmpIndexEbitBufArr).order(ByteOrder.BIG_ENDIAN);
		tmpIndexEbitBufObj.putInt(srtcpIndexField);
		outputEncryptedPacketBuf.append(tmpIndexEbitBufArr);

		// compute auth tag over: encrypted RTCP packet + SRTCP index/E-bit
		computeAuthTagForRtcp(outputEncryptedPacketBuf, curAuthTagBuf);

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

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Decrypt an SRTCP packet buffer (containing a compound SR/RR packet) according to RFC-3711 Section 3.4
	 * @param srtcpPacketBuf SRTCP packet buffer
	 * @param outputDecryptedPacketBuf Decrypted RTCP packet buffer
	 * @return True if the packet was decrypted, false otherwise
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public boolean unprotectSrtcpCompound(
				@NonNull BufferExt srtcpPacketBuf,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		if (srtcpPacketBuf.getUsed() < getSrtcpExtraPacketLength()) {
			// packet might not be encrypted
			outputDecryptedPacketBuf.copyOf(srtcpPacketBuf);
			return false;
		}

		/*
		 * Encrypted SRTCP format:
		 *   [RTCP packet header plaintext][encrypted RTCP payload][SRTCP index/E-bit][MKI][auth tag]
		 *
		 * SRTCP may use only packets where the first part MUST be a sender report or a receiver report.
		 */

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
		computeAuthTagForRtcp(remaingEncrBuf, curAuthTagBufExp);

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
				buildCipherObject(),
				remaingEncrBuf,
				RTCP_PLAIN_HEADER_SIZE,
				ctxSessionKeysRtcp.encKey(),
				curIvBuf,
				outputDecryptedPacketBuf
			);
		return true;
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

			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void encryptPayload(
				@NonNull Cipher cipher,
				@NonNull BufferExt plainPacket,
				int pktHeaderSize,
				@NonNull BufferExt sessionEncKey,
				@NonNull BufferExt curIvBuf,
				@NonNull BufferExt outputEncrPacket
			) throws SrtpSecurityException {
		outputEncrPacket.copyOf(plainPacket, 0, pktHeaderSize);
		outputEncrPacket.increaseSize(plainPacket.getUsed() + 64);  // reserve some extra memory for the AuthTag etc.
		outputEncrPacket.setUsed(plainPacket.getUsed());

		try {
			SecretKeySpec key = new SecretKeySpec(sessionEncKey.getBufPtr(), 0, sessionEncKey.getUsed(), "AES");
			cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(curIvBuf.getBufPtr(), 0, curIvBuf.getUsed()));

			cipher.doFinal(
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
				@NonNull Cipher cipher,
				@NonNull BufferExt encrPacket,
				int pktHeaderSize,  // @TODO implement unprotectRtp
				@NonNull BufferExt sessionEncKey,
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
			SecretKeySpec key = new SecretKeySpec(sessionEncKey.getBufPtr(), 0, sessionEncKey.getUsed(), "AES");
			cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(curIvBuf.getBufPtr(), 0, curIvBuf.getUsed()));

			cipher.doFinal(
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
				@NonNull BufferExt encrRtpPacket,
				long packetIndex,
				@NonNull BufferExt curAuthTagBuf
			) throws SrtpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(
					new SecretKeySpec(
							ctxSessionKeysRtp.authKey().getBufPtr(),
							0,
							ctxSessionKeysRtp.authKey().getUsed(),
							"HmacSHA1"
				));

			mac.update(encrRtpPacket.getBufPtr(), 0, encrRtpPacket.getUsed());

			byte[] rocBytes = ByteBuffer.allocate(4).putInt((int)(packetIndex >> 16)).array();
			mac.update(rocBytes);

			byte[] fullTag = mac.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // 80-bit tag
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	private void computeAuthTagForRtcp(
				@NonNull BufferExt encrRtcpPacket,
				@NonNull BufferExt curAuthTagBuf
			) throws SrtpSecurityException {
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(
					new SecretKeySpec(
							ctxSessionKeysRtcp.authKey().getBufPtr(),
							0,
							ctxSessionKeysRtcp.authKey().getUsed(),
							"HmacSHA1"
				));

			mac.update(encrRtcpPacket.getBufPtr(), 0, encrRtcpPacket.getUsed());

			byte[] fullTag = mac.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // 80-bit tag
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
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
		if (ctxAuthKeyLength <= 0) {
			throw new SrtpSecurityException("Session Auth Key length not set");
		}
		if (sessionKeys.encKey().getUsed() != KeySizes.AES_128_KEY_SIZE) {
			throw new SrtpSecurityException("Invalid RTP Session Encr Key length (expected " +
					KeySizes.AES_128_KEY_SIZE + " bytes, got " + sessionKeys.encKey().getUsed() + ")");
		}
		if (sessionKeys.salt().getUsed() != KeySizes.SALT_SIZE) {
			throw new SrtpSecurityException("Invalid RTP Session Salt length (expected " +
					KeySizes.SALT_SIZE + " bytes, got " + sessionKeys.salt().getUsed() + ")");
		}
		if (sessionKeys.authKey().getUsed() != ctxAuthKeyLength) {
			throw new SrtpSecurityException("Invalid RTP Session Auth Key length (expected " +
					ctxAuthKeyLength + " bytes, got " + sessionKeys.authKey().getUsed() + ")");
		}
	}

}

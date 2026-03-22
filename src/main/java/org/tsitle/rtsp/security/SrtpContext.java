package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
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

public class SrtpContext implements Cloneable {

	/** Size of the Master Key Identifier in bytes */
	public static final int MKI_SIZE = 4;
	/** Size of the SRTCP Index Field in bytes */
	public static final int SRTCP_INDEX_FIELD_SIZE = 4;

	/** Master AES-128 key (16 bytes) */
	private final byte[] ctxMasterEncKey = new byte[KeySizes.AES_128_KEY_SIZE];
	/** Master SRTP salt (14 bytes) */
	private final byte[] ctxMasterSalt = new byte[KeySizes.SALT_SIZE];

	/** RTP Session AES-128 key (16 bytes) */
	private final byte[] ctxRtpSessionEncKey = new byte[KeySizes.AES_128_KEY_SIZE];
	/** RTP Session salt (14 bytes) */
	private final byte[] ctxRtpSessionSalt = new byte[KeySizes.SALT_SIZE];
	/** RTP Session HMAC-SHA1 key (10 or 20 bytes) */
	private byte[] ctxRtpSessionAuthKey = new byte[KeySizes.AUTH_KEY_SIZE_160];

	/** RTCP Session AES-128 key (16 bytes) */
	private final byte[] ctxRtcpSessionEncKey = new byte[KeySizes.AES_128_KEY_SIZE];
	/** RTCP Session salt (14 bytes) */
	private final byte[] ctxRtcpSessionSalt = new byte[KeySizes.SALT_SIZE];
	/** RTCP Session HMAC-SHA1 key (10 or 20 bytes) */
	private byte[] ctxRtcpSessionAuthKey = new byte[KeySizes.AUTH_KEY_SIZE_160];

	/** For RTP encryption: Rollover counter for RTP packets */
	private long ctxRtpRoc = 0;

	/** For RTCP encryption: Packet index for RTCP packets */
	private int ctxRtcpIndex = 0;
	/** For SRTCP decryption: Sender SSRC */
	private int ctxSrtcpSsrc = 0;
	/** For SRTCP decryption: Last packet index */
	private int ctxSrtcpLastIndex = -1;

	/** Have we received a MIKEY message? */
	private boolean haveMikey = false;

	/** Master key identifier */
	private int ctxMasterKeyIdentifier = 0;

	public SrtpContext() {
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

		MikeyParser.SrtpKeys keys = MikeyParser.parseKeyMgmtData(msgB64);
		System.arraycopy(keys.masterKey(), 0, ctxMasterEncKey, 0, KeySizes.AES_128_KEY_SIZE);
		System.arraycopy(keys.masterSalt(), 0, ctxMasterSalt, 0, KeySizes.SALT_SIZE);
		ctxMasterKeyIdentifier = keys.mki();

		//
		SrtpKeyDerivation.SessionKeys tmpSessionKeys = SrtpKeyDerivation.deriveForRtp(ctxMasterEncKey, ctxMasterSalt, keys.authKeyLen());
		System.arraycopy(tmpSessionKeys.encKey(), 0, ctxRtpSessionEncKey, 0, tmpSessionKeys.encKey().length);
		System.arraycopy(tmpSessionKeys.salt(), 0, ctxRtpSessionSalt, 0, tmpSessionKeys.salt().length);
		ctxRtpSessionAuthKey = new byte[tmpSessionKeys.authKey().length];
		System.arraycopy(tmpSessionKeys.authKey(), 0, ctxRtpSessionAuthKey, 0, tmpSessionKeys.authKey().length);

		//
		tmpSessionKeys = SrtpKeyDerivation.deriveForRtcp(ctxMasterEncKey, ctxMasterSalt, keys.authKeyLen());
		System.arraycopy(tmpSessionKeys.encKey(), 0, ctxRtcpSessionEncKey, 0, tmpSessionKeys.encKey().length);
		System.arraycopy(tmpSessionKeys.salt(), 0, ctxRtcpSessionSalt, 0, tmpSessionKeys.salt().length);
		ctxRtcpSessionAuthKey = new byte[tmpSessionKeys.authKey().length];
		System.arraycopy(tmpSessionKeys.authKey(), 0, ctxRtcpSessionAuthKey, 0, tmpSessionKeys.authKey().length);

		//
		haveMikey = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public int getSrtpExtraPacketLength() {
		return KeySizes.AUTH_TAG_SIZE + (ctxMasterKeyIdentifier != 0 ? MKI_SIZE : 0);
	}

	public int getSrtcpExtraPacketLength() {
		return KeySizes.AUTH_TAG_SIZE + (ctxMasterKeyIdentifier != 0 ? MKI_SIZE : 0) + SRTCP_INDEX_FIELD_SIZE;
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
		final long srtpPacketIndex = ((ctxRtpRoc << 16) | seqNr);

		//
		final BufferExt curIvBuf = new BufferExt();
		final BufferExt curAuthTagBuf = new BufferExt();

		// build IV
		buildIvForRtp(srtpPacketIndex, ssrcId, curIvBuf);

		// encrypt payload
		encryptPayload(
				rtpPacketBuf,
				RtpPacketContainerBase.RTP_CONT_HEADER_SIZE,
				ctxRtpSessionEncKey,
				curIvBuf,
				outputEncryptedPacketBuf
			);

		// compute auth tag (10 bytes)
		computeAuthTagForRtp(outputEncryptedPacketBuf, srtpPacketIndex, curAuthTagBuf);

		// append MKI (4 bytes)
		if (ctxMasterKeyIdentifier != 0) {
			byte[] tmpMkiBufArr = new byte[MKI_SIZE];
			ByteBuffer tmpMkiBufObj = ByteBuffer.wrap(tmpMkiBufArr).order(ByteOrder.BIG_ENDIAN);
			tmpMkiBufObj.putInt(ctxMasterKeyIdentifier);
			outputEncryptedPacketBuf.append(tmpMkiBufArr);
		}

		// build final SRTP packet
		outputEncryptedPacketBuf.append(curAuthTagBuf);  // 10 bytes

		// update ROC if sequence wrapped
		if (seqNr == 0xFFFF) {
			ctxRtpRoc++;
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
		final int rtcpSrRrExtendedHeaderLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;
		if (rtcpPacketBuf.getUsed() < rtcpSrRrExtendedHeaderLen) {
			throw new SrtpSecurityException("Invalid RTCP packet length");
		}

		/*
		 * Encrypted SRTCP format:
		 *   [RTCP packet header plaintext][encrypted RTCP payload][SRTCP index/E-bit][auth tag]
		 *
		 * SRTCP may only use packets where the first part MUST be a sender report or a receiver report.
		 */

		// SRTCP index is 31 bits + 1 E-bit (encryption flag) in the MSB
		final int srtcpIndexOnly = (ctxRtcpIndex & 0x7FFFFFFF);
		final int srtcpIndexEbit = 0x80000000;
		final int srtcpIndexField = (srtcpIndexEbit | srtcpIndexOnly);

		//
		final BufferExt curIvBuf = new BufferExt();
		final BufferExt curAuthTagBuf = new BufferExt();

		// build IV
		buildIvForRtcp(srtcpIndexOnly, ssrcId, curIvBuf);

		// encrypt RTCP payload
		encryptPayload(
				rtcpPacketBuf,
				rtcpSrRrExtendedHeaderLen,
				ctxRtcpSessionEncKey,
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
		if (ctxMasterKeyIdentifier != 0) {
			byte[] tmpMkiBufArr = new byte[MKI_SIZE];
			ByteBuffer tmpMkiBufObj = ByteBuffer.wrap(tmpMkiBufArr).order(ByteOrder.BIG_ENDIAN);
			tmpMkiBufObj.putInt(ctxMasterKeyIdentifier);
			outputEncryptedPacketBuf.append(tmpMkiBufArr);
		}

		// append auth tag (10 bytes for HMAC-SHA1-80)
		outputEncryptedPacketBuf.append(curAuthTagBuf);

		// advance RTCP index
		ctxRtcpIndex = ((ctxRtcpIndex + 1) & 0x7FFFFFFF);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Decrypt an RTCP packet buffer (containing a compound SR/RR packet) according to RFC-3711 Section 3.4
	 * @param srtcpPacketBuf SRTCP packet buffer
	 * @param outputDecryptedPacketBuf Decrypted RTCP packet buffer
	 * @return True if the packet was decrypted, false otherwise
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public boolean unprotectRtcpCompound(
				@NonNull BufferExt srtcpPacketBuf,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtpSecurityException {
		if (! haveMikey) {
			throw new SrtpSecurityException("Client MIKEY not set");
		}
		final int rtcpSrRrExtendedHeaderLen = RtcpPacketHeader.HEADER_SIZE + RtcpPacketSR.INNER_HEADER_SIZE;
		final int mkiLength = (ctxMasterKeyIdentifier != 0 ? MKI_SIZE : 0);
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
		remaingEncrBuf.copyOf(srtcpPacketBuf, 0, srtcpPacketBuf.getUsed() - KeySizes.AUTH_TAG_SIZE - mkiLength);
		computeAuthTagForRtcp(remaingEncrBuf, curAuthTagBufExp);

		//
		int inpSz = srtcpPacketBuf.getUsed() - KeySizes.AUTH_TAG_SIZE;
		ByteBuffer inpBuf = ByteBuffer
				.wrap(srtcpPacketBuf.getBufPtr(), 0, inpSz)
				.order(ByteOrder.BIG_ENDIAN);

		// MKI
		if (mkiLength != 0) {
			inpBuf.position(inpSz - mkiLength);
			int tmpMki = inpBuf.getInt();
			if (tmpMki != ctxMasterKeyIdentifier) {
				throw new SrtpSecurityException("Invalid MKI in SRTCP packet");
			}
			inpSz -= mkiLength;
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
		if (ctxSrtcpLastIndex >= tmpIndexOnly) {
			throw new SrtpSecurityException("Invalid SRTCP packet index");
		}
		ctxSrtcpLastIndex = tmpIndexOnly;
		inpSz -= SRTCP_INDEX_FIELD_SIZE;

		// Sender SSRC
		inpBuf.position(RtcpPacketHeader.HEADER_SIZE);
		int tmpSenderSsrc = inpBuf.getInt();
		if (ctxSrtcpSsrc != 0 && tmpSenderSsrc != ctxSrtcpSsrc) {
			throw new SrtpSecurityException("Invalid Sender SSRC in SRTCP packet");
		}
		ctxSrtcpSsrc = tmpSenderSsrc;

		// build IV
		buildIvForRtcp(tmpIndexOnly, tmpSenderSsrc, curIvBuf);

		// decrypt RTCP payload
		remaingEncrBuf.copyOf(srtcpPacketBuf, 0, inpSz);
		decryptPayload(
				remaingEncrBuf,
				rtcpSrRrExtendedHeaderLen,
				ctxRtcpSessionEncKey,
				curIvBuf,
				outputDecryptedPacketBuf
			);
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public SrtpContext clone() {
		try {
			SrtpContext clone = (SrtpContext)super.clone();

			System.arraycopy(this.ctxMasterEncKey, 0, clone.ctxMasterEncKey, 0, this.ctxMasterEncKey.length);
			System.arraycopy(this.ctxMasterSalt, 0, clone.ctxMasterSalt, 0, this.ctxMasterSalt.length);

			System.arraycopy(this.ctxRtpSessionEncKey, 0, clone.ctxRtpSessionEncKey, 0, this.ctxRtpSessionEncKey.length);
			System.arraycopy(this.ctxRtpSessionSalt, 0, clone.ctxRtpSessionSalt, 0, this.ctxRtpSessionSalt.length);
			if (this.ctxRtpSessionAuthKey != null) {
				System.arraycopy(this.ctxRtpSessionAuthKey, 0, clone.ctxRtpSessionAuthKey, 0, this.ctxRtpSessionAuthKey.length);
			}

			System.arraycopy(this.ctxRtcpSessionEncKey, 0, clone.ctxRtcpSessionEncKey, 0, this.ctxRtcpSessionEncKey.length);
			System.arraycopy(this.ctxRtcpSessionSalt, 0, clone.ctxRtcpSessionSalt, 0, this.ctxRtcpSessionSalt.length);
			if (this.ctxRtcpSessionAuthKey != null) {
				System.arraycopy(this.ctxRtcpSessionAuthKey, 0, clone.ctxRtcpSessionAuthKey, 0, this.ctxRtcpSessionAuthKey.length);
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void encryptPayload(
				@NonNull BufferExt plainPacket,
				int pktHeaderSize,
				byte[] sessionEncKey,
				@NonNull BufferExt curIvBuf,
				@NonNull BufferExt outputEncrPacket
			) throws SrtpSecurityException {
		outputEncrPacket.copyOf(plainPacket, 0, pktHeaderSize);
		outputEncrPacket.increaseSize(plainPacket.getUsed() + 64);  // reserve some extra memory for the AuthTag etc.
		outputEncrPacket.setUsed(plainPacket.getUsed());

		try {
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			SecretKeySpec key = new SecretKeySpec(sessionEncKey, "AES");
			cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(curIvBuf.getBufPtr(), 0, curIvBuf.getUsed()));

			cipher.doFinal(
					plainPacket.getBufPtr(),
					pktHeaderSize,
					outputEncrPacket.getUsed() - pktHeaderSize,
					outputEncrPacket.getBufPtr(),
					pktHeaderSize
				);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | ShortBufferException | IllegalBlockSizeException |
				InvalidAlgorithmParameterException | BadPaddingException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	private void decryptPayload(
				@NonNull BufferExt encrPacket,
				int pktHeaderSize,
				byte[] sessionEncKey,
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
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			SecretKeySpec key = new SecretKeySpec(sessionEncKey, "AES");
			cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(curIvBuf.getBufPtr(), 0, curIvBuf.getUsed()));

			cipher.doFinal(
					encrPacket.getBufPtr(),
					pktHeaderSize,
					outputPlainPacket.getUsed() - pktHeaderSize,
					outputPlainPacket.getBufPtr(),
					pktHeaderSize
				);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | ShortBufferException | IllegalBlockSizeException |
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
		if (ctxRtpSessionAuthKey == null) {
			throw new SrtpSecurityException("RTP session auth key not set");
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(ctxRtpSessionAuthKey, "HmacSHA1"));

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
		if (ctxRtcpSessionAuthKey == null) {
			throw new SrtpSecurityException("RTP session auth key not set");
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(ctxRtcpSessionAuthKey, "HmacSHA1"));

			mac.update(encrRtcpPacket.getBufPtr(), 0, encrRtcpPacket.getUsed());

			byte[] fullTag = mac.doFinal();
			curAuthTagBuf.copyOf(fullTag);
			curAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // 80-bit tag
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildIvForRtp(long packetIndex, int ssrc, @NonNull BufferExt curIvBuf) {
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
			tmpIvBytes[i] ^= ctxRtpSessionSalt[i];
		}

		curIvBuf.copyOf(tmpIvBytes);
	}

	private void buildIvForRtcp(int packetIndex, int ssrc, @NonNull BufferExt curIvBuf) {
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
			tmpIvBytes[i] ^= ctxRtcpSessionSalt[i];
		}

		curIvBuf.copyOf(tmpIvBytes);
	}

}

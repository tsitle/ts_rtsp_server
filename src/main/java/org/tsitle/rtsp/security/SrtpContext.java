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
import java.util.HexFormat;

public class SrtpContext implements Cloneable {

	/** Number of bytes added to the RTP packet by SRTP encryption */
	public static int SRT_EXTRA_PACKET_SIZE = KeySizes.AUTH_TAG_SIZE;

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

	/** Rollover counter for RTP packets */
	private long ctxRtpRoc = 0;

	/** Packet index for RTCP packets */
	private int ctxRtcpIndex = 0;

	private boolean haveMikey = false;

	private final BufferExt cacheAuthTagBuf = new BufferExt();
	private final BufferExt cacheIvBuf = new BufferExt();
	private final byte[] cacheIvBytes = new byte[KeySizes.AES_128_KEY_SIZE];

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
		System.err.println("MIKEY: " + "b64:" + msgB64);  // @TODO

		MikeyParser.SrtpKeys keys = MikeyParser.parseKeyMgmtData(msgB64);
		System.arraycopy(keys.masterKey(), 0, ctxMasterEncKey, 0, KeySizes.AES_128_KEY_SIZE);
		System.arraycopy(keys.masterSalt(), 0, ctxMasterSalt, 0, KeySizes.SALT_SIZE);
		System.err.println("MIKEY: master Key : " + "0x" + HexFormat.of().withUpperCase().formatHex(ctxMasterEncKey));  // @TODO
		System.err.println("MIKEY: master Salt: " + "0x" + HexFormat.of().withUpperCase().formatHex(ctxMasterSalt));  // @TODO
		System.err.println("MIKEY: MKI: " + String.format("0x%04X", keys.mki()));  // @TODO

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
		 *   [RTP packet header plaintext][encrypted RTP payload][auth tag]
		 *
		 * If the RTP packet header includes a CSRC list, the CSRC list must not be encrypted.
		 * The same is true for the RTP packet header extension.
		 */

		if (hasCsrcList || hasHeaderExtension) {
			throw new SrtpSecurityException("Unsupported: RTP packet header contains CSRC list or header extension");
		}

		// SRTP packet index
		final long srtpPacketIndex = (ctxRtpRoc << 16) | seqNr;

		// build IV
		buildIvForRtp(srtpPacketIndex, ssrcId);

		// encrypt payload
		encryptPayload(rtpPacketBuf, RtpPacketContainerBase.RTP_CONT_HEADER_SIZE, ctxRtpSessionEncKey, outputEncryptedPacketBuf);

		// compute auth tag (10 bytes)
		computeAuthTagForRtp(outputEncryptedPacketBuf, srtpPacketIndex);

		// build final SRTP packet
		outputEncryptedPacketBuf.append(cacheAuthTagBuf);  // 10 bytes

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
		 * SRTCP MUST be given packets where the first part MUST be a sender report or a receiver report.
		 */

		// SRTCP index is 31 bits + 1 E-bit (encryption flag) in the MSB
		final int srtcpIndexOnly = (ctxRtcpIndex & 0x7FFFFFFF);
		final int srtcpIndexEbit = 0x80000000;
		final int srtcpIndexField = (srtcpIndexEbit | srtcpIndexOnly);

		// build IV
		buildIvForRtcp(srtcpIndexOnly, ssrcId);

		// encrypt RTCP payload
		encryptPayload(rtcpPacketBuf, rtcpSrRrExtendedHeaderLen, ctxRtcpSessionEncKey, outputEncryptedPacketBuf);

		// append SRTCP index / E-bit (4 bytes)
		byte[] tmpIndexEbitBufArr = new byte[4];
		ByteBuffer tmpIndexEbitBufObj = ByteBuffer.wrap(tmpIndexEbitBufArr).order(ByteOrder.BIG_ENDIAN);
		tmpIndexEbitBufObj.putInt(srtcpIndexField);
		outputEncryptedPacketBuf.append(tmpIndexEbitBufArr);

		// compute auth tag over: encrypted RTCP packet + SRTCP index/E-bit
		computeAuthTagForRtcp(outputEncryptedPacketBuf);

		// append auth tag (10 bytes for HMAC-SHA1-80)
		outputEncryptedPacketBuf.append(cacheAuthTagBuf);

		// advance RTCP index
		ctxRtcpIndex = ((ctxRtcpIndex + 1) & 0x7FFFFFFF);
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
				@NonNull BufferExt plainRtpPacket,
				int pktHeaderSize,
				byte[] sessionEncKey,
				@NonNull BufferExt outputEncrPacket
			) throws SrtpSecurityException {
		outputEncrPacket.copyOf(plainRtpPacket, 0, pktHeaderSize);
		outputEncrPacket.increaseSize(plainRtpPacket.getUsed() + 64);
		outputEncrPacket.setUsed(plainRtpPacket.getUsed());

		try {
			Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
			SecretKeySpec key = new SecretKeySpec(sessionEncKey, "AES");
			cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(cacheIvBuf.getBufPtr(), 0, cacheIvBuf.getUsed()));

			cipher.doFinal(
					plainRtpPacket.getBufPtr(),
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

	private void computeAuthTagForRtp(@NonNull BufferExt encrRtpPacket, long packetIndex) throws SrtpSecurityException {
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
			cacheAuthTagBuf.copyOf(fullTag);
			cacheAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // 80-bit tag
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	private void computeAuthTagForRtcp(@NonNull BufferExt encrRtcpPacket) throws SrtpSecurityException {
		if (ctxRtcpSessionAuthKey == null) {
			throw new SrtpSecurityException("RTP session auth key not set");
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(ctxRtcpSessionAuthKey, "HmacSHA1"));

			mac.update(encrRtcpPacket.getBufPtr(), 0, encrRtcpPacket.getUsed());

			byte[] fullTag = mac.doFinal();
			cacheAuthTagBuf.copyOf(fullTag);
			cacheAuthTagBuf.setUsed(KeySizes.AUTH_TAG_SIZE);  // 80-bit tag
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new SrtpSecurityException(e.getMessage());
		}
	}

	private void buildIvForRtp(long packetIndex, int ssrc) {
		Arrays.fill(cacheIvBytes, (byte)0);
		ByteBuffer buf = ByteBuffer.wrap(cacheIvBytes).order(ByteOrder.BIG_ENDIAN);
		buf.putInt(0);  // zero prefix
		buf.putInt(ssrc);
		buf.putLong(packetIndex);

		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			cacheIvBytes[i] ^= ctxRtpSessionSalt[i];
		}

		cacheIvBuf.copyOf(cacheIvBytes);
	}

	private void buildIvForRtcp(int packetIndex, int ssrc) {
		Arrays.fill(cacheIvBytes, (byte)0);
		ByteBuffer buf = ByteBuffer.wrap(cacheIvBytes).order(ByteOrder.BIG_ENDIAN);
		buf.putInt(0);  // zero prefix
		buf.putInt(ssrc);
		buf.putInt(packetIndex);
		buf.putInt(0);  // fill with zeroes

		for (int i = 0; i < KeySizes.SALT_SIZE; i++) {
			cacheIvBytes[i] ^= ctxRtcpSessionSalt[i];
		}

		cacheIvBuf.copyOf(cacheIvBytes);
	}

}

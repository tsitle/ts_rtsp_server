package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
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

	/** Key Management Data */
	private @NonNull SrtxpKmd ctxKmd = new SrtxpKmd();

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

	/** Has the Key Management Data been set? */
	private boolean haveKmd = false;

	private @NonNull CtxCipherAndMac ctxCamRtpEncr = new CtxCipherAndMac();
	private @NonNull CtxCipherAndMac ctxCamRtpDecr = new CtxCipherAndMac();
	private @NonNull CtxCipherAndMac ctxCamRtcpEncr = new CtxCipherAndMac();
	private @NonNull CtxCipherAndMac ctxCamRtcpDecr = new CtxCipherAndMac();

	public SrtxpContext() {
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Set the Key Management Data and derive the session keys from it.<br />
	 * @param kmd Key Management Data
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void setKmd(@NonNull SrtxpKmd kmd) throws SrtpSecurityException {
		if (haveKmd) {
			throw new SrtpSecurityException("KMD already set");
		}

		//
		ctxKmd = kmd.clone();
		haveKmd = true;

		//
		final Cipher cipherAesCtr = buildCipherObject();

		//
		SessionKeys tmpSessionKeys = SrtpKeyDerivation.deriveForRtp(cipherAesCtr, ctxKmd);
		setRtpSessionKeys(tmpSessionKeys);

		//
		tmpSessionKeys = SrtpKeyDerivation.deriveForRtcp(cipherAesCtr, ctxKmd);
		setRtcpSessionKeys(tmpSessionKeys);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the extra packet length for encrypted SRTP packets
	 * @return Extra packet length
	 */
	public int getSrtpExtraPacketLength() throws SrtpSecurityException {
		validateHaveKmd();
		return (KeySizes.AUTH_TAG_SIZE + ctxKmd.mki().getUsed());
	}

	/**
	 * Get the extra packet length for encrypted SRTCP packets
	 * @return Extra packet length
	 */
	public int getSrtcpExtraPacketLength() throws SrtpSecurityException {
		validateHaveKmd();
		return (KeySizes.AUTH_TAG_SIZE + SRTCP_INDEX_FIELD_SIZE + ctxKmd.mki().getUsed());
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
		validateHaveKmd();
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

		// compute Auth Tag over: encrypted RTP packet
		final BufferView encrPktView = new BufferView(outputEncryptedPacketBuf);
		computeAuthTagForRtp(
				camPtr.macObj,
				camPtr.sksMacObj,
				encrPktView,
				srtpPacketIndex,
				curAuthTagBuf
			);

		// append MKI
		if (! ctxKmd.mki().isEmpty()) {
			outputEncryptedPacketBuf.append(ctxKmd.mki());
		}

		// append Auth Tag
		outputEncryptedPacketBuf.append(curAuthTagBuf);

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
		validateHaveKmd();
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

		// SRTP packet index
		final long srtpPacketIndex = ((ctxStateRtpRocInbound << 16) | ((long)hdSeqNr & 0xFFFFL));
		if (srtpPacketIndex <= ctxStateSrtpLastIndex) {
			throw new SrtpSecurityException("Invalid SRTP packet index: " + srtpPacketIndex + " <= " + ctxStateSrtpLastIndex);
		}
		ctxStateSrtpLastIndex = srtpPacketIndex;

		//
		final BufferView encrPktView = new BufferView(srtpPacketBuf);

		// validate Auth Tag
		validateAuthTag(encrPktView, camPtr, true, srtpPacketIndex);

		//
		encrPktView.setLength(encrPktView.getInternalBeLength() - KeySizes.AUTH_TAG_SIZE);

		// validate MKI
		if (! ctxKmd.mki().isEmpty()) {
			encrPktView.setOffset(encrPktView.getLength() - ctxKmd.mki().getUsed());
			validateMki(encrPktView, "SRTP");
			encrPktView.increaseLength(-1 * ctxKmd.mki().getUsed());
		}

		// build IV
		buildIvForRtp(srtpPacketIndex, hdSsrcId, curIvBuf);

		// decrypt RTP payload
		encrPktView.setOffset(0);
		decryptPayload(
				camPtr.cipherObj,
				camPtr.sksCipherObj,
				encrPktView,
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
		validateHaveKmd();
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		if (rtcpPacketBuf.getUsed() < RTCP_PLAIN_HEADER_SIZE) {
			throw new SrtpSecurityException("Invalid RTCP packet length");
		}

		/*
		 * Encrypted SRTCP format:
		 *   [RTCP packet header plaintext][encrypted RTCP payload][E-bit|SRTCP index][MKI][auth tag]
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

		//
		final BufferView encrPktView = new BufferView(outputEncryptedPacketBuf);

		// compute Auth Tag over: encrypted RTCP packet + SRTCP index/E-bit
		computeAuthTagForRtcp(
				camPtr.macObj,
				camPtr.sksMacObj,
				encrPktView,
				curAuthTagBuf
			);

		// append MKI
		if (! ctxKmd.mki().isEmpty()) {
			outputEncryptedPacketBuf.append(ctxKmd.mki());
		}

		// append Auth Tag
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
		validateHaveKmd();
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		if (srtcpPacketBuf.getUsed() < RTCP_PLAIN_HEADER_SIZE + getSrtcpExtraPacketLength()) {
			throw new SrtpSecurityException("Invalid SRTCP packet length: " +
					srtcpPacketBuf.getUsed() + " < " + (RTCP_PLAIN_HEADER_SIZE + getSrtcpExtraPacketLength()) + " bytes");
		}

		//
		CtxCipherAndMac camPtr = buildCamObject(ctxCamRtcpDecr, ctxSessionKeysRtcp);

		//
		final BufferExt curIvBuf = new BufferExt();

		//
		final BufferView encrPktView = new BufferView(srtcpPacketBuf);

		// validate Auth Tag
		validateAuthTag(encrPktView, camPtr, false, 0L);

		//
		encrPktView.setLength(encrPktView.getInternalBeLength() - KeySizes.AUTH_TAG_SIZE);

		// validate MKI
		if (! ctxKmd.mki().isEmpty()) {
			encrPktView.setOffset(encrPktView.getLength() - ctxKmd.mki().getUsed());
			validateMki(encrPktView, "SRTCP");
			encrPktView.increaseLength(-1 * ctxKmd.mki().getUsed());
		}

		// SRTCP index is 31 bits + 1 E-bit (encryption flag) in the MSB
		encrPktView.setOffset(encrPktView.getLength() - SRTCP_INDEX_FIELD_SIZE);
		int tmpIndexField = encrPktView.getIntFromBigEndian(false);
		int tmpIndexEbit = (tmpIndexField & 0x80000000);
		int tmpIndexOnly = (tmpIndexField & 0x7FFFFFFF);
		if (tmpIndexEbit != 0x80000000) {
			throw new SrtpSecurityException("Invalid E-bit in SRTCP packet");
		}
		if (ctxStateSrtcpLastIndex >= tmpIndexOnly) {
			throw new SrtpSecurityException("Invalid SRTCP packet index");
		}
		ctxStateSrtcpLastIndex = tmpIndexOnly;
		encrPktView.increaseLength(-1 * SRTCP_INDEX_FIELD_SIZE);

		// validate Sender SSRC
		encrPktView.setOffset(RtcpPacketHeader.HEADER_SIZE);
		int tmpSenderSsrc = encrPktView.getIntFromBigEndian(false);
		if (ctxStateSrtcpSsrc != 0 && tmpSenderSsrc != ctxStateSrtcpSsrc) {
			System.err.println(srtcpPacketBuf.toHexString());
			throw new SrtpSecurityException("Invalid Sender SSRC in SRTCP packet: " +
					String.format("is=0x%08X, expected=0x%08X", tmpSenderSsrc, ctxStateSrtcpSsrc));
		}
		ctxStateSrtcpSsrc = tmpSenderSsrc;

		// build IV
		buildIvForRtcp(tmpIndexOnly, tmpSenderSsrc, curIvBuf);

		// decrypt RTCP payload
		encrPktView.setOffset(0);
		decryptPayload(
				camPtr.cipherObj,
				camPtr.sksCipherObj,
				encrPktView,
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

			if (this.ctxSessionKeysRtp != null) {
				clone.ctxSessionKeysRtp = this.ctxSessionKeysRtp.clone();
			}
			if (this.ctxSessionKeysRtcp != null) {
				clone.ctxSessionKeysRtcp = this.ctxSessionKeysRtcp.clone();
			}

			clone.ctxKmd = this.ctxKmd.clone();

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

	private void decryptPayload(
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

	private void computeAuthTagForRtp(
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

	private void computeAuthTagForRtcp(
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

	private void validateHaveKmd() throws SrtpSecurityException {
		if (! haveKmd) {
			throw new SrtpSecurityException("KMD not set");
		}
	}

	private void validateAuthTag(
				@NonNull BufferView bufView,
				@NonNull CtxCipherAndMac camPtr,
				boolean isRtpPkt,
				long srtpPacketIndex
			) throws SrtpSecurityException {
		validateHaveKmd();

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

	private void validateMki(@NonNull BufferView bufView, @NonNull String packetDesc) throws SrtpSecurityException {
		validateHaveKmd();

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

	private void validateSessionKeys(@NonNull SessionKeys sessionKeys) throws SrtpSecurityException {
		validateHaveKmd();
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
		validateHaveKmd();
		if (ctxKmd.authKeyLen() <= 0) {
			throw new SrtpSecurityException("Session Auth Key length not set");
		}
		if (sessionAuthKey.getUsed() != ctxKmd.authKeyLen()) {
			throw new SrtpSecurityException("Invalid RTP Session Auth Key length (expected " +
					ctxKmd.authKeyLen() + " bytes, got " + sessionAuthKey.getUsed() + ")");
		}
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
		haveKmd = true;
	}

	/** For Unit Tests only */
	void setKmdMasterKeyIdentifier(@NonNull BufferExt mki) {
		ctxKmd = new SrtxpKmd(ctxKmd.masterKey().clone(), ctxKmd.masterSalt().clone(), ctxKmd.authKeyLen(), mki);
		haveKmd = true;
	}

}

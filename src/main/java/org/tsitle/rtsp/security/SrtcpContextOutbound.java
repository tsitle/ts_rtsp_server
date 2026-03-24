package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Context for outbound RTCP packet encryption
 */
public class SrtcpContextOutbound extends SrtcpContextBase {

	/** For RTCP encryption: Packet index */
	private int ctxStateRtcpIndex = 0;

	/**
	 * Constructor.
	 * @param kmd Key Management Data
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public SrtcpContextOutbound(@NonNull SrtxpKmd kmd) throws SrtpSecurityException {
		super(kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------
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
		CtxCipherAndMac camPtr = buildCamObject(ctxCam, ctxSessionKeysRtcp);

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

}

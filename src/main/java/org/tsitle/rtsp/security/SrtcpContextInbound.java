package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketHeader;
import org.tsitle.rtsp.security.constants.KeySizes;

/**
 * Context for inbound SRTCP packet decryption
 */
public class SrtcpContextInbound extends SrtcpContextBase {

	/** For SRTCP decryption: Sender SSRC */
	private int ctxStateSrtcpSsrc = 0;
	/** For SRTCP decryption: Last packet index */
	private int ctxStateSrtcpLastIndex = -1;

	/**
	 * Constructor.
	 * @param kmd Key Management Data
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public SrtcpContextInbound(@NonNull SrtxpKmd kmd) throws SrtpSecurityException {
		super(kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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
		if (ctxSessionKeysRtcp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		if (srtcpPacketBuf.getUsed() < RTCP_PLAIN_HEADER_SIZE + getSrtcpExtraPacketLength()) {
			throw new SrtpSecurityException("Invalid SRTCP packet length: " +
					srtcpPacketBuf.getUsed() + " < " + (RTCP_PLAIN_HEADER_SIZE + getSrtcpExtraPacketLength()) + " bytes");
		}

		//
		CtxCipherAndMac camPtr = buildCamObject(ctxCam, ctxSessionKeysRtcp);

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

}

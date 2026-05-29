package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtxpInvalidAuthTagException;
import org.tsitle.rtsp.exceptions.SrtxpInvalidMkiException;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.packets.rtcp.RtcpPacketHeader;

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
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public SrtcpContextInbound(@NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		super(kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Decrypt an SRTCP packet buffer (containing a compound SR/RR packet) according to RFC-3711 Section 3.4
	 * @param srtcpPacketBuf SRTCP packet buffer
	 * @param outputDecryptedPacketBuf Decrypted RTCP packet buffer
	 * @throws SrtxpInvalidAuthTagException If the Authentication Tag is invalid
	 * @throws SrtxpInvalidMkiException If the MKI (Master Key Identifier) is invalid
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public void unprotectSrtcpCompound(
				@NonNull BufferExt srtcpPacketBuf,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtxpInvalidAuthTagException, SrtxpInvalidMkiException, SrtxpSecurityException {
		if (ctxSessionKeysRtcp == null) {
			throw new SrtxpSecurityException("Session Keys not set");
		}

		if (srtcpPacketBuf.getUsed() < RTCP_PLAIN_HEADER_SIZE + getSrtcpExtraPacketLength()) {
			throw new SrtxpSecurityException("Invalid SRTCP packet length: " +
					srtcpPacketBuf.getUsed() + " < " + (RTCP_PLAIN_HEADER_SIZE + getSrtcpExtraPacketLength()) + " bytes");
		}

		//
		final BufferView encrPktView = new BufferView(srtcpPacketBuf);

		// SRTCP index is 31 bits + 1 E-bit (encryption flag) in the MSB
		encrPktView.setLength(encrPktView.getInternalBeLength() - ctxKmd.authTagLen() - ctxKmd.mki().sizeBytes());
		encrPktView.setOffset(encrPktView.getLength() - SRTCP_INDEX_FIELD_SIZE);
		int tmpIndexField = encrPktView.getIntFromBigEndian(false);
		int tmpIndexEbit = (tmpIndexField & 0x80000000);
		int tmpIndexOnly = (tmpIndexField & 0x7FFFFFFF);
		if (tmpIndexEbit != 0x80000000) {
			throw new SrtxpSecurityException("Invalid E-bit in SRTCP packet");
		}
		encrPktView.setLength(encrPktView.getInternalBeLength());

		// Session keys re-derivation
		sessionKeysRederivation(false, tmpIndexOnly);

		// validate MKI
		if (! ctxKmd.mki().isEmpty()) {
			encrPktView.setOffset(encrPktView.getLength() - ctxKmd.authTagLen() - ctxKmd.mki().sizeBytes());
			validateMki(encrPktView, "SRTCP");
		}

		// validate Auth Tag
		validateAuthTag(encrPktView, false, 0);

		// remove Auth Tag
		encrPktView.setLength(encrPktView.getInternalBeLength() - ctxKmd.authTagLen());

		// remove MKI
		if (! ctxKmd.mki().isEmpty()) {
			encrPktView.increaseLength(-1 * ctxKmd.mki().sizeBytes());
		}

		// validate and remove SRTCP index
		if (ctxStateSrtcpLastIndex >= tmpIndexOnly) {
			throw new SrtxpSecurityException("Invalid SRTCP packet index");
		}
		ctxStateSrtcpLastIndex = tmpIndexOnly;
		encrPktView.increaseLength(-1 * SRTCP_INDEX_FIELD_SIZE);

		// validate Sender SSRC
		encrPktView.setOffset(RtcpPacketHeader.HEADER_SIZE);
		int tmpSenderSsrc = encrPktView.getIntFromBigEndian(false);
		if (ctxStateSrtcpSsrc != 0 && tmpSenderSsrc != ctxStateSrtcpSsrc) {
			throw new SrtxpSecurityException("Invalid Sender SSRC in SRTCP packet: " +
					String.format("is=0x%08X, expected=0x%08X", tmpSenderSsrc, ctxStateSrtcpSsrc));
		}
		ctxStateSrtcpSsrc = tmpSenderSsrc;

		// build IV
		buildIvForRtcp(tmpIndexOnly, tmpSenderSsrc, cacheIvBuf);

		// decrypt RTCP payload
		encrPktView.setOffset(0);
		decryptPayload(
				encrPktView,
				RTCP_PLAIN_HEADER_SIZE,
				cacheIvBuf,
				outputDecryptedPacketBuf
			);
	}

}

package org.tsitle.lib_xrtxp.kmd;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.packets.rtcp.RtcpPacketHeader;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidAuthTagException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidMkiException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

/**
 * Context for inbound SRTCP packet decryption
 */
public final class SrtcpContextInbound extends SrtcpContextBase {

	/** For SRTCP decryption: Sender SSRC */
	private final @NonNull RtspProtoIdXsrc ctxStateSrtcpSsrc = RtspProtoIdXsrc.ofEmpty();
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
		encrPktView.setLength(encrPktView.getInternalBeLength() - ctxKmd.authTagLen() - ctxKmd.mki().getSizeBytes());
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
			encrPktView.setOffset(encrPktView.getLength() - ctxKmd.authTagLen() - ctxKmd.mki().getSizeBytes());
			validateMki(encrPktView, "SRTCP");
		}

		// validate Auth Tag
		validateAuthTag(encrPktView, false, 0);

		// remove Auth Tag
		encrPktView.setLength(encrPktView.getInternalBeLength() - ctxKmd.authTagLen());

		// remove MKI
		if (! ctxKmd.mki().isEmpty()) {
			encrPktView.increaseLength(-1 * ctxKmd.mki().getSizeBytes());
		}

		// validate and remove SRTCP index
		if (ctxStateSrtcpLastIndex >= tmpIndexOnly) {
			throw new SrtxpSecurityException("Invalid SRTCP packet index");
		}
		ctxStateSrtcpLastIndex = tmpIndexOnly;
		encrPktView.increaseLength(-1 * SRTCP_INDEX_FIELD_SIZE);

		// validate Sender SSRC
		encrPktView.setOffset(RtcpPacketHeader.HEADER_SIZE);
		int tmpSenderSsrcInt = encrPktView.getIntFromBigEndian(false);
		if (! ctxStateSrtcpSsrc.isEmpty() && Integer.toUnsignedLong(tmpSenderSsrcInt) != ctxStateSrtcpSsrc.getId32bit().orElseThrow()) {
			throw new SrtxpSecurityException("Invalid Sender SSRC in SRTCP packet: " +
					String.format("is=0x%08X, expected=%s", tmpSenderSsrcInt, ctxStateSrtcpSsrc.toHexString(true)));
		}
		try {
			ctxStateSrtcpSsrc.setId32bit(Integer.toUnsignedLong(tmpSenderSsrcInt));
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
		}

		// build IV
		buildIvForRtcp(tmpIndexOnly, ctxStateSrtcpSsrc, cacheIvBuf);

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

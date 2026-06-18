package org.tsitle.lib_xrtxp.kmd;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.packets.srtp.RtpEncryptedPacket;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidAuthTagException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpInvalidMkiException;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;

/**
 * Context for inbound SRTP packet decryption according to RFC-3711 Section 3.1
 */
public final class SrtpContextInbound extends SrtpContextBase {

	/** For SRTP decryption: Rollover counter */
	private int ctxStateSrtpRocInbound = 0;
	/** For SRTP decryption: Last packet index */
	private long ctxStateSrtpLastIndex = -1;

	/**
	 * Constructor.
	 * @param kmd Key Management Data
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public SrtpContextInbound(@NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		super(kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Decrypt an SRTP packet
	 * @param srtpPacket SRTP packet
	 * @param outputDecryptedPacketBuf Decrypted RTP packet buffer
	 * @throws SrtxpInvalidAuthTagException If the Authentication Tag is invalid
	 * @throws SrtxpInvalidMkiException If the MKI (Master Key Identifier) is invalid
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	@SuppressWarnings("unused")
	public void unprotectSrtp(
				@NonNull RtpEncryptedPacket srtpPacket,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtxpInvalidAuthTagException, SrtxpInvalidMkiException, SrtxpSecurityException {
		unprotectSrtp(
				srtpPacket.getPacketBufferView(),
				srtpPacket.getSequenceNumber(),
				srtpPacket.getSsrcId(),
				outputDecryptedPacketBuf
			);
	}

	/**
	 * Decrypt an SRTP packet buffer
	 * @param srtpPacketBuf SRTP packet buffer
	 * @param hdSeqNr Sequence number of the RTP packet
	 * @param hdSsrcId SSRC ID of the RTP packet
	 * @param outputDecryptedPacketBuf Decrypted RTP packet buffer
	 * @throws SrtxpInvalidAuthTagException If the Authentication Tag is invalid
	 * @throws SrtxpInvalidMkiException If the MKI (Master Key Identifier) is invalid
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public void unprotectSrtp(
				@NonNull BufferExt srtpPacketBuf,
				@NonNull RtspProtoRtpSeqNr hdSeqNr,
				@NonNull RtspProtoIdXsrc hdSsrcId,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtxpInvalidAuthTagException, SrtxpInvalidMkiException, SrtxpSecurityException {
		final BufferView encrPktView = new BufferView(srtpPacketBuf);
		unprotectSrtp(encrPktView, hdSeqNr, hdSsrcId, outputDecryptedPacketBuf);
	}

	/**
	 * Decrypt an SRTP packet buffer
	 * @param srtpPacketBufView SRTP packet buffer view
	 * @param hdSeqNr Sequence number of the RTP packet
	 * @param hdSsrcId SSRC ID of the RTP packet
	 * @param outputDecryptedPacketBuf Decrypted RTP packet buffer
	 * @throws SrtxpInvalidAuthTagException If the Authentication Tag is invalid
	 * @throws SrtxpInvalidMkiException If the MKI (Master Key Identifier) is invalid
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public void unprotectSrtp(
				@NonNull BufferView srtpPacketBufView,
				@NonNull RtspProtoRtpSeqNr hdSeqNr,
				@NonNull RtspProtoIdXsrc hdSsrcId,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtxpInvalidAuthTagException, SrtxpInvalidMkiException, SrtxpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtxpSecurityException("Session Keys not set");
		}

		if (srtpPacketBufView.getLength() < RTP_PLAIN_HEADER_SIZE + getSrtpExtraPacketLength()) {
			throw new SrtxpSecurityException("Invalid SRTP packet length: " +
					srtpPacketBufView.getLength() + " < " + (RTP_PLAIN_HEADER_SIZE + getSrtpExtraPacketLength()) + " bytes");
		}

		// SRTP packet index
		long tmpSeqLong = (long)hdSeqNr.getSeqNr16bit().orElse(0);
		final long srtpPacketIndex = (((long)ctxStateSrtpRocInbound << 16) | (tmpSeqLong & 0xFFFFL));
		if (srtpPacketIndex <= ctxStateSrtpLastIndex) {
			throw new SrtxpSecurityException("Invalid SRTP packet index: " + srtpPacketIndex + " <= " + ctxStateSrtpLastIndex);
		}
		ctxStateSrtpLastIndex = srtpPacketIndex;

		// Session keys re-derivation
		sessionKeysRederivation(true, srtpPacketIndex);

		// validate MKI
		if (! ctxKmd.mki().isEmpty()) {
			srtpPacketBufView.setOffset(srtpPacketBufView.getLength() - ctxKmd.authTagLen() - ctxKmd.mki().getSizeBytes());
			validateMki(srtpPacketBufView, "SRTP");
		}

		// validate Auth Tag
		validateAuthTag(srtpPacketBufView, true, ctxStateSrtpRocInbound);

		//
		srtpPacketBufView.setLength(srtpPacketBufView.getInternalBeLength() - ctxKmd.authTagLen());

		// remove MKI
		if (! ctxKmd.mki().isEmpty()) {
			srtpPacketBufView.increaseLength(-1 * ctxKmd.mki().getSizeBytes());
		}

		// build IV
		buildIvForRtp(srtpPacketIndex, hdSsrcId, cacheIvBuf);

		// decrypt RTP payload
		srtpPacketBufView.setOffset(0);
		decryptPayload(
				srtpPacketBufView,
				RTP_PLAIN_HEADER_SIZE,
				cacheIvBuf,
				outputDecryptedPacketBuf
			);

		// update ROC if sequence wrapped
		if (tmpSeqLong == 0xFFFFL) {
			ctxStateSrtpRocInbound++;
		}
	}

}

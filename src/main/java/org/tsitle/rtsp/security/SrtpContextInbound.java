package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtxpInvalidAuthTagException;
import org.tsitle.rtsp.exceptions.SrtxpInvalidMkiException;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.packets.rtp.RtpEncryptedPacket;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

/**
 * Context for inbound SRTP packet decryption according to RFC-3711 Section 3.1
 */
public class SrtpContextInbound extends SrtpContextBase {

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
				short hdSeqNr,
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
				short hdSeqNr,
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
		final long srtpPacketIndex = (((long)ctxStateSrtpRocInbound << 16) | ((long)hdSeqNr & 0xFFFFL));
		if (srtpPacketIndex <= ctxStateSrtpLastIndex) {
			throw new SrtxpSecurityException("Invalid SRTP packet index: " + srtpPacketIndex + " <= " + ctxStateSrtpLastIndex);
		}
		ctxStateSrtpLastIndex = srtpPacketIndex;

		// Session keys re-derivation
		sessionKeysRederivation(true, srtpPacketIndex);

		// validate MKI
		if (! ctxKmd.mki().isEmpty()) {
			srtpPacketBufView.setOffset(srtpPacketBufView.getLength() - ctxKmd.authTagLen() - ctxKmd.mki().sizeBytes());
			validateMki(srtpPacketBufView, "SRTP");
		}

		// validate Auth Tag
		validateAuthTag(srtpPacketBufView, true, ctxStateSrtpRocInbound);

		//
		srtpPacketBufView.setLength(srtpPacketBufView.getInternalBeLength() - ctxKmd.authTagLen());

		// remove MKI
		if (! ctxKmd.mki().isEmpty()) {
			srtpPacketBufView.increaseLength(-1 * ctxKmd.mki().sizeBytes());
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
		if (hdSeqNr == (short)0xFFFF) {
			ctxStateSrtpRocInbound++;
		}
	}

}

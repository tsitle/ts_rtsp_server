package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.packets.rtp.RtpEncryptedPacket;
import org.tsitle.rtsp.security.constants.KeySizes;

/**
 * Context for inbound SRTP packet decryption according to RFC-3711 Section 3.1
 */
public class SrtpContextInbound extends SrtpContextBase {

	/** For SRTP decryption: Rollover counter */
	private long ctxStateRtpRocInbound = 0;
	/** For SRTP decryption: Last packet index */
	private long ctxStateSrtpLastIndex = -1;

	/**
	 * Constructor.
	 * @param kmd Key Management Data
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public SrtpContextInbound(@NonNull SrtxpKmd kmd) throws SrtpSecurityException {
		super(kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Decrypt an SRTP packet
	 * @param srtpPacket SRTP packet
	 * @param outputDecryptedPacketBuf Decrypted RTP packet buffer
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	@SuppressWarnings("unused")
	public void unprotectSrtp(
				@NonNull RtpEncryptedPacket srtpPacket,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtpSecurityException {
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
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void unprotectSrtp(
				@NonNull BufferExt srtpPacketBuf,
				short hdSeqNr,
				int hdSsrcId,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtpSecurityException {
		final BufferView encrPktView = new BufferView(srtpPacketBuf);
		unprotectSrtp(encrPktView, hdSeqNr, hdSsrcId, outputDecryptedPacketBuf);
	}

	/**
	 * Decrypt an SRTP packet buffer
	 * @param srtpPacketBufView SRTP packet buffer view
	 * @param hdSeqNr Sequence number of the RTP packet
	 * @param hdSsrcId SSRC ID of the RTP packet
	 * @param outputDecryptedPacketBuf Decrypted RTP packet buffer
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public void unprotectSrtp(
				@NonNull BufferView srtpPacketBufView,
				short hdSeqNr,
				int hdSsrcId,
				@NonNull BufferExt outputDecryptedPacketBuf
			) throws SrtpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtpSecurityException("Session Keys not set");
		}

		if (srtpPacketBufView.getLength() < RTP_PLAIN_HEADER_SIZE + getSrtpExtraPacketLength()) {
			throw new SrtpSecurityException("Invalid SRTP packet length: " +
					srtpPacketBufView.getLength() + " < " + (RTP_PLAIN_HEADER_SIZE + getSrtpExtraPacketLength()) + " bytes");
		}

		// SRTP packet index
		final long srtpPacketIndex = ((ctxStateRtpRocInbound << 16) | ((long)hdSeqNr & 0xFFFFL));
		if (srtpPacketIndex <= ctxStateSrtpLastIndex) {
			throw new SrtpSecurityException("Invalid SRTP packet index: " + srtpPacketIndex + " <= " + ctxStateSrtpLastIndex);
		}
		ctxStateSrtpLastIndex = srtpPacketIndex;

		// validate Auth Tag
		validateAuthTag(srtpPacketBufView, true, srtpPacketIndex);

		//
		srtpPacketBufView.setLength(srtpPacketBufView.getInternalBeLength() - KeySizes.AUTH_TAG_SIZE);

		// validate MKI
		if (! ctxKmd.mki().isEmpty()) {
			srtpPacketBufView.setOffset(srtpPacketBufView.getLength() - ctxKmd.mki().getUsed());
			validateMki(srtpPacketBufView, "SRTP");
			srtpPacketBufView.increaseLength(-1 * ctxKmd.mki().getUsed());
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
			ctxStateRtpRocInbound++;
		}
	}

}

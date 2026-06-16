package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

/**
 * Context for outbound RTP packet encryption
 */
public class SrtpContextOutbound extends SrtpContextBase {

	/** For RTP encryption: Rollover counter */
	private int ctxStateRtpRocOutbound = 0;

	/**
	 * Constructor.
	 * @param kmd Key Management Data
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public SrtpContextOutbound(@NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		super(kmd);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Encrypt an RTP packet buffer according to RFC-3711 Section 3.1
	 * @param rtpPacketBuf RTP packet buffer
	 * @param hasCsrcList Whether the RTP packet header contains a CSRC list
	 * @param hasHeaderExtension Whether the RTP packet header contains a header extension
	 * @param hdSeqNr Sequence number of the RTP packet
	 * @param hdSsrcId SSRC ID of the RTP packet
	 * @param outputEncryptedPacketBuf Encrypted RTP packet buffer
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public void protectRtp(
				@NonNull BufferExt rtpPacketBuf,
				boolean hasCsrcList,
				boolean hasHeaderExtension,
				short hdSeqNr,
				@NonNull RtspProtoIdXsrc hdSsrcId,
				@NonNull BufferExt outputEncryptedPacketBuf
			) throws SrtxpSecurityException {
		if (ctxSessionKeysRtp == null) {
			throw new SrtxpSecurityException("Session Keys not set");
		}

		/*
		 * Encrypted SRTP format:
		 *   [RTP packet header plaintext][encrypted RTP payload][MKI][auth tag]
		 *
		 * If the RTP packet header includes a CSRC list, the CSRC list must not be encrypted.
		 * The same is true for the RTP packet header extension.
		 */

		if (hasCsrcList || hasHeaderExtension) {
			throw new SrtxpSecurityException("Unsupported: RTP packet header contains CSRC list or header extension");
		}

		// SRTP packet index
		final long srtpPacketIndex = (((long)ctxStateRtpRocOutbound << 16) | ((long)hdSeqNr & 0xFFFFL));

		// Session keys re-derivation
		sessionKeysRederivation(true, srtpPacketIndex);

		// build IV
		buildIvForRtp(srtpPacketIndex, hdSsrcId, cacheIvBuf);

		// encrypt payload
		encryptPayload(
				rtpPacketBuf,
				RTP_PLAIN_HEADER_SIZE,
				cacheIvBuf,
				outputEncryptedPacketBuf
			);

		// compute Auth Tag over: encrypted RTP packet
		final BufferView encrPktView = new BufferView(outputEncryptedPacketBuf);
		computeAuthTagForRtp(encrPktView, ctxStateRtpRocOutbound, cacheAuthTagBuf);

		// append MKI
		if (! ctxKmd.mki().isEmpty()) {
			outputEncryptedPacketBuf.append(ctxKmd.mki().toBufferExtBigEndian());
		}

		// append Auth Tag
		outputEncryptedPacketBuf.append(cacheAuthTagBuf);

		// update ROC if sequence wrapped
		if (hdSeqNr == (short)0xFFFF) {
			ctxStateRtpRocOutbound++;
		}
	}

}

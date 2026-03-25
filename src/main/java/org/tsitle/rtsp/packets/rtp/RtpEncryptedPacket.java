package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.SrtpContextOutbound;

/**
 * RTP Encrypted Packet.
 */
public class RtpEncryptedPacket extends RtpPacketContainerBase {

	private final @NonNull SrtpContextOutbound srtpCtx;

	/**
	 * Constructor.
	 * @param payloadType RTP payload type
	 * @param plainPacket Plain RTP packet
	 * @param srtpCtx SRTxP context
	 */
	public RtpEncryptedPacket(
				@NonNull RtpPacketType payloadType,
				@NonNull RtpPacketContainerBase plainPacket,
				@NonNull SrtpContextOutbound srtpCtx
			) throws SrtpSecurityException {
		super(payloadType, plainPacket.packetBuf, true);

		this.srtpCtx = srtpCtx;

		//
		updatePacket(plainPacket);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Update the entire packet with the contents of the given plain packet.
	 * @param plainPacket Plain RTP packet
	 * @throws SrtpSecurityException If any kind of error occurred during the SRTP encryption
	 */
	public void updatePacket(@NonNull RtpPacketContainerBase plainPacket) throws SrtpSecurityException {
		packetBuf.copyOf(plainPacket.packetBuf, 0, RTP_CONT_HEADER_SIZE);

		srtpCtx.protectRtp(
				plainPacket.packetBuf,
				plainPacket.hasCsrcList(),
				plainPacket.hasHeaderExtension(),
				plainPacket.getSequenceNumber(),
				plainPacket.getSsrcId(),
				packetBuf
			);
	}

}

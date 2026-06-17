package org.tsitle.lib_xrtxp.packets.srtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.SrtpContextOutbound;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Encrypted Packet.
 */
public final class RtpEncryptedPacket extends RtpPacketContainerBase {

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
			) throws SrtxpSecurityException {
		super(payloadType, plainPacket.getPacketBufferPtr(), true);

		this.srtpCtx = srtpCtx;

		//
		updatePacket(plainPacket);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Update the entire packet with the contents of the given plain packet.
	 * @param plainPacket Plain RTP packet
	 * @throws SrtxpSecurityException If any kind of error occurred during the SRTP encryption
	 */
	public void updatePacket(@NonNull RtpPacketContainerBase plainPacket) throws SrtxpSecurityException {
		packetBuf.copyOf(plainPacket.getPacketBufferPtr(), 0, RTP_CONT_HEADER_SIZE);

		srtpCtx.protectRtp(
				plainPacket.getPacketBufferPtr(),
				plainPacket.hasCsrcList(),
				plainPacket.hasHeaderExtension(),
				plainPacket.getSequenceNumber(),
				plainPacket.getSsrcId(),
				packetBuf
			);
	}

}

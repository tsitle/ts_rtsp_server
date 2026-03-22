package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.SrtpContext;

/**
 * RTP Encrypted Packet.
 */
public class RtpEncryptedPacket extends RtpPacketContainerBase {

	/**
	 * Constructor.
	 * @param payloadType RTP payload type
	 * @param plainPacket Plain RTP packet
	 * @param srtpContext SRTP context
	 */
	public RtpEncryptedPacket(
				@NonNull RtpPacketType payloadType,
				@NonNull RtpPacketContainerBase plainPacket,
				@NonNull SrtpContext srtpContext
			) throws SrtpSecurityException {
		super(payloadType, plainPacket.packetBuf, true);

		//
		srtpContext.protectRtp(
				plainPacket.packetBuf,
				plainPacket.hasCsrcList(),
				plainPacket.hasHeaderExtension(),
				plainPacket.getSequenceNumber(),
				plainPacket.getSsrcId(),
				this.packetBuf
			);
	}

}

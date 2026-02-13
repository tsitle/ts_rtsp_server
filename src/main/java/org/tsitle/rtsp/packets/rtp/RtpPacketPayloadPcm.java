package org.tsitle.rtsp.packets.rtp;

import org.tsitle.rtsp.avdata.PcmInfo;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP Packet Payload for PCMU/LinearPCM.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3551">RFC-3551</a>
 */
public class RtpPacketPayloadPcm extends RtpPacketPayloadBase {

	private final RtpPacketType packetType;

	/**
	 * Constructor.
	 * @param packetType RTP packet type
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the PCM frame data) (24 bits)
	 * @param pcmInfo PCM info
	 * @param payloadData Payload data
	 */
	public RtpPacketPayloadPcm(RtpPacketType packetType, int fragmentOffset, PcmInfo pcmInfo, BufferExt payloadData) {
		super();

		//
		if (! packetType.isPcmAudio()) {
			throw new IllegalArgumentException("Invalid RTP packet type");
		}
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (pcmInfo == null) {
			throw new IllegalArgumentException("Cannot process this kind of PCM");
		}

		//
		this.packetType = packetType;

		// set inner main header fields
		/* there are none */

		// build the inner header bitstream
		/* there is none */

		// copy the inner payload bitstream
		this.rawInnerPayloadData.copyOf(payloadData);
	}

	/**
	 * Constructor.
	 * @param packetType RTP packet type
	 * @param rawInnerHeaderAndPayloadData Payload-specific header and payload of the RTP packet
	 */
	public RtpPacketPayloadPcm(RtpPacketType packetType, BufferExt rawInnerHeaderAndPayloadData) {
		super();

		if (! packetType.isPcmAudio()) {
			throw new IllegalArgumentException("Invalid RTP packet type");
		}

		//
		this.packetType = packetType;

		// parse inner main header fields
		/* there are none */

		// copy the inner header bitstream
		/* there is none */

		// copy the inner payload bitstream
		this.rawInnerPayloadData.copyOf(
				rawInnerHeaderAndPayloadData,
				this.rawInnerHeaderData.getUsed(),
				rawInnerHeaderAndPayloadData.getUsed() - this.rawInnerHeaderData.getUsed()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtpPacketType getPayloadType() {
		return packetType;
	}

}

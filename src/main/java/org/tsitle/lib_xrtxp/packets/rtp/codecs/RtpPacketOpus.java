package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_opus.AudioOpusInfo;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Packet Payload for Opus.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7587">RFC-7587</a>
 */
public final class RtpPacketOpus extends RtpPacketCodecBase {

	/** Size of the main payload-specific RTP header */
	public static final int INNER_HEADER_SIZE = 0;

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param opusInfo Opus info
	 * @param payloadView Payload data view
	 */
	public RtpPacketOpus(
				@NonNull ParamsContainerBase paramsBase,
				@NonNull AudioOpusInfo opusInfo,
				@NonNull BufferView payloadView
			) {
		super(RtpPacketType.A_OPUS, paramsBase);

		//
		updatePacket(paramsBase, opusInfo, payloadView);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	@SuppressWarnings("unused")
	public RtpPacketOpus(@NonNull BufferExt packetData) {
		super(RtpPacketType.A_OPUS, packetData);

		if (packetData.getUsed() <= RTP_CONT_HEADER_SIZE + 4 + 1) {  // 4^=inner header length, 1^=inner payload length
			throw new IllegalArgumentException("Invalid RTP packet size (too short)");
		}

		// determine the length of the inner header bitstream
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE;

		// parse inner main header fields
		/* there are none */
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Update the entire packet.
	 * @param paramsBase Base Container parameters
	 * @param opusInfo Opus info
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				@NonNull AudioOpusInfo opusInfo,
				@NonNull BufferView payloadView
			) {
		if (payloadView.getLength() > opusInfo.getPayloadLength()) {
			throw new IllegalArgumentException("Invalid Opus payload size");
		}

		//
		updatePacketHeader(paramsBase);

		// set inner main header fields
		/* there are none */

		// build the inner header bitstream
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE;
		/* there is none */

		// copy the inner payload bitstream
		this.packetBuf.copyFrom(
				payloadView.getInternalBaPtr(),
				payloadView.getOffset(),
				this.packetBuf.getUsed(),
				payloadView.getLength()
			);
	}

}

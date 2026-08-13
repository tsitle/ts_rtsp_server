package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_mp3.AudioMp3Info;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Packet Payload for MP3.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3551#section-4.5.13">RFC-3551 Section 4.5.13</a>
 * and <a href="https://datatracker.ietf.org/doc/html/rfc2250#section-3.5">RFC-2250 Section 3.5</a>
 */
public final class RtpPacketMp3 extends RtpPacketCodecBase {

	/** Size of the main payload-specific RTP header */
	public static final int INNER_HEADER_SIZE = 4;

	private int hdFragOffset = 0;

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the MP3 frame data) (16 bits)
	 * @param mp3Info MP3 info
	 * @param payloadView Payload data view
	 */
	public RtpPacketMp3(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				@NonNull AudioMp3Info mp3Info,
				@NonNull BufferView payloadView
			) {
		super(RtpPacketType.A_MP3, paramsBase);

		//
		updatePacket(paramsBase, fragmentOffset, mp3Info, payloadView);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	@SuppressWarnings("unused")
	public RtpPacketMp3(@NonNull BufferExt packetData) {
		super(RtpPacketType.A_MP3, packetData);

		if (packetData.getUsed() <= RTP_CONT_HEADER_SIZE + INNER_HEADER_SIZE + 1) {  // 1^=inner payload length
			throw new IllegalArgumentException("Invalid RTP packet size (too short)");
		}

		// determine the length of the inner header bitstream
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE;

		// parse inner main header fields
		BitReaderHelper brh = new BitReaderHelper(packetData, RTP_CONT_HEADER_SIZE);
		try {
			int tmpMbz = brh.readBits(16);
			if (tmpMbz != 0) {
				throw new IllegalArgumentException("Invalid RTP packet (MBZ field != 0)");
			}
			this.hdFragOffset = brh.readBits(16);
		} catch (BitReaderEosException e) {
			throw new RuntimeException();  // this should never happen
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Update the entire packet.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the MP3 frame data) (16 bits)
	 * @param mp3Info MP3 info
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				@NonNull AudioMp3Info mp3Info,
				@NonNull BufferView payloadView
			) {
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (payloadView.getLength() > mp3Info.getPayloadLength()) {
			throw new IllegalArgumentException("Invalid MP3 payload size");
		}

		//
		updatePacketHeader(paramsBase);

		// set inner main header fields
		this.hdFragOffset = fragmentOffset;

		// build the inner header bitstream
		byte[] tmpRtpXxxHeader = buildRawInnerHeaderFromFields();
		this.payloadSpecHeaderSize = tmpRtpXxxHeader.length;
		this.packetBuf.append(tmpRtpXxxHeader);

		// copy the inner payload bitstream
		this.packetBuf.copyFrom(
				payloadView.getInternalBaPtr(),
				payloadView.getOffset(),
				this.packetBuf.getUsed(),
				payloadView.getLength()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private byte[] buildRawInnerHeaderFromFields() {
		final byte[] resA = new byte[INNER_HEADER_SIZE];

		resA[0] = 0x00;
		resA[1] = 0x00;
		resA[2] = (byte)((hdFragOffset >> 8) & 0xFF);
		resA[3] = (byte)(hdFragOffset & 0xFF);

		return resA;
	}

}

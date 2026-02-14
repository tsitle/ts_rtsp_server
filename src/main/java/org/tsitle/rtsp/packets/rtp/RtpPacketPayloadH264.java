package org.tsitle.rtsp.packets.rtp;

import org.tsitle.rtsp.avdata.H264Info;
import org.tsitle.rtsp.avdata.H264Parser;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP Packet Payload for H264.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7798">RFC-7798</a>
 */
public class RtpPacketPayloadH264 extends RtpPacketPayloadBase {

	private enum H264PayloadType {
		/** RTP/H264 Payload Type: Aggregation Packet */
		AP(48),
		/** RTP/H264 Payload Type: Fragmentation Unit */
		FU(49),
		/** RTP/H264 Payload Type: PACI */
		PACI(50),
		/** Unknown type */
		UNKNOWN(0xFF);

		private final byte value;

		H264PayloadType(int value) {
			this.value = (byte)value;
		}
		public byte getValue() {
			return value;
		}
		public static H264PayloadType of(byte value) {
			for (H264PayloadType type : H264PayloadType.values()) {
				if (type.getValue() == value) {
					return type;
				}
			}
			return UNKNOWN;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Payload Header: RTP/H264 Payload Type as byte (6 bits) */
	private final byte hdPayTypeBy;
	/** Payload Header: RTP/H264 Payload Type as enum */
	private final H264PayloadType hdPayTypeEn;
	/** Payload Header: Layer ID, required to be equal to zero (6 bits) */
	private final byte hdPayNuhLayerId;
	/** Payload Header: Temporal identifier of the NAL unit plus 1, required to be unequal to zero (3 bits) */
	private final byte hdPayNuhTemporalIdPlus1;
	/** FU Header: S bit, needs to be zero for the first packet and one for later packets (1 bit) */
	private final boolean hdFuS;
	/** FU Header: E bit, needs to be one for the last packet and zero for all other packets (1 bit) */
	private final boolean hdFuE;
	/** FU Header: NAL Unit Type, must be equal to the field Type of the NAL Unit (6 bits) */
	private final byte hdFuTypeBy;

	/**
	 * Constructor.
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H264 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment?
	 * @param h264Info H264 info
	 * @param payloadData Payload data
	 */
	public RtpPacketPayloadH264(int fragmentOffset, boolean isLastFragment, H264Info h264Info, BufferExt payloadData) {
		super();

		//
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (h264Info == null || h264Info.nuhLayerId != 0 || h264Info.nuhTemporalIdPlus1 == 0) {
			throw new IllegalArgumentException("Cannot process this kind of H264");
		}

		/*
		 * Transmission Modes:
		 *   - SRST: a Single RTP stream on a Single media Transport
		 *   - MRST: Multiple RTP streams over a Single media Transport
		 *   - MRMT: Multiple RTP streams on Multiple media Transports
		 */

		// set inner main header fields
		this.hdPayTypeBy = (fragmentOffset == 0 && isLastFragment ? h264Info.nalUnitTypeBy : H264PayloadType.FU.getValue());
		this.hdPayTypeEn = H264PayloadType.of(this.hdPayTypeBy);
		this.hdPayNuhLayerId = h264Info.nuhLayerId;
		this.hdPayNuhTemporalIdPlus1 = h264Info.nuhTemporalIdPlus1;
		this.hdFuS = (this.hdPayTypeEn == H264PayloadType.FU && fragmentOffset == 0);
		this.hdFuE = (this.hdPayTypeEn == H264PayloadType.FU && isLastFragment);
		this.hdFuTypeBy = (this.hdPayTypeEn == H264PayloadType.FU ? h264Info.nalUnitTypeBy : 0x00);

		// build the inner header bitstream (main header + optional FU header)
		byte[] tmpRtpXxxHeader = buildRawInnerHeaderFromFields();
		this.rawInnerHeaderData.copyOf(tmpRtpXxxHeader);

		/*
		 * Copy the inner payload bitstream:
		 *   - FU packets: The NAL Unit header of the fragmented NAL Unit is not included as such in the FU payload.
		 *   - Single NAL Unit packets: NAL Unit payload data (the NAL Unit excluding its NAL Unit header)
		 *     of the contained NAL unit.
		 * So for all Single NAL Unit packets and the first packet of a fragmented NAL Unit,
		 * we need to skip the NAL Unit header.
		 */
		int skip = (fragmentOffset == 0 ? H264Parser.NAL_UNIT_HEADER_SIZE : 0);
		this.rawInnerPayloadData.copyOf(
				payloadData,
				skip,
				payloadData.getUsed() - skip
			);
	}

	/**
	 * Constructor.
	 * @param rawInnerHeaderAndPayloadData Payload-specific header and payload of the RTP packet
	 */
	public RtpPacketPayloadH264(BufferExt rawInnerHeaderAndPayloadData) {
		super();

		if (rawInnerHeaderAndPayloadData.getUsed() < H264Parser.NAL_UNIT_HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		// parse inner main header fields
		int offs = 0;
		this.hdPayTypeBy = (byte)( ((rawInnerHeaderAndPayloadData.get(offs) & 0x7E) >>> 1) & 0x3F);
		this.hdPayTypeEn = H264PayloadType.of(this.hdPayTypeBy);
		this.hdPayNuhLayerId = (byte)( ( ((rawInnerHeaderAndPayloadData.get(offs++) & 0x01) << 5) |
				((rawInnerHeaderAndPayloadData.get(offs) & 0xF8) >> 3) ) & 0x3F);
		this.hdPayNuhTemporalIdPlus1 = (byte)(rawInnerHeaderAndPayloadData.get(offs++) & 0x07);

		final int additionalHeaderSize = (hdPayTypeEn == H264PayloadType.FU ? 1 : 0);

		if (additionalHeaderSize == 0) {
			this.hdFuS = false;
			this.hdFuE = false;
			this.hdFuTypeBy = 0x00;
		} else {
			if (rawInnerHeaderAndPayloadData.getUsed() < H264Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize) {
				throw new IllegalArgumentException("Invalid RTP packet size");
			}
			byte tmpByte = rawInnerHeaderAndPayloadData.get(offs);
			this.hdFuS = ((tmpByte & (byte)0x80) != 0);
			this.hdFuE = ((tmpByte & (byte)0x40) != 0);
			this.hdFuTypeBy = (byte)(tmpByte & (byte)0x3F);
		}

		// copy the inner header bitstream (main header + optional FU header)
		this.rawInnerHeaderData.copyOf(
				rawInnerHeaderAndPayloadData,
				0,
				H264Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize
			);

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
		return RtpPacketType.V_H264;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName()).append(" [");
		sb.append("PayType: ").append(hdPayTypeEn);
		if (hdPayTypeEn == H264PayloadType.UNKNOWN) {
			sb.append(" (o=").append(Integer.toUnsignedString(hdPayTypeBy)).append(")");
		}
		sb.append(", PayNuhLayerId: ").append(Integer.toUnsignedString(hdPayNuhLayerId));
		sb.append(", PayNuhTemporalIdPlus1: ").append(Integer.toUnsignedString(hdPayNuhTemporalIdPlus1));
		if (hdPayTypeEn == H264PayloadType.FU) {
			sb.append(", FuS: ").append(hdFuS);
			sb.append(", FuE: ").append(hdFuE);
			sb.append(", FuType: ").append(Integer.toUnsignedString(hdFuTypeBy));
		}
		sb.append(", innerHeaderSz: ").append(Integer.toUnsignedString(rawInnerHeaderData.getUsed()));
		sb.append(", innerPayloadSz: ").append(Integer.toUnsignedString(rawInnerPayloadData.getUsed()));
		sb.append("]");
		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private byte[] buildRawInnerHeaderFromFields() {
		final int additionalHeaderSize = (hdPayTypeEn == H264PayloadType.FU ? 1 : 0);
		final byte[] resA = new byte[H264Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize];

		resA[0] = (byte)( ( ((hdPayTypeBy & 0x3F) << 1) & 0x7E) | (((hdPayNuhLayerId & 0x3F) >> 5) & 0x01) );
		resA[1] = (byte)( ( ((hdPayNuhLayerId & 0x1F) << 5) & 0xF8) | (hdPayNuhTemporalIdPlus1 & 0x07) );
		if (additionalHeaderSize > 0) {
			resA[2] = (byte)((hdFuS ? 0x80 : 0x00) | (hdFuE ? 0x40 : 0x00) | (hdFuTypeBy & 0x3F));
		}

		return resA;
	}

}

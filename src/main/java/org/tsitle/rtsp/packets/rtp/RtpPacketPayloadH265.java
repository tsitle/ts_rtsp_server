package org.tsitle.rtsp.packets.rtp;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.avdata.H265Info;
import org.tsitle.rtsp.avdata.H265Parser;

/**
 * RTP Packet Payload for H265.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7798">RFC-7798</a>
 */
public class RtpPacketPayloadH265 extends RtpPacketPayloadBase {

	private enum H265PayloadType {
		/** RTP/H265 Payload Type: Aggregation Packet */
		AP(48),
		/** RTP/H265 Payload Type: Fragmentation Unit */
		FU(49),
		/** RTP/H265 Payload Type: PACI */
		PACI(50),
		/** Unknown type */
		UNKNOWN(0xFF);

		private final byte value;

		H265PayloadType(int value) {
			this.value = (byte)value;
		}
		public byte getValue() {
			return value;
		}
		public static H265PayloadType of(byte value) {
			for (H265PayloadType type : H265PayloadType.values()) {
				if (type.getValue() == value) {
					return type;
				}
			}
			return UNKNOWN;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Payload Header: RTP/H265 Payload Type as byte (6 bits) */
	private final byte hdPayTypeBy;
	/** Payload Header: RTP/H265 Payload Type as enum */
	private final H265PayloadType hdPayTypeEn;
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
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H265 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment?
	 * @param h265Info H265 info
	 * @param payloadData Payload data
	 */
	public RtpPacketPayloadH265(
				int fragmentOffset,
				boolean isLastFragment,
				H265Info h265Info,
				BufferExt payloadData
			) {
		super();

		//
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (h265Info == null || h265Info.nuhLayerId != 0 || h265Info.nuhTemporalIdPlus1 == 0) {
			throw new IllegalArgumentException("Cannot process this kind of H265");
		}

		/*
		 * Transmission Modes:
		 *   - SRST: a Single RTP stream on a Single media Transport
		 *   - MRST: Multiple RTP streams over a Single media Transport
		 *   - MRMT: Multiple RTP streams on Multiple media Transports
		 */

		final boolean isFragmented = (fragmentOffset != 0 || ! isLastFragment);

		// set inner main header fields
		this.hdPayTypeBy = (isFragmented ? H265PayloadType.FU.getValue() : h265Info.nalUnitTypeBy);
		this.hdPayTypeEn = H265PayloadType.of(this.hdPayTypeBy);
		this.hdPayNuhLayerId = h265Info.nuhLayerId;
		this.hdPayNuhTemporalIdPlus1 = h265Info.nuhTemporalIdPlus1;
		this.hdFuS = (this.hdPayTypeEn == H265PayloadType.FU && fragmentOffset == 0);
		this.hdFuE = (this.hdPayTypeEn == H265PayloadType.FU && isLastFragment);
		this.hdFuTypeBy = (this.hdPayTypeEn == H265PayloadType.FU ? h265Info.nalUnitTypeBy : 0x00);

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
		int skip = (! isFragmented || fragmentOffset == 0 ? H265Parser.NAL_UNIT_HEADER_SIZE : 0);
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
	public RtpPacketPayloadH265(BufferExt rawInnerHeaderAndPayloadData) {
		super();

		if (rawInnerHeaderAndPayloadData.getUsed() < H265Parser.NAL_UNIT_HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		// parse inner main header fields
		int offs = 0;
		this.hdPayTypeBy = (byte)( ((rawInnerHeaderAndPayloadData.get(offs) & 0x7E) >>> 1) & 0x3F);
		this.hdPayTypeEn = H265PayloadType.of(this.hdPayTypeBy);
		this.hdPayNuhLayerId = (byte)( ( ((rawInnerHeaderAndPayloadData.get(offs++) & 0x01) << 5) |
				((rawInnerHeaderAndPayloadData.get(offs) & 0xF8) >> 3) ) & 0x3F);
		this.hdPayNuhTemporalIdPlus1 = (byte)(rawInnerHeaderAndPayloadData.get(offs++) & 0x07);

		final int additionalHeaderSize = (hdPayTypeEn == H265PayloadType.FU ? 1 : 0);

		if (additionalHeaderSize == 0) {
			this.hdFuS = false;
			this.hdFuE = false;
			this.hdFuTypeBy = 0x00;
		} else {
			if (rawInnerHeaderAndPayloadData.getUsed() < H265Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize) {
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
				H265Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize
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
		return RtpPacketType.V_H265;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName()).append(" [");
		sb.append("PayType: ").append(hdPayTypeEn);
		if (hdPayTypeEn == H265PayloadType.UNKNOWN) {
			sb.append(" (o=").append(Integer.toUnsignedString(hdPayTypeBy)).append(")");
		}
		sb.append(", PayNuhLayerId: ").append(Integer.toUnsignedString(hdPayNuhLayerId));
		sb.append(", PayNuhTemporalIdPlus1: ").append(Integer.toUnsignedString(hdPayNuhTemporalIdPlus1));
		if (hdPayTypeEn == H265PayloadType.FU) {
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
		final int additionalHeaderSize = (hdPayTypeEn == H265PayloadType.FU ? 1 : 0);
		final byte[] resA = new byte[H265Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize];

		resA[0] = (byte)( ( ((hdPayTypeBy & 0x3F) << 1) & 0x7E) | (((hdPayNuhLayerId & 0x3F) >> 5) & 0x01) );
		resA[1] = (byte)( ( ((hdPayNuhLayerId & 0x1F) << 5) & 0xF8) | (hdPayNuhTemporalIdPlus1 & 0x07) );
		if (additionalHeaderSize > 0) {
			resA[2] = (byte)((hdFuS ? 0x80 : 0x00) | (hdFuE ? 0x40 : 0x00) | (hdFuTypeBy & 0x3F));
		}

		return resA;
	}

}

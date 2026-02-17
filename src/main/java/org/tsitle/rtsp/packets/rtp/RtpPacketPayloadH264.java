package org.tsitle.rtsp.packets.rtp;

import org.tsitle.rtsp.avdata.H264Info;
import org.tsitle.rtsp.avdata.H264Parser;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP Packet Payload for H264.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3984">RFC-3984</a>
 */
public class RtpPacketPayloadH264 extends RtpPacketPayloadBase {

	private enum H264PayloadType {
		/** RTP/H264 Payload Type: Single-time aggregation packet (STAP-A), RFC-3984 Section 5.7.1 */
		STAP_A(24),
		/** RTP/H264 Payload Type: Single-time aggregation packet (STAP-B), RFC-3984 Section 5.7.1 */
		STAP_B(25),
		/** RTP/H264 Payload Type: Multi-time aggregation packet (MTAP16), RFC-3984 Section 5.7.2 */
		MTAP16(26),
		/** RTP/H264 Payload Type: Multi-time aggregation packet (MTAP24), RFC-3984 Section 5.7.2 */
		MTAP24(27),
		/** RTP/H264 Payload Type: Fragmentation Unit (FU-A), RFC-3984 Section 5.8 */
		FU_A(28),
		/** RTP/H264 Payload Type: Fragmentation Unit (FU-B), RFC-3984 Section 5.8 */
		FU_B(29),
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
	/** Payload Header: Ref IDC - indicates importance: 0=not used for reference, >0=used for reference (2 bits) */
	private final byte hdPayNuhRefIdc;
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
	public RtpPacketPayloadH264(
				int fragmentOffset,
				boolean isLastFragment,
				H264Info h264Info,
				BufferExt payloadData
			) {
		super();

		//
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (h264Info == null) {
			throw new IllegalArgumentException("Cannot process this kind of H264");
		}

		/*
		 * Transmission Modes:
		 *   - SRST: a Single RTP stream on a Single media Transport
		 *   - MRST: Multiple RTP streams over a Single media Transport
		 *   - MRMT: Multiple RTP streams on Multiple media Transports
		 */

		final boolean isFragmented = (fragmentOffset != 0 || ! isLastFragment);

		// set inner main header fields
		this.hdPayTypeBy = (isFragmented ? H264PayloadType.FU_A.getValue() : h264Info.nalUnitTypeBy);
		this.hdPayTypeEn = H264PayloadType.of(this.hdPayTypeBy);
		this.hdPayNuhRefIdc = h264Info.nuhRefIdc;
		this.hdFuS = (this.hdPayTypeEn == H264PayloadType.FU_A && fragmentOffset == 0);
		this.hdFuE = (this.hdPayTypeEn == H264PayloadType.FU_A && isLastFragment);
		this.hdFuTypeBy = (this.hdPayTypeEn == H264PayloadType.FU_A ? h264Info.nalUnitTypeBy : 0x00);

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
		int skip = (! isFragmented || fragmentOffset == 0 ? H264Parser.NAL_UNIT_HEADER_SIZE : 0);
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
	@SuppressWarnings("unused")
	public RtpPacketPayloadH264(BufferExt rawInnerHeaderAndPayloadData) {
		super();

		if (rawInnerHeaderAndPayloadData.getUsed() < H264Parser.NAL_UNIT_HEADER_SIZE) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		// parse inner main header fields
		int offs = 0;
		this.hdPayNuhRefIdc = (byte)( ((rawInnerHeaderAndPayloadData.get(offs) & 0x60) >>> 5) & 0x03);
		this.hdPayTypeBy = (byte)(rawInnerHeaderAndPayloadData.get(offs) & 0x1F);
		this.hdPayTypeEn = H264PayloadType.of(this.hdPayTypeBy);

		final int additionalHeaderSize = (hdPayTypeEn == H264PayloadType.FU_A ? 1 : 0);

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
		sb.append(", PayNuhRefIdc: ").append(Integer.toUnsignedString(hdPayNuhRefIdc));
		if (hdPayTypeEn == H264PayloadType.FU_A) {
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
		final int additionalHeaderSize = (hdPayTypeEn == H264PayloadType.FU_A ? 1 : 0);
		final byte[] resA = new byte[H264Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize];

		resA[0] = (byte)( ( ((hdPayNuhRefIdc & 0x03) << 5) & 0x60) | (hdPayTypeBy & 0x1F) );
		if (additionalHeaderSize > 0) {
			resA[1] = (byte)((hdFuS ? 0x80 : 0x00) | (hdFuE ? 0x40 : 0x00) | (hdFuTypeBy & 0x3F));
		}

		return resA;
	}

}

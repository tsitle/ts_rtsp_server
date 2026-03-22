package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.VideoH265Info;
import org.tsitle.rtsp.avdata.VideoH265Parser;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP Packet Payload for H265.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7798">RFC-7798</a>
 */
public final class RtpPacketH265 extends RtpPacketCodecBase {

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

	/** Minimum size of the main payload-specific RTP header */
	public static final int INNER_HEADER_SIZE_MIN = VideoH265Parser.NAL_UNIT_HEADER_SIZE;
	/** Maximum size of the main payload-specific RTP header */
	@SuppressWarnings("unused")
	public static final int INNER_HEADER_SIZE_MAX = INNER_HEADER_SIZE_MIN + 1;

	/** Payload Header: RTP/H265 Payload Type as byte (6 bits) */
	private final byte hdInnPayTypeBy;
	/** Payload Header: RTP/H265 Payload Type as enum */
	private final @NonNull H265PayloadType hdInnPayTypeEn;
	/** Payload Header: Layer ID, required to be equal to zero (6 bits) */
	private final byte hdInnPayNuhLayerId;
	/** Payload Header: Temporal identifier of the NAL unit plus 1, required to be unequal to zero (3 bits) */
	private final byte hdInnPayNuhTemporalIdPlus1;
	/** FU Header: S bit, needs to be zero for the first packet and one for later packets (1 bit) */
	private final boolean hdInnFuS;
	/** FU Header: E bit, needs to be one for the last packet and zero for all other packets (1 bit) */
	private final boolean hdInnFuE;
	/** FU Header: NAL Unit Type, must be equal to the field Type of the NAL Unit (6 bits) */
	private final byte hdInnFuTypeBy;

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H265 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param h265Info H265 info
	 * @param payloadData Payload data
	 */
	public RtpPacketH265(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				boolean isLastFragment,
				@NonNull VideoH265Info h265Info,
				@NonNull BufferExt payloadData
			) {
		super(RtpPacketType.V_H265, paramsBase);

		//
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (h265Info.nuhLayerId != 0 || h265Info.nuhTemporalIdPlus1 == 0) {
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
		this.hdInnPayTypeBy = (isFragmented ? H265PayloadType.FU.getValue() : h265Info.nalUnitTypeBy);
		this.hdInnPayTypeEn = H265PayloadType.of(this.hdInnPayTypeBy);
		this.hdInnPayNuhLayerId = h265Info.nuhLayerId;
		this.hdInnPayNuhTemporalIdPlus1 = h265Info.nuhTemporalIdPlus1;
		this.hdInnFuS = (this.hdInnPayTypeEn == H265PayloadType.FU && fragmentOffset == 0);
		this.hdInnFuE = (this.hdInnPayTypeEn == H265PayloadType.FU && isLastFragment);
		this.hdInnFuTypeBy = (this.hdInnPayTypeEn == H265PayloadType.FU ? h265Info.nalUnitTypeBy : 0x00);

		// build the inner header bitstream (main header + optional FU header)
		byte[] tmpRtpXxxHeader = buildRawInnerHeaderFromFields();
		this.payloadSpecHeaderSize = tmpRtpXxxHeader.length;
		this.packetBuf.append(tmpRtpXxxHeader);

		/*
		 * Copy the inner payload bitstream:
		 *   - FU packets: The NAL Unit header of the fragmented NAL Unit is not included as such in the FU payload.
		 *   - Single NAL Unit packets: NAL Unit payload data (the NAL Unit excluding its NAL Unit header)
		 *     of the contained NAL unit.
		 * So for all Single NAL Unit packets and the first packet of a fragmented NAL Unit,
		 * we need to skip the NAL Unit header.
		 */
		int skip = (! isFragmented || fragmentOffset == 0 ? VideoH265Parser.NAL_UNIT_HEADER_SIZE : 0);
		this.packetBuf.copyFrom(
				payloadData,
				skip,
				this.packetBuf.getUsed(),
				payloadData.getUsed() - skip
			);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	@SuppressWarnings("unused")
	public RtpPacketH265(@NonNull BufferExt packetData) {
		super(RtpPacketType.V_H265, packetData);

		if (packetData.getUsed() < RTP_CONT_HEADER_SIZE + INNER_HEADER_SIZE_MIN) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		// parse inner main header fields
		int offs = RTP_CONT_HEADER_SIZE;
		this.hdInnPayTypeBy = (byte)( ((packetData.get(offs) & 0x7E) >>> 1) & 0x3F);
		this.hdInnPayTypeEn = H265PayloadType.of(this.hdInnPayTypeBy);
		this.hdInnPayNuhLayerId = (byte)( ( ((packetData.get(offs++) & 0x01) << 5) |
				((packetData.get(offs) & 0xF8) >> 3) ) & 0x3F);
		this.hdInnPayNuhTemporalIdPlus1 = (byte)(packetData.get(offs++) & 0x07);

		// determine the length of the inner header bitstream (main header + optional QT header)
		final int additionalHeaderSize = (hdInnPayTypeEn == H265PayloadType.FU ? 1 : 0);
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE_MIN + additionalHeaderSize;

		if (additionalHeaderSize == 0) {
			this.hdInnFuS = false;
			this.hdInnFuE = false;
			this.hdInnFuTypeBy = 0x00;
		} else {
			if (packetData.getUsed() < RTP_CONT_HEADER_SIZE + this.payloadSpecHeaderSize) {
				throw new IllegalArgumentException("Invalid RTP packet size");
			}
			byte tmpByte = packetData.get(offs);
			this.hdInnFuS = ((tmpByte & (byte)0x80) != 0);
			this.hdInnFuE = ((tmpByte & (byte)0x40) != 0);
			this.hdInnFuTypeBy = (byte)(tmpByte & (byte)0x3F);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName()).append(" [");
		sb.append(super.toString(true));
		sb.append(", H265PayType: ").append(hdInnPayTypeEn);
		if (hdInnPayTypeEn == H265PayloadType.UNKNOWN) {
			sb.append(" (o=").append(Integer.toUnsignedString(hdInnPayTypeBy)).append(")");
		}
		sb.append(", PayNuhLayerId: ").append(Integer.toUnsignedString(hdInnPayNuhLayerId));
		sb.append(", PayNuhTemporalIdPlus1: ").append(Integer.toUnsignedString(hdInnPayNuhTemporalIdPlus1));
		if (hdInnPayTypeEn == H265PayloadType.FU) {
			sb.append(", FuS: ").append(hdInnFuS);
			sb.append(", FuE: ").append(hdInnFuE);
			sb.append(", FuType: ").append(Integer.toUnsignedString(hdInnFuTypeBy));
		}
		sb.append("]");
		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private byte[] buildRawInnerHeaderFromFields() {
		final int additionalHeaderSize = (hdInnPayTypeEn == H265PayloadType.FU ? 1 : 0);
		final byte[] resA = new byte[VideoH265Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize];

		resA[0] = (byte)( ( ((hdInnPayTypeBy & 0x3F) << 1) & 0x7E) | (((hdInnPayNuhLayerId & 0x3F) >> 5) & 0x01) );
		resA[1] = (byte)( ( ((hdInnPayNuhLayerId & 0x1F) << 5) & 0xF8) | (hdInnPayNuhTemporalIdPlus1 & 0x07) );
		if (additionalHeaderSize > 0) {
			resA[2] = (byte)((hdInnFuS ? 0x80 : 0x00) | (hdInnFuE ? 0x40 : 0x00) | (hdInnFuTypeBy & 0x3F));
		}

		return resA;
	}

}

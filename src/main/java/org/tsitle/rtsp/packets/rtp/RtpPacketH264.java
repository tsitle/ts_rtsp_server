package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.VideoH264Info;
import org.tsitle.rtsp.avdata.VideoH264Parser;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * RTP Packet Payload for H264.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3984">RFC-3984</a>
 */
public final class RtpPacketH264 extends RtpPacketCodecBase {

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
		public static @NonNull H264PayloadType of(byte value) {
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

	/** Minimum size of the main payload-specific RTP header */
	public static final int INNER_HEADER_SIZE_MIN = VideoH264Parser.NAL_UNIT_HEADER_SIZE;
	/** Maximum size of the main payload-specific RTP header */
	@SuppressWarnings("unused")
	public static final int INNER_HEADER_SIZE_MAX = INNER_HEADER_SIZE_MIN + 1;

	/** Payload Header: RTP/H264 Payload Type as byte (6 bits) */
	private byte hdInnPayTypeBy;
	/** Payload Header: RTP/H264 Payload Type as enum */
	private @NonNull H264PayloadType hdInnPayTypeEn;
	/** Payload Header: Ref IDC - indicates importance: 0=not used for reference, >0=used for reference (2 bits) */
	private byte hdInnPayNuhRefIdc;
	/** FU Header: S bit, needs to be zero for the first packet and one for later packets (1 bit) */
	private boolean hdInnFuS;
	/** FU Header: E bit, needs to be one for the last packet and zero for all other packets (1 bit) */
	private boolean hdInnFuE;
	/** FU Header: NAL Unit Type, must be equal to the field Type of the NAL Unit (6 bits) */
	private byte hdInnFuTypeBy;

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H264 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param h264Info H264 info
	 * @param payloadData Payload data
	 */
	public RtpPacketH264(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				boolean isLastFragment,
				@NonNull VideoH264Info h264Info,
				@NonNull BufferExt payloadData
			) {
		super(RtpPacketType.V_H264, paramsBase);

		//
		this.hdInnPayTypeEn = H264PayloadType.UNKNOWN;
		updatePacket(paramsBase, fragmentOffset, isLastFragment, h264Info, payloadData);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	@SuppressWarnings("unused")
	public RtpPacketH264(@NonNull BufferExt packetData) {
		super(RtpPacketType.V_H264, packetData);

		if (packetData.getUsed() < RTP_CONT_HEADER_SIZE + INNER_HEADER_SIZE_MIN) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		// parse inner main header fields
		int offs = RTP_CONT_HEADER_SIZE;
		this.hdInnPayNuhRefIdc = (byte)( ((packetData.get(offs) & 0x60) >>> 5) & 0x03);
		this.hdInnPayTypeBy = (byte)(packetData.get(offs) & 0x1F);
		this.hdInnPayTypeEn = H264PayloadType.of(this.hdInnPayTypeBy);

		// determine the length of the inner header bitstream (main header + optional QT header)
		final int additionalHeaderSize = (hdInnPayTypeEn == H264PayloadType.FU_A ? 1 : 0);
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE_MIN + additionalHeaderSize;

		// read optional FU header fields
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

	/**
	 * Update the entire packet.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H264 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param h264Info H264 info
	 * @param payloadData Payload data
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				boolean isLastFragment,
				@NonNull VideoH264Info h264Info,
				@NonNull BufferExt payloadData
			) {
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}

		//
		updatePacketHeader(paramsBase);

		/*
		 * Transmission Modes:
		 *   - SRST: a Single RTP stream on a Single media Transport
		 *   - MRST: Multiple RTP streams over a Single media Transport
		 *   - MRMT: Multiple RTP streams on Multiple media Transports
		 */

		final boolean isFragmented = (fragmentOffset != 0 || ! isLastFragment);

		// set inner main header fields
		this.hdInnPayTypeBy = (isFragmented ? H264PayloadType.FU_A.getValue() : h264Info.nalUnitTypeBy);
		this.hdInnPayTypeEn = H264PayloadType.of(this.hdInnPayTypeBy);
		this.hdInnPayNuhRefIdc = h264Info.nuhRefIdc;
		this.hdInnFuS = (this.hdInnPayTypeEn == H264PayloadType.FU_A && fragmentOffset == 0);
		this.hdInnFuE = (this.hdInnPayTypeEn == H264PayloadType.FU_A && isLastFragment);
		this.hdInnFuTypeBy = (this.hdInnPayTypeEn == H264PayloadType.FU_A ? h264Info.nalUnitTypeBy : 0x00);

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
		int skip = (! isFragmented || fragmentOffset == 0 ? VideoH264Parser.NAL_UNIT_HEADER_SIZE : 0);
		this.packetBuf.copyFrom(
				payloadData,
				skip,
				this.packetBuf.getUsed(),
				payloadData.getUsed() - skip
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName()).append(" [");
		sb.append(super.toString(true));
		sb.append(", H264PayType: ").append(hdInnPayTypeEn);
		if (hdInnPayTypeEn == H264PayloadType.UNKNOWN) {
			sb.append(" (o=").append(Integer.toUnsignedString(hdInnPayTypeBy)).append(")");
		}
		sb.append(", PayNuhRefIdc: ").append(Integer.toUnsignedString(hdInnPayNuhRefIdc));
		if (hdInnPayTypeEn == H264PayloadType.FU_A) {
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
		final int additionalHeaderSize = (hdInnPayTypeEn == H264PayloadType.FU_A ? 1 : 0);
		final byte[] resA = new byte[INNER_HEADER_SIZE_MIN + additionalHeaderSize];

		resA[0] = (byte)( ( ((hdInnPayNuhRefIdc & 0x03) << 5) & 0x60) | (hdInnPayTypeBy & 0x1F) );
		if (additionalHeaderSize > 0) {
			resA[1] = (byte)((hdInnFuS ? 0x80 : 0x00) | (hdInnFuE ? 0x40 : 0x00) | (hdInnFuTypeBy & 0x3F));
		}

		return resA;
	}

}

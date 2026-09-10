package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.VideoH264Info;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.VideoH264Parser;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Packet Payload for H264.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3984">RFC-3984</a>
 */
public final class RtpPacketH264 extends RtpPacketCodecBase {

	public enum H264PayloadType {
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

	public static class InnerHeaderData implements Cloneable {
		/** Payload Header: RTP/H264 Payload Type as byte (6 bits) */
		public byte payTypeBy = H264PayloadType.UNKNOWN.value;
		/** Payload Header: RTP/H264 Payload Type as enum */
		public @NonNull H264PayloadType payTypeEn = H264PayloadType.UNKNOWN;
		/** Payload Header: Ref IDC - indicates importance: 0=not used for reference, >0=used for reference (2 bits) */
		public byte payNuhRefIdc = 0;
		/** FU Header: S bit, needs to be zero for the first packet and one for later packets (1 bit) */
		public boolean fuS = false;
		/** FU Header: E bit, needs to be one for the last packet and zero for all other packets (1 bit) */
		public boolean fuE = false;
		/** FU Header: NAL Unit Type, must be equal to the field Type of the NAL Unit (6 bits) */
		public byte fuTypeBy = H264PayloadType.UNKNOWN.value;

		@Override
		public @NonNull String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("H264PayType: ").append(payTypeEn);
			if (payTypeEn == H264PayloadType.UNKNOWN) {
				sb.append(" (o=").append(Integer.toUnsignedString(payTypeBy)).append(")");
			}
			sb.append(", PayNuhRefIdc: ").append(Integer.toUnsignedString(payNuhRefIdc));
			if (payTypeEn == H264PayloadType.FU_A) {
				sb.append(", FuS: ").append(fuS);
				sb.append(", FuE: ").append(fuE);
				sb.append(", FuType: ").append(Integer.toUnsignedString(fuTypeBy));
			}
			return sb.toString();
		}

		@Override
		public InnerHeaderData clone() {
			try {
				return (InnerHeaderData)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Minimum size of the main payload-specific RTP header */
	public static final int INNER_HEADER_SIZE_MIN = VideoH264Parser.NAL_UNIT_HEADER_SIZE;
	/** Maximum size of the main payload-specific RTP header */
	@SuppressWarnings("unused")
	public static final int INNER_HEADER_SIZE_MAX = INNER_HEADER_SIZE_MIN + 1;

	private final InnerHeaderData hdInnData = new InnerHeaderData();

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H264 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param h264Info H264 info
	 * @param payloadView Payload data view
	 */
	public RtpPacketH264(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				boolean isLastFragment,
				@NonNull VideoH264Info h264Info,
				@NonNull BufferView payloadView
			) {
		super(RtpPacketType.V_H264, paramsBase);

		//
		this.hdInnData.payTypeEn = H264PayloadType.UNKNOWN;
		updatePacket(paramsBase, fragmentOffset, isLastFragment, h264Info, payloadView);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	public RtpPacketH264(@NonNull BufferExt packetData) {
		super(RtpPacketType.V_H264, packetData);

		if (packetData.getUsed() < RTP_CONT_HEADER_SIZE + INNER_HEADER_SIZE_MIN) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		// parse inner main header fields
		int offs = RTP_CONT_HEADER_SIZE;
		this.hdInnData.payNuhRefIdc = (byte)( ((packetData.get(offs) & 0x60) >>> 5) & 0x03);
		this.hdInnData.payTypeBy = (byte)(packetData.get(offs) & 0x1F);
		this.hdInnData.payTypeEn = H264PayloadType.of(this.hdInnData.payTypeBy);

		// determine the length of the inner header bitstream
		final int additionalHeaderSize = (this.hdInnData.payTypeEn == H264PayloadType.FU_A ? 1 : 0);
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE_MIN + additionalHeaderSize;

		// read optional FU header fields
		if (additionalHeaderSize == 0) {
			this.hdInnData.fuS = false;
			this.hdInnData.fuE = false;
			this.hdInnData.fuTypeBy = 0x00;
		} else {
			if (packetData.getUsed() < RTP_CONT_HEADER_SIZE + this.payloadSpecHeaderSize) {
				throw new IllegalArgumentException("Invalid RTP packet size");
			}
			byte tmpByte = packetData.get(++offs);
			this.hdInnData.fuS = ((tmpByte & (byte)0x80) != 0);
			this.hdInnData.fuE = ((tmpByte & (byte)0x40) != 0);
			this.hdInnData.fuTypeBy = (byte)(tmpByte & (byte)0x3F);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull InnerHeaderData getParsedInnerHeaderData() {
		return hdInnData.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Update the entire packet.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H264 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param h264Info H264 info
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				boolean isLastFragment,
				@NonNull VideoH264Info h264Info,
				@NonNull BufferView payloadView
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
		this.hdInnData.payTypeBy = (isFragmented ? H264PayloadType.FU_A.getValue() : h264Info.nalUnitTypeBy);
		this.hdInnData.payTypeEn = H264PayloadType.of(this.hdInnData.payTypeBy);
		this.hdInnData.payNuhRefIdc = h264Info.nuhRefIdc;
		this.hdInnData.fuS = (this.hdInnData.payTypeEn == H264PayloadType.FU_A && fragmentOffset == 0);
		this.hdInnData.fuE = (this.hdInnData.payTypeEn == H264PayloadType.FU_A && isLastFragment);
		this.hdInnData.fuTypeBy = (this.hdInnData.payTypeEn == H264PayloadType.FU_A ? h264Info.nalUnitTypeBy : 0x00);

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
				payloadView.getInternalBaPtr(),
				payloadView.getOffset() + skip,
				this.packetBuf.getUsed(),
				payloadView.getLength() - skip
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				super.toString(true) +
				", " + hdInnData.toString() +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private byte[] buildRawInnerHeaderFromFields() {
		final int additionalHeaderSize = (hdInnData.payTypeEn == H264PayloadType.FU_A ? 1 : 0);
		final byte[] resA = new byte[INNER_HEADER_SIZE_MIN + additionalHeaderSize];

		resA[0] = (byte)( ( ((hdInnData.payNuhRefIdc & 0x03) << 5) & 0x60) | (hdInnData.payTypeBy & 0x1F) );
		if (additionalHeaderSize > 0) {
			resA[1] = (byte)((hdInnData.fuS ? 0x80 : 0x00) | (hdInnData.fuE ? 0x40 : 0x00) | (hdInnData.fuTypeBy & 0x3F));
		}

		return resA;
	}

}

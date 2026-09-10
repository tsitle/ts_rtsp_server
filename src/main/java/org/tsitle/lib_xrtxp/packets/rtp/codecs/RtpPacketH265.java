package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.VideoH265Info;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.VideoH265Parser;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Packet Payload for H265.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7798">RFC-7798</a>
 */
public final class RtpPacketH265 extends RtpPacketCodecBase {

	public enum H265PayloadType {
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
		public static @NonNull H265PayloadType of(byte value) {
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

	public static class InnerHeaderData implements Cloneable {
		/** Payload Header: RTP/H265 Payload Type as byte (6 bits) */
		public byte payTypeBy = H265PayloadType.UNKNOWN.value;
		/** Payload Header: RTP/H265 Payload Type as enum */
		public @NonNull H265PayloadType payTypeEn = H265PayloadType.UNKNOWN;
		/** Payload Header: Layer ID, required to be equal to zero (6 bits) */
		public byte payNuhLayerId = 0;
		/** Payload Header: Temporal identifier of the NAL unit plus 1, required to be unequal to zero (3 bits) */
		public byte payNuhTemporalIdPlus1 = 0;
		/** FU Header: S bit, needs to be zero for the first packet and one for later packets (1 bit) */
		public boolean fuS = false;
		/** FU Header: E bit, needs to be one for the last packet and zero for all other packets (1 bit) */
		public boolean fuE = false;
		/** FU Header: NAL Unit Type, must be equal to the field Type of the NAL Unit (6 bits) */
		public byte fuTypeBy = H265PayloadType.UNKNOWN.value;

		@Override
		public @NonNull String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("H265PayType: ").append(payTypeEn);
			if (payTypeEn == H265PayloadType.UNKNOWN) {
				sb.append(" (o=").append(Integer.toUnsignedString(payTypeBy)).append(")");
			}
			sb.append(", PayNuhLayerId: ").append(Integer.toUnsignedString(payNuhLayerId));
			sb.append(", PayNuhTemporalIdPlus1: ").append(Integer.toUnsignedString(payNuhTemporalIdPlus1));
			if (payTypeEn == H265PayloadType.FU) {
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
	public static final int INNER_HEADER_SIZE_MIN = VideoH265Parser.NAL_UNIT_HEADER_SIZE;
	/** Maximum size of the main payload-specific RTP header */
	@SuppressWarnings("unused")
	public static final int INNER_HEADER_SIZE_MAX = INNER_HEADER_SIZE_MIN + 1;

	private final InnerHeaderData hdInnData = new InnerHeaderData();

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H265 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param h265Info H265 info
	 * @param payloadView Payload data view
	 */
	public RtpPacketH265(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				boolean isLastFragment,
				@NonNull VideoH265Info h265Info,
				@NonNull BufferView payloadView
			) {
		super(RtpPacketType.V_H265, paramsBase);

		//
		this.hdInnData.payTypeEn = H265PayloadType.UNKNOWN;
		updatePacket(paramsBase, fragmentOffset, isLastFragment, h265Info, payloadView);
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
		this.hdInnData.payTypeBy = (byte)( ((packetData.get(offs) & 0x7E) >>> 1) & 0x3F);
		this.hdInnData.payTypeEn = H265PayloadType.of(this.hdInnData.payTypeBy);
		this.hdInnData.payNuhLayerId = (byte)( ( ((packetData.get(offs++) & 0x01) << 5) |
				((packetData.get(offs) & 0xF8) >> 3) ) & 0x3F);
		this.hdInnData.payNuhTemporalIdPlus1 = (byte)(packetData.get(offs++) & 0x07);

		// determine the length of the inner header bitstream
		final int additionalHeaderSize = (this.hdInnData.payTypeEn == H265PayloadType.FU ? 1 : 0);
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE_MIN + additionalHeaderSize;

		if (additionalHeaderSize == 0) {
			this.hdInnData.fuS = false;
			this.hdInnData.fuE = false;
			this.hdInnData.fuTypeBy = 0x00;
		} else {
			if (packetData.getUsed() < RTP_CONT_HEADER_SIZE + this.payloadSpecHeaderSize) {
				throw new IllegalArgumentException("Invalid RTP packet size");
			}
			byte tmpByte = packetData.get(offs);
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
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the H265 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param h265Info H265 info
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				boolean isLastFragment,
				@NonNull VideoH265Info h265Info,
				@NonNull BufferView payloadView
			) {
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (h265Info.nuhLayerId != 0 || h265Info.nuhTemporalIdPlus1 == 0) {
			throw new IllegalArgumentException("Cannot process this kind of H265");
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
		this.hdInnData.payTypeBy = (isFragmented ? H265PayloadType.FU.getValue() : h265Info.nalUnitTypeBy);
		this.hdInnData.payTypeEn = H265PayloadType.of(this.hdInnData.payTypeBy);
		this.hdInnData.payNuhLayerId = h265Info.nuhLayerId;
		this.hdInnData.payNuhTemporalIdPlus1 = h265Info.nuhTemporalIdPlus1;
		this.hdInnData.fuS = (this.hdInnData.payTypeEn == H265PayloadType.FU && fragmentOffset == 0);
		this.hdInnData.fuE = (this.hdInnData.payTypeEn == H265PayloadType.FU && isLastFragment);
		this.hdInnData.fuTypeBy = (this.hdInnData.payTypeEn == H265PayloadType.FU ? h265Info.nalUnitTypeBy : 0x00);

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
		final int additionalHeaderSize = (hdInnData.payTypeEn == H265PayloadType.FU ? 1 : 0);
		final byte[] resA = new byte[VideoH265Parser.NAL_UNIT_HEADER_SIZE + additionalHeaderSize];

		resA[0] = (byte)( ( ((hdInnData.payTypeBy & 0x3F) << 1) & 0x7E) | (((hdInnData.payNuhLayerId & 0x3F) >> 5) & 0x01) );
		resA[1] = (byte)( ( ((hdInnData.payNuhLayerId & 0x1F) << 5) & 0xF8) | (hdInnData.payNuhTemporalIdPlus1 & 0x07) );
		if (additionalHeaderSize > 0) {
			resA[2] = (byte)((hdInnData.fuS ? 0x80 : 0x00) | (hdInnData.fuE ? 0x40 : 0x00) | (hdInnData.fuTypeBy & 0x3F));
		}

		return resA;
	}

}

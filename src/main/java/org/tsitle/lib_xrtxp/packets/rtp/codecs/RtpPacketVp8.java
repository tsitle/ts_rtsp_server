package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Info;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;
import org.tsitle.lib_xrtxp.common.helpers.BitWriterHelper;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Packet Payload for VP8.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7741">RFC-7741</a>
 */
public final class RtpPacketVp8 extends RtpPacketCodecBase {

	public static class InnerHeaderData implements Cloneable {
		/** Extended control bits present? */
		public boolean main_FlagX = false;
		/** Non-reference frame? */
		public boolean main_FlagN = false;
		/** Start of VP8 partition? */
		public boolean main_FlagS = false;
		/** Partition index (can be kept at 0 for all packets). This is not the fragment offset. */
		public byte main_PartIx = 0;

		/** PictureID present? */
		public boolean subX_FlagI = false;
		/** TL0PICIDX present? */
		public boolean subX_FlagL = false;
		/** TID present? */
		public boolean subX_FlagT = false;
		/** KEYIDX present? */
		public boolean subX_FlagK = false;

		/** If M is set, the remainder of the PictureID field MUST contain 15 bits, else it MUST contain 7 bits */
		public boolean subI_M = false;
		/** PictureID: 7 or 15 bits */
		public short subI_PID = 0;

		/** 8-bits temporal level zero index */
		public byte subL_TL0PICIDX = 0;

		/** 2-bits temporal-layer index */
		public byte subTK_TID = 0;
		/** 1 layer sync bit */
		public boolean subTK_Y = false;
		/** 5-bits temporal key frame index */
		public byte subTK_KEYIDX = 0;

		@Override
		public @NonNull String toString() {
			//noinspection StringBufferReplaceableByString
			StringBuilder sb = new StringBuilder();
			sb.append("IsStartOfPart=").append(main_FlagS ? "T" : "F");
			return sb.toString();
		}

		@Override
		public @NonNull InnerHeaderData clone() {
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
	public static final int INNER_HEADER_SIZE_MIN = 1;
	/** Maximum size of the main payload-specific RTP header */
	@SuppressWarnings("unused")
	public static final int INNER_HEADER_SIZE_MAX = INNER_HEADER_SIZE_MIN + 5;

	private final InnerHeaderData hdInnData = new InnerHeaderData();

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the VP8 frame data) (24 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param vp8Info VP8 info
	 * @param payloadView Payload data view
	 */
	public RtpPacketVp8(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				@SuppressWarnings("unused") boolean isLastFragment,
				@SuppressWarnings("unused") @NonNull VideoVp8Info vp8Info,
				@NonNull BufferView payloadView
			) {
		super(RtpPacketType.V_VP8, paramsBase);

		//
		updatePacket(paramsBase, fragmentOffset, payloadView);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	@SuppressWarnings("unused")
	public RtpPacketVp8(@NonNull BufferExt packetData) {
		super(RtpPacketType.V_VP8, packetData);

		if (packetData.getUsed() < RTP_CONT_HEADER_SIZE + INNER_HEADER_SIZE_MIN) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		// parse inner main header fields
		BitReaderHelper brh = new BitReaderHelper(packetData, RTP_CONT_HEADER_SIZE);
		try {
			this.hdInnData.main_FlagX = (brh.readBits(1) == 1);
			brh.readBits(1);  // reserved
			this.hdInnData.main_FlagN = (brh.readBits(1) == 1);
			this.hdInnData.main_FlagS = (brh.readBits(1) == 1);
			brh.readBits(1);  // reserved
			this.hdInnData.main_PartIx = (byte)(brh.readBits(3) & 0x07);

			if (this.hdInnData.main_FlagX) {
				this.hdInnData.subX_FlagI = (brh.readBits(1) == 1);
				this.hdInnData.subX_FlagL = (brh.readBits(1) == 1);
				this.hdInnData.subX_FlagT = (brh.readBits(1) == 1);
				this.hdInnData.subX_FlagK = (brh.readBits(1) == 1);
				brh.readBits(4);  // reserved
				if (this.hdInnData.subX_FlagI) {
					this.hdInnData.subI_M = (brh.readBits(1) == 1);
					this.hdInnData.subI_PID =
							(short)(brh.readBits(this.hdInnData.subI_M ? 15 : 7) & (this.hdInnData.subI_M ? 0x7FFF : 0x7F));
				}
				if (this.hdInnData.subX_FlagL) {
					this.hdInnData.subL_TL0PICIDX = (byte)(brh.readBits(8) & 0xFF);
				}
				if (this.hdInnData.subX_FlagT || this.hdInnData.subX_FlagK) {
					this.hdInnData.subTK_TID = (byte)(brh.readBits(2) & 0x03);
					if (! this.hdInnData.subX_FlagT) {
						this.hdInnData.subTK_TID = 0;
					}
					this.hdInnData.subTK_Y = (brh.readBits(1) == 1);
					this.hdInnData.subTK_KEYIDX = (byte)(brh.readBits(5) & 0x1F);
					if (! this.hdInnData.subX_FlagK) {
						this.hdInnData.subTK_KEYIDX = 0;
					}
				}
			}
		} catch (BitReaderEosException e) {
			throw new IllegalArgumentException("Could not read from RTP packet");
		}

		// determine the length of the inner header bitstream
		final int additionalHeaderSize = (this.hdInnData.main_FlagX ? 4 + (this.hdInnData.subI_M ? 1 : 0) : 0);
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE_MIN + additionalHeaderSize;
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
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the VP8 frame data) (24 bits)
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				@NonNull BufferView payloadView
			) {
		updatePacketHeader(paramsBase);

		// set inner main header fields
		this.hdInnData.main_FlagS = (fragmentOffset == 0);

		// build the inner header bitstream (main header + optional FU header)
		byte[] tmpRtpXxxHeader = buildRawInnerHeaderFromFields();
		this.payloadSpecHeaderSize = tmpRtpXxxHeader.length;
		this.packetBuf.append(tmpRtpXxxHeader);

		// copy the inner payload bitstream
		int skip = 0;
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
		BitWriterHelper bwh = new BitWriterHelper();
		bwh.writeBits(hdInnData.main_FlagX ? 1 : 0, 1);
		bwh.writeBits(0, 1);  // reserved
		bwh.writeBits(hdInnData.main_FlagN ? 1 : 0, 1);
		bwh.writeBits(hdInnData.main_FlagS ? 1 : 0, 1);
		bwh.writeBits(0, 1);  // reserved
		bwh.writeBits(hdInnData.main_PartIx & 0x07, 3);

		//
		if (hdInnData.main_FlagX) {
			bwh.writeBits(hdInnData.subX_FlagI ? 1 : 0, 1);
			bwh.writeBits(hdInnData.subX_FlagL ? 1 : 0, 1);
			bwh.writeBits(hdInnData.subX_FlagT ? 1 : 0, 1);
			bwh.writeBits(hdInnData.subX_FlagK ? 1 : 0, 1);
			bwh.writeBits(0, 4);  // reserved
			//
			if (hdInnData.subX_FlagI) {
				bwh.writeBits(hdInnData.subI_M ? 1 : 0, 1);
				int tmpPictId = (int)hdInnData.subI_PID & 0xFFFF;
				bwh.writeBits(tmpPictId & (hdInnData.subI_M ? 0x7FFF : 0x7F), hdInnData.subI_M ? 15 : 7);
			}
			//
			if (hdInnData.subX_FlagL) {
				bwh.writeBits(hdInnData.subL_TL0PICIDX, 8);
			}
			//
			if (hdInnData.subX_FlagT || hdInnData.subX_FlagK) {
				bwh.writeBits(hdInnData.subX_FlagT ? (hdInnData.subTK_TID & 0x03) : 0, 2);
				bwh.writeBits(hdInnData.subTK_Y ? 1 : 0, 1);
				bwh.writeBits(hdInnData.subX_FlagK ? (hdInnData.subTK_KEYIDX & 0x1F) : 0, 5);
			}
		}

		return bwh.toByteArray();
	}

}

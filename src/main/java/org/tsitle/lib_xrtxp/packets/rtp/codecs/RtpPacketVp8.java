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

	/** Minimum size of the main payload-specific RTP header */
	public static final int INNER_HEADER_SIZE_MIN = 1;
	/** Maximum size of the main payload-specific RTP header */
	@SuppressWarnings("unused")
	public static final int INNER_HEADER_SIZE_MAX = INNER_HEADER_SIZE_MIN + 5;

	/** Extended control bits present? */
	private boolean hdInnMain_FlagX = false;
	/** Non-reference frame? */
	private boolean hdInnMain_FlagN = false;
	/** Start of VP8 partition? */
	private boolean hdInnMain_FlagS = false;
	/** Partition index */
	private byte hdInnMain_PartIx = 0;

	/** PictureID present? */
	private boolean hdInnSubX_FlagI = false;
	/** TL0PICIDX present? */
	private boolean hdInnSubX_FlagL = false;
	/** TID present? */
	private boolean hdInnSubX_FlagT = false;
	/** KEYIDX present? */
	private boolean hdInnSubX_FlagK = false;

	/** If M is set, the remainder of the PictureID field MUST contain 15 bits, else it MUST contain 7 bits */
	private boolean hdInnSubI_M = false;
	/** PictureID: 7 or 15 bits */
	private short hdInnSubI_PID = 0;

	/** 8-bits temporal level zero index */
	private byte hdInnSubL_TL0PICIDX = 0;

	/** 2-bits temporal-layer index */
	private byte hdInnSubTK_TID = 0;
	/** 1 layer sync bit */
	private boolean hdInnSubTK_Y = false;
	/** 5-bits temporal key frame index */
	private byte hdInnSubTK_KEYIDX = 0;

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
			this.hdInnMain_FlagX = (brh.readBits(1) == 1);
			brh.readBits(1);  // reserved
			this.hdInnMain_FlagN = (brh.readBits(1) == 1);
			this.hdInnMain_FlagS = (brh.readBits(1) == 1);
			brh.readBits(1);  // reserved
			this.hdInnMain_PartIx = (byte)(brh.readBits(3) & 0x07);

			if (this.hdInnMain_FlagX) {
				this.hdInnSubX_FlagI = (brh.readBits(1) == 1);
				this.hdInnSubX_FlagL = (brh.readBits(1) == 1);
				this.hdInnSubX_FlagT = (brh.readBits(1) == 1);
				this.hdInnSubX_FlagK = (brh.readBits(1) == 1);
				brh.readBits(4);  // reserved
				if (this.hdInnSubX_FlagI) {
					this.hdInnSubI_M = (brh.readBits(1) == 1);
					this.hdInnSubI_PID = (short)(brh.readBits(this.hdInnSubI_M ? 15 : 7) & (hdInnSubI_M ? 0x7FFF : 0x7F));
				}
				if (this.hdInnSubX_FlagL) {
					this.hdInnSubL_TL0PICIDX = (byte)(brh.readBits(8) & 0xFF);
				}
				if (this.hdInnSubX_FlagT || this.hdInnSubX_FlagK) {
					this.hdInnSubTK_TID = (byte)(brh.readBits(2) & 0x03);
					if (! this.hdInnSubX_FlagT) {
						this.hdInnSubTK_TID = 0;
					}
					this.hdInnSubTK_Y = (brh.readBits(1) == 1);
					this.hdInnSubTK_KEYIDX = (byte)(brh.readBits(5) & 0x1F);
					if (! this.hdInnSubX_FlagK) {
						this.hdInnSubTK_KEYIDX = 0;
					}
				}
			}
		} catch (BitReaderEosException e) {
			throw new IllegalArgumentException("Could not read from RTP packet");
		}

		// determine the length of the inner header bitstream
		final int additionalHeaderSize = (hdInnMain_FlagX ? 4 + (hdInnSubI_M ? 1 : 0) : 0);
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE_MIN + additionalHeaderSize;
	}

	// -----------------------------------------------------------------------------------------------------------------
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
		this.hdInnMain_FlagS = (fragmentOffset == 0);

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
				", isStartOfPart=" + (hdInnMain_FlagS ? "T" : "F") +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private byte[] buildRawInnerHeaderFromFields() {
		BitWriterHelper bwh = new BitWriterHelper();
		bwh.writeBits(hdInnMain_FlagX ? 1 : 0, 1);
		bwh.writeBits(0, 1);  // reserved
		bwh.writeBits(hdInnMain_FlagN ? 1 : 0, 1);
		bwh.writeBits(hdInnMain_FlagS ? 1 : 0, 1);
		bwh.writeBits(0, 1);  // reserved
		bwh.writeBits(hdInnMain_PartIx & 0x07, 3);

		//
		if (hdInnMain_FlagX) {
			bwh.writeBits(hdInnSubX_FlagI ? 1 : 0, 1);
			bwh.writeBits(hdInnSubX_FlagL ? 1 : 0, 1);
			bwh.writeBits(hdInnSubX_FlagT ? 1 : 0, 1);
			bwh.writeBits(hdInnSubX_FlagK ? 1 : 0, 1);
			bwh.writeBits(0, 4);  // reserved
			//
			if (hdInnSubX_FlagI) {
				bwh.writeBits(hdInnSubI_M ? 1 : 0, 1);
				int tmpPictId = (int)hdInnSubI_PID & 0xFFFF;
				bwh.writeBits(tmpPictId & (hdInnSubI_M ? 0x7FFF : 0x7F), hdInnSubI_M ? 15 : 7);
			}
			//
			if (hdInnSubX_FlagL) {
				bwh.writeBits(hdInnSubL_TL0PICIDX, 8);
			}
			//
			if (hdInnSubX_FlagT || hdInnSubX_FlagK) {
				bwh.writeBits(hdInnSubX_FlagT ? (hdInnSubTK_TID & 0x03) : 0, 2);
				bwh.writeBits(hdInnSubTK_Y ? 1 : 0, 1);
				bwh.writeBits(hdInnSubX_FlagK ? (hdInnSubTK_KEYIDX & 0x1F) : 0, 5);
			}
		}

		return bwh.toByteArray();
	}

}

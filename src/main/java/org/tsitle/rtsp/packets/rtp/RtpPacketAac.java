package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.AudioAacInfo;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;
import org.tsitle.rtsp.exceptions.BitReaderEosException;
import org.tsitle.rtsp.helpers.BitReaderHelper;
import org.tsitle.rtsp.helpers.BitWriterHelper;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.RtspProtoSdpConstants;

/**
 * RTP Packet Payload for AAC.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3640">RFC-3640</a>
 */
public final class RtpPacketAac extends RtpPacketCodecBase {

	/**
	 * AU-header field AU-Size length in bits.<br />
	 * See RFC-3640 Section 3.3.6
	 */
	public static final int HEADER_FLD_SIZE_LENGTH_BITS = RtspProtoSdpConstants.AAC_HEADER_FLD_SIZE_LENGTH_BITS;
	/**
	 * AU-header field AU-Index length in bits.<br />
	 * See RFC-3640 Section 3.3.6
	 */
	public static final int HEADER_FLD_INDEX_LENGTH_BITS = RtspProtoSdpConstants.AAC_HEADER_FLD_INDEX_LENGTH_BITS;
	/**
	 * AU-header field AU-IndexDelta length in bits.<br />
	 * See RFC-3640 Section 3.3.6
	 */
	public static final int HEADER_FLD_INDEXDELTA_LENGTH_BITS = RtspProtoSdpConstants.AAC_HEADER_FLD_INDEXDELTA_LENGTH_BITS;

	/** Size of the entire AAC Access Unit in bytes ({@code HEADER_FLD_SIZE_LENGTH_BITS} bits) */
	private short hdInnAuSize;
	/** Access Unit Index ({@code HEADER_FLD_INDEX_LENGTH_BITS} bits) */
	private byte hdInnAuIndex;

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param fragmentIndex Fragment index ({@code HEADER_FLD_INDEX_LENGTH_BITS} bits)
	 * @param aacInfo AAC info
	 * @param payloadView Payload data view
	 */
	public RtpPacketAac(
				@NonNull ParamsContainerBase paramsBase,
				byte fragmentIndex,
				@NonNull AudioAacInfo aacInfo,
				@NonNull BufferView payloadView
			) {
		super(RtpPacketType.A_AAC, paramsBase);

		//
		updatePacket(paramsBase, fragmentIndex, aacInfo, payloadView);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	@SuppressWarnings("unused")
	public RtpPacketAac(@NonNull BufferExt packetData) {
		super(RtpPacketType.A_AAC, packetData);

		if (packetData.getUsed() <= RTP_CONT_HEADER_SIZE + 5) {
			throw new IllegalArgumentException("Invalid RTP packet size (too short)");
		}

		//
		BitReaderHelper bitReader = new BitReaderHelper(packetData, RTP_CONT_HEADER_SIZE);
		int tmpReadBits = 0;
		try {
			// AU-headers-length (16 bits)
			int tmpAuHeadersLengthBits = bitReader.readBits(16);
			tmpReadBits += 16;
			// AU-header (size + index + indexDelta)
			this.hdInnAuSize = (short)bitReader.readBits(HEADER_FLD_SIZE_LENGTH_BITS);
			tmpReadBits += HEADER_FLD_SIZE_LENGTH_BITS;
			if (HEADER_FLD_INDEX_LENGTH_BITS > 0) {
				this.hdInnAuIndex = (byte)bitReader.readBits(HEADER_FLD_INDEX_LENGTH_BITS);
				tmpReadBits += HEADER_FLD_INDEX_LENGTH_BITS;
			}
			if (HEADER_FLD_INDEXDELTA_LENGTH_BITS > 0) {
				int tmpAuIdxDelta = bitReader.readBits(HEADER_FLD_INDEXDELTA_LENGTH_BITS);
				tmpReadBits += HEADER_FLD_INDEXDELTA_LENGTH_BITS;
			}
			//noinspection ConstantValue
			if (tmpReadBits % 8 != 0) {
				bitReader.readBits(8 - (tmpReadBits % 8));
			}
		} catch (BitReaderEosException e) {
			throw new RuntimeException(e);
		}

		// determine the length of the inner header bitstream
		this.payloadSpecHeaderSize = (tmpReadBits / 8);

		if (packetData.getUsed() != RTP_CONT_HEADER_SIZE + this.payloadSpecHeaderSize + hdInnAuSize) {
			int tmpPaySz = packetData.getUsed() - RTP_CONT_HEADER_SIZE - this.payloadSpecHeaderSize;
			throw new IllegalArgumentException("Invalid RTP payload size (is=" +
					tmpPaySz + ", exp=" + hdInnAuSize + ")");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Update the entire packet.
	 * @param paramsBase Base Container parameters
	 * @param fragmentIndex Fragment index ({@code HEADER_FLD_INDEX_LENGTH_BITS} bits)
	 * @param aacInfo AAC info
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				byte fragmentIndex,
				@NonNull AudioAacInfo aacInfo,
				@NonNull BufferView payloadView
			) {
		if (fragmentIndex < 0 || fragmentIndex > 0x07) {
			throw new IllegalArgumentException("Invalid fragment index");
		}
		if (aacInfo.channelConfiguration == 0) {
			throw new IllegalArgumentException("Cannot process this kind of AAC");
		}
		if (payloadView.getLength() > aacInfo.getPayloadLength()) {
			throw new IllegalArgumentException("Invalid AAC payload size");
		}

		//
		updatePacketHeader(paramsBase);

		/*
		 *  +-------------------------------------------------------------------+
		 *  | AU-headers-length (2 bytes)                                       |
		 *  +-------------------------------------------------------------------+
		 *  | AU-header ('AU-Size' + 'AU-Index' + 'AU-IndexDelta')              |
		 *  +-------------------------------------------------------------------+
		 *  | AAC frame bytes (excluding the ADTS header which is 7 or 9 bytes  |
		 *  +-------------------------------------------------------------------+
		 */

		// set inner main header fields
		this.hdInnAuSize = (short)aacInfo.getPayloadLength();  // size of the entire AAC Access Unit
		if (Short.toUnsignedInt(this.hdInnAuSize) != aacInfo.getPayloadLength()) {
			throw new IllegalArgumentException("Invalid AAC payload size -- exceeds 16 bits");
		}
		validatePayloadSize(Short.toUnsignedInt(this.hdInnAuSize));
		this.hdInnAuIndex = fragmentIndex;

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
		if (hdInnAuSize >= (1 << HEADER_FLD_SIZE_LENGTH_BITS)) {
			throw new IllegalArgumentException("AAC frame too large for " + HEADER_FLD_SIZE_LENGTH_BITS + "-bit size field");
		}
		if (hdInnAuIndex >= (1 << HEADER_FLD_INDEX_LENGTH_BITS)) {
			throw new IllegalArgumentException("AAC AU Index too large for " + HEADER_FLD_INDEX_LENGTH_BITS + "-bit index field");
		}

		// AU-headers-length (16 bits)
		final int auHeadersLengthBits = HEADER_FLD_SIZE_LENGTH_BITS + HEADER_FLD_INDEX_LENGTH_BITS +
				HEADER_FLD_INDEXDELTA_LENGTH_BITS;

		BitWriterHelper bitWriter = new BitWriterHelper();

		bitWriter.writeBits((auHeadersLengthBits >> 8) & 0xFF, 8);
		bitWriter.writeBits(auHeadersLengthBits & 0xFF, 8);

		// AU-header (size + index + indexDelta)
		/*
		 * Size      : 13 bits 1111 1111 1111 1
		 * Index     :  3 bits                  111
		 * IndexDelta:  3 bits                      1110 0000 0000 0000
		 */
		bitWriter.writeBits(hdInnAuSize, HEADER_FLD_SIZE_LENGTH_BITS);
		if (HEADER_FLD_INDEX_LENGTH_BITS > 0) {
			bitWriter.writeBits(hdInnAuIndex, HEADER_FLD_INDEX_LENGTH_BITS);
		}
		if (HEADER_FLD_INDEXDELTA_LENGTH_BITS > 0) {
			bitWriter.writeBits(0, HEADER_FLD_INDEXDELTA_LENGTH_BITS);
		}

		return bitWriter.toByteArray();
	}

	private static void validatePayloadSize(int payloadSize) {
		BitWriterHelper bitWriter = new BitWriterHelper();
		bitWriter.writeBits(payloadSize, HEADER_FLD_SIZE_LENGTH_BITS);
		byte[] tmpBa = bitWriter.toByteArray();

		BitReaderHelper bitReader = new BitReaderHelper(new BufferExt(tmpBa), 0);
		int tmpReadVal;
		try {
			tmpReadVal = bitReader.readBits(HEADER_FLD_SIZE_LENGTH_BITS);
		} catch (BitReaderEosException e) {
			throw new RuntimeException(e);
		}
		if (tmpReadVal != payloadSize) {
			throw new IllegalArgumentException("Invalid payload size -- exceeds " +
					HEADER_FLD_SIZE_LENGTH_BITS + "-bit size field");
		}
	}

}

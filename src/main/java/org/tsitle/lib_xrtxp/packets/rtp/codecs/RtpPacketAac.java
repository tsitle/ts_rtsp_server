package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacInfo;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;
import org.tsitle.lib_xrtxp.common.helpers.BitWriterHelper;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpConstants;

/**
 * RTP Packet Payload for AAC.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3640">RFC-3640</a>
 */
public final class RtpPacketAac extends RtpPacketCodecBase {

	public static class InnerHeaderConfig {
		/** AU-header field 'size' length in bits */
		public int cfgHdInnSizeLength = HEADER_FLD_SIZE_LENGTH_BITS;
		/** AU-header field 'index' length in bits */
		public int cfgHdInnIndexLength = HEADER_FLD_INDEX_LENGTH_BITS;
		/** AU-header field 'index delta' length in bits */
		public int cfgHdInnIndexDeltaLength = HEADER_FLD_INDEXDELTA_LENGTH_BITS;

		public void copyFrom(@NonNull InnerHeaderConfig other) {
			cfgHdInnSizeLength = other.cfgHdInnSizeLength;
			cfgHdInnIndexLength = other.cfgHdInnIndexLength;
			cfgHdInnIndexDeltaLength = other.cfgHdInnIndexDeltaLength;
		}
	}

	public static class InnerHeaderData implements Cloneable {
		/** Size of the entire AAC Access Unit in bytes ({@code HEADER_FLD_SIZE_LENGTH_BITS} bits) */
		public short auSize;
		/** Access Unit Index ({@code HEADER_FLD_INDEX_LENGTH_BITS} bits) */
		public byte auIndex;
		/** Access Unit Index Delta ({@code AAC_HEADER_FLD_INDEXDELTA_LENGTH_BITS} bits) */
		public byte auIdxDelta;

		@Override
		public @NonNull String toString() {
			return "AuSize: " + auSize +
					", AuIndex: " + Byte.toUnsignedInt(auIndex) +
					", AuIdxDelta: " + Byte.toUnsignedInt(auIdxDelta);
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

	private final InnerHeaderData hdInnData = new InnerHeaderData();

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
	 * @param innerHeaderConfig AU-header field lengths
	 */
	public RtpPacketAac(
				@NonNull BufferExt packetData,
				@NonNull InnerHeaderConfig innerHeaderConfig
			) {
		super(RtpPacketType.A_AAC, packetData);

		final int totalInnerHeaderLengthBits = innerHeaderConfig.cfgHdInnSizeLength +
				innerHeaderConfig.cfgHdInnIndexLength + innerHeaderConfig.cfgHdInnIndexDeltaLength;
		if (packetData.getUsed() <= RTP_CONT_HEADER_SIZE + (totalInnerHeaderLengthBits / 8) + 1) {  // 1^=inner payload length
			throw new IllegalArgumentException("Invalid RTP packet size (too short)");
		}

		//
		BitReaderHelper bitReader = new BitReaderHelper(packetData, RTP_CONT_HEADER_SIZE);
		int tmpReadBits = 0;
		try {
			// AU-headers-length (16 bits)
			int tmpAuHeadersLengthBits = bitReader.readBits(16);
			if (tmpAuHeadersLengthBits != totalInnerHeaderLengthBits) {
				/*
				 * Either there are multiple AU-headers - which is not supported - or the header field sizes are incorrect.
				 */
				throw new IllegalArgumentException("Invalid RTP packet: multiple AU-headers are not supported");
			}
			tmpReadBits += 16;
			// AU-header (size + index + indexDelta)
			this.hdInnData.auSize = (short)bitReader.readBits(innerHeaderConfig.cfgHdInnSizeLength);
			tmpReadBits += innerHeaderConfig.cfgHdInnSizeLength;
			if (innerHeaderConfig.cfgHdInnIndexLength > 0) {
				this.hdInnData.auIndex = (byte)bitReader.readBits(innerHeaderConfig.cfgHdInnIndexLength);
				tmpReadBits += innerHeaderConfig.cfgHdInnIndexLength;
			}
			if (innerHeaderConfig.cfgHdInnIndexDeltaLength > 0) {
				this.hdInnData.auIdxDelta = (byte)bitReader.readBits(innerHeaderConfig.cfgHdInnIndexDeltaLength);
				tmpReadBits += innerHeaderConfig.cfgHdInnIndexDeltaLength;
			}
			if (tmpReadBits % 8 != 0) {
				bitReader.readBits(8 - (tmpReadBits % 8));
			}
		} catch (BitReaderEosException e) {
			throw new RuntimeException(e);
		}

		// determine the length of the inner header bitstream
		this.payloadSpecHeaderSize = (tmpReadBits / 8);
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
		this.hdInnData.auSize = (short)aacInfo.getPayloadLength();  // size of the entire AAC Access Unit
		if (Short.toUnsignedInt(this.hdInnData.auSize) != aacInfo.getPayloadLength()) {
			throw new IllegalArgumentException("Invalid AAC payload size -- exceeds 16 bits");
		}
		validatePayloadSize(Short.toUnsignedInt(this.hdInnData.auSize));
		this.hdInnData.auIndex = fragmentIndex;

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
		if (Short.toUnsignedInt(hdInnData.auSize) >= (1 << HEADER_FLD_SIZE_LENGTH_BITS)) {
			throw new IllegalArgumentException("AAC frame too large for " + HEADER_FLD_SIZE_LENGTH_BITS + "-bit size field");
		}
		if (Byte.toUnsignedInt(hdInnData.auIndex) >= (1 << HEADER_FLD_INDEX_LENGTH_BITS)) {
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
		bitWriter.writeBits(Short.toUnsignedInt(hdInnData.auSize), HEADER_FLD_SIZE_LENGTH_BITS);
		if (HEADER_FLD_INDEX_LENGTH_BITS > 0) {
			bitWriter.writeBits(Byte.toUnsignedInt(hdInnData.auIndex), HEADER_FLD_INDEX_LENGTH_BITS);
		}
		if (HEADER_FLD_INDEXDELTA_LENGTH_BITS > 0) {
			bitWriter.writeBits(Byte.toUnsignedInt(hdInnData.auIdxDelta), HEADER_FLD_INDEXDELTA_LENGTH_BITS);
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

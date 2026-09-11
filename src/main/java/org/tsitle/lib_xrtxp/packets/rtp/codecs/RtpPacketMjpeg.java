package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_v_mjpeg.VideoJpegInfo;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Packet Payload for MJPEG.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc2435">RFC-2435</a>
 */
public final class RtpPacketMjpeg extends RtpPacketCodecBase {

	public enum JpegChannelEncodingType {
		/** 4:2:2 */
		YCBCR422(0),
		/** 4:2:0 */
		YCBCR420(1),
		/** Unknown type */
		UNKNOWN(0xFF);

		private final byte value;

		JpegChannelEncodingType(int value) {
			this.value = (byte)value;
		}
		public byte getValue() {
			return value;
		}
		public static @NonNull JpegChannelEncodingType of(byte value) {
			for (JpegChannelEncodingType type : JpegChannelEncodingType.values()) {
				if (type != UNKNOWN && type.getValue() == value) {
					return type;
				}
			}
			return UNKNOWN;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static class InnerHeaderData implements Cloneable {
		/** Type-specific first byte (8 bits)<br />
		 *   0=Image is progressively scanned<br />
		 *   1=Image is an odd field of an interlaced video signal<br />
		 *   2=Image is an even field of an interlaced video signal<br />
		 *   3=Image is a single field from an interlaced video signal
		 */
		public byte firstByte = 0;
		/** Fragment Offset (offset in bytes of the current packet in the JPEG frame data) (24 bits) */
		public int fragmentOffset = 0;
		/**
		 * JPEG Type (8 bits)<br />
		 *   0=YCbCr 4:2:2<br />
		 *   1=YCbCr 4:2:0
		 */
		public @NonNull JpegChannelEncodingType jpegType = JpegChannelEncodingType.UNKNOWN;
		/** Q value (8 bits) */
		public byte q = 0;
		/** Image width divided by 8 pixels, max. is 255*8=2040 pixels (8 bits) */
		public byte imageWidthDiv8 = 0;
		/** Image height divided by 8 pixels, max. is 255*8=2040 pixels (8 bits) */
		public byte imageHeightDiv8 = 0;

		@Override
		public @NonNull String toString() {
			//noinspection StringBufferReplaceableByString
			StringBuilder sb = new StringBuilder();
			sb.append("FirstByte: ").append(Byte.toUnsignedInt(firstByte));
			sb.append(", FragmentOffset: ").append(Integer.toUnsignedString(fragmentOffset));
			sb.append(", Type: ").append(jpegType);
			sb.append(", Q: ").append(Byte.toUnsignedInt(q));
			sb.append(", ImageWidth: ").append(Byte.toUnsignedInt(imageWidthDiv8));
			sb.append(", ImageHeight: ").append(Byte.toUnsignedInt(imageHeightDiv8));
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

	/** Maximum width and height of an image */
	public static final int IMAGE_MAX_WIDTH_HEIGHT = 2040;

	/** Size of the main payload-specific RTP header */
	public static final int INNER_HEADER_MAIN_SIZE = 8;
	/** Size of the QT RTP header without the tables */
	public static final int INNER_HEADER_QT_PRE_SIZE = 4;

	private final InnerHeaderData hdInnData = new InnerHeaderData();

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the JPEG frame data) (24 bits)
	 * @param jpegInfo JPEG info
	 * @param payloadView Payload data view
	 */
	public RtpPacketMjpeg(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				@NonNull VideoJpegInfo jpegInfo,
				@NonNull BufferView payloadView
			) {
		super(RtpPacketType.V_MJPEG, paramsBase);

		//
		updatePacket(paramsBase, fragmentOffset, jpegInfo, payloadView);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	public RtpPacketMjpeg(@NonNull BufferExt packetData) {
		super(RtpPacketType.V_MJPEG, packetData);

		if (packetData.getUsed() < RTP_CONT_HEADER_SIZE + INNER_HEADER_MAIN_SIZE) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		int offs = RTP_CONT_HEADER_SIZE;
		// parse inner main header fields
		this.hdInnData.firstByte = packetData.get(offs++);
		this.hdInnData.fragmentOffset = (((packetData.get(offs++) << 16) |
				(packetData.get(offs++) << 8) |
				packetData.get(offs++)) & 0xFFFFFF);
		this.hdInnData.jpegType = JpegChannelEncodingType.of(
				packetData.get(offs++)
			);
		this.hdInnData.q = packetData.get(offs++);
		this.hdInnData.imageWidthDiv8 = packetData.get(offs++);
		this.hdInnData.imageHeightDiv8 = packetData.get(offs++);

		// determine the length of the inner header bitstream (main header + optional QT header)
		final int tmpTotalMinLen = (RTP_CONT_HEADER_SIZE + INNER_HEADER_MAIN_SIZE + INNER_HEADER_QT_PRE_SIZE);
		final int tmpQtHdLength = (this.hdInnData.fragmentOffset == 0 && packetData.getUsed() > tmpTotalMinLen ?
				INNER_HEADER_QT_PRE_SIZE + parseInnerHeaderQuantTableLength(packetData, offs)
				: 0);
		this.payloadSpecHeaderSize = INNER_HEADER_MAIN_SIZE + tmpQtHdLength;
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
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the JPEG frame data) (24 bits)
	 * @param jpegInfo JPEG info
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				int fragmentOffset,
				@NonNull VideoJpegInfo jpegInfo,
				@NonNull BufferView payloadView
			) {
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}

		//
		updatePacketHeader(paramsBase);

		//
		if (jpegInfo.sof0_imgWidth <= 0 || jpegInfo.sof0_imgWidth > IMAGE_MAX_WIDTH_HEIGHT ||
				jpegInfo.sof0_imgHeight <= 0 || jpegInfo.sof0_imgHeight > IMAGE_MAX_WIDTH_HEIGHT ||
				(jpegInfo.sof0_channelEncoding != VideoJpegInfo.ChannelEncoding.YCBCR420 &&
						jpegInfo.sof0_channelEncoding != VideoJpegInfo.ChannelEncoding.YCBCR422) ||
				jpegInfo.sof0_precision != 8 ||
				jpegInfo.sos_scanDataOffs < 0 || jpegInfo.sos_scanDataLength < 1 ||
				jpegInfo.sof2_isProgressive ||
				jpegInfo.dqt_table16bitCount != 0 ||
				! jpegInfo.foundEoi || jpegInfo.usesDri) {
			throw new IllegalArgumentException("Cannot process this kind of JPEG");
		}

		// set inner main header fields
		this.hdInnData.firstByte = (byte)0;
		this.hdInnData.fragmentOffset = fragmentOffset;
		this.hdInnData.jpegType = (
				jpegInfo.sof0_channelEncoding == VideoJpegInfo.ChannelEncoding.YCBCR420 ?
				JpegChannelEncodingType.YCBCR420
				: JpegChannelEncodingType.YCBCR422
			);
		this.hdInnData.q = (byte)255;
		this.hdInnData.imageWidthDiv8 = (byte)(jpegInfo.sof0_imgWidth / 8);
		this.hdInnData.imageHeightDiv8 = (byte)(jpegInfo.sof0_imgHeight / 8);

		// build the inner header bitstream (main header + optional QT header)
		byte[] tmpRtpXxxHeader = buildRawInnerHeaderFromFields(fragmentOffset == 0, jpegInfo);
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

	private static int parseInnerHeaderQuantTableLength(@NonNull BufferExt data, int offs) {
		++offs;  // MBZ
		++offs;  // Precision
		return ((((data.get(offs) & 0xFF) << 8) & 0xFF00) |
				(data.get(offs + 1) & 0xFF));
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("DanglingJavadoc")
	private byte[] buildRawInnerHeaderFromFields(boolean withQtHeader, @NonNull VideoJpegInfo jpegInfo) {
		int qtHdLengthWithPreAndTables = 0;
		int qtTablesLength = 0;
		if (withQtHeader) {
			if (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelY] == null) {
				throw new IllegalArgumentException("Invalid JPEG info: Luma quantization table precision not found");
			}
			if (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCb] == null ||
					jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCr] == null) {
				throw new IllegalArgumentException("Invalid JPEG info: Chroma quantization table precision not found");
			}
			qtTablesLength = (
					64 * (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelY] == VideoJpegInfo.QuantizationTablePrecision.INT8 ?
							1 : 2) +
					64 * (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCb] == VideoJpegInfo.QuantizationTablePrecision.INT8 ?
							1 : 2)
				);
			qtHdLengthWithPreAndTables += INNER_HEADER_QT_PRE_SIZE + qtTablesLength;
		}
		final int completeHdLength = INNER_HEADER_MAIN_SIZE + qtHdLengthWithPreAndTables;
		final byte[] resA = new byte[completeHdLength];

		// main RTP/JPEG header
		resA[0] = hdInnData.firstByte;
		resA[1] = (byte)((hdInnData.fragmentOffset >> 16) & 0xFF);
		resA[2] = (byte)((hdInnData.fragmentOffset >> 8) & 0xFF);
		resA[3] = (byte)(hdInnData.fragmentOffset & 0xFF);
		resA[4] = hdInnData.jpegType.value;
		resA[5] = hdInnData.q;
		resA[6] = hdInnData.imageWidthDiv8;
		resA[7] = hdInnData.imageHeightDiv8;

		// The JPEG Quantization Table RTP header is only present in the first packet of a frame
		if (! withQtHeader) {
			return resA;
		}
		// Quantization Table: JPEG type 0 and 1 use two tables: one for
		//   the luminance component and one shared by the chrominance components.
		//   Each table is an array of 64 values.
		///
		int offs = INNER_HEADER_MAIN_SIZE;
		/// MBZ - must be zero (8 bits)
		resA[offs++] = 0;
		/// Precision (8 bits): the Precision field specifies the size of the coefficients in the table.
		///   The lowest bit corresponds to the first table. The second bit corresponds to the second table.
		///   If a bit is zero, the coefficients are 8 bits yielding a table length of 64 bytes.
		///   If a bit is one, the coefficients are 16 bits for a table length of 128 bytes.
		resA[offs++] = (byte)(
				(jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelY] == VideoJpegInfo.QuantizationTablePrecision.INT8 ?
						0 : 1) |
				(jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCb] == VideoJpegInfo.QuantizationTablePrecision.INT8 ?
						0 : 2)
			);
		/// QT Table Length (16 bits)
		resA[offs++] = (byte)((qtTablesLength >> 8) & 0xFF);
		resA[offs++] = (byte)(qtTablesLength & 0xFF);
		/// QT Table (128..256 bytes)
		//// generated QT Table
		/*byte[] outputLqt = new byte[64];
		byte[] outputCqt = new byte[64];
		generateQuantizationTable8bits(hdQ, outputLqt, outputCqt);*/
		//// copied QT Table
		byte[] outputLqt = getQuantizationTableData(jpegInfo, jpegInfo.sof0_quantTableSelY);
		if (outputLqt == null) {
			throw new IllegalArgumentException("Invalid JPEG info: Luma quantization table data not found");
		}
		byte[] outputCqt = getQuantizationTableData(jpegInfo, jpegInfo.sof0_quantTableSelCb);
		if (outputCqt == null) {
			throw new IllegalArgumentException("Invalid JPEG info: Chroma quantization table data not found");
		}
		////
		if (jpegInfo.sof0_quantTableSelCb != jpegInfo.sof0_quantTableSelCr) {
			throw new IllegalArgumentException("Invalid JPEG info: Quantization tables for Cb and Cr must be the same");
		}
		if (outputLqt.length + outputCqt.length != qtTablesLength) {
			throw new IllegalArgumentException("Invalid JPEG info: Invalid Quantization table sizes");
		}
		System.arraycopy(outputLqt, 0, resA, offs, outputLqt.length);
		offs += outputLqt.length;
		System.arraycopy(outputCqt, 0, resA, offs, outputCqt.length);

		return resA;
	}

	private static byte[] getQuantizationTableData(@NonNull VideoJpegInfo jpegInfo, byte tableSel) {
		if (jpegInfo.dqt_tablePrecisions[tableSel] == VideoJpegInfo.QuantizationTablePrecision.INT8 &&
				jpegInfo.dqt_tables8Bit[tableSel] == null) {
			throw new IllegalStateException("Invalid JPEG info: DQT 8-bit table is null");
		}
		if (jpegInfo.dqt_tablePrecisions[tableSel] == VideoJpegInfo.QuantizationTablePrecision.INT16 &&
				jpegInfo.dqt_tables16Bit[tableSel] == null) {
			throw new IllegalStateException("Invalid JPEG info: DQT 16-bit table is null");
		}
		//noinspection DataFlowIssue
		return (
				jpegInfo.dqt_tablePrecisions[tableSel] == VideoJpegInfo.QuantizationTablePrecision.INT8 ?
						jpegInfo.dqt_tables8Bit[tableSel].tableData :
						jpegInfo.dqt_tables16Bit[tableSel].tableData
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/*
	 * JPEG quantization table generation.
	 * See https://datatracker.ietf.org/doc/html/rfc2435#appendix-A
	 */

	/**
	 * Table K.1 from JPEG spec.
	 */
	private static final byte[] JPEG_LUMA_QUANTIZER = {
			16, 11, 10, 16, 24,  40,  51,  61,
			12, 12, 14, 19, 26,  58,  60,  55,
			14, 13, 16, 24, 40,  57,  69,  56,
			14, 17, 22, 29, 51,  87,  80,  62,
			18, 22, 37, 56, 68,  109, 103, 77,
			24, 35, 55, 64, 81,  104, 113, 92,
			49, 64, 78, 87, 103, 121, 120, 101,
			72, 92, 95, 98, 112, 100, 103, 99
		};

	/**
	 * Table K.2 from JPEG spec.
	 */
	private static final byte[] JPEG_CHROMA_QUANTIZER = {
			17, 18, 24, 47, 99, 99, 99, 99,
			18, 21, 26, 66, 99, 99, 99, 99,
			24, 26, 56, 99, 99, 99, 99, 99,
			47, 66, 99, 99, 99, 99, 99, 99,
			99, 99, 99, 99, 99, 99, 99, 99,
			99, 99, 99, 99, 99, 99, 99, 99,
			99, 99, 99, 99, 99, 99, 99, 99,
			99, 99, 99, 99, 99, 99, 99, 99
		};

	/**
	 * Generates a quantization table from the specified quality factor.
	 * @param q Q factor (8 bits)
	 * @param outputLqt Luma quantization table (64 bytes)
	 * @param outputCqt Chroma quantization table (64 bytes)
	 */
	@SuppressWarnings("unused")
	private static void generateQuantizationTable8bits(int q, byte[] outputLqt, byte[] outputCqt) {
		assert (q >= 0 && q <= 255);
		assert (outputLqt != null && outputLqt.length == 64);
		assert (outputCqt != null && outputCqt.length == 64);

		int i;
		int factor = q;

		if (q < 1) {
			factor = 1;
		} else if (q > 99) {
			factor = 99;
		}
		if (q < 50) {
			q = 5000 / factor;
		} else {
			q = 200 - factor * 2;
		}

		for (i = 0; i < 64; i++) {
			int lq = (JPEG_LUMA_QUANTIZER[i] * q + 50) / 100;
			int cq = (JPEG_CHROMA_QUANTIZER[i] * q + 50) / 100;

			// Limit the quantizers to 1 <= q <= 255
			if (lq < 1) {
				lq = 1;
			} else if (lq > 255) {
				lq = 255;
			}
			outputLqt[i] = (byte)lq;

			if (cq < 1) {
				cq = 1;
			} else if (cq > 255) {
				cq = 255;
			}
			outputCqt[i] = (byte)cq;
		}
	}

}

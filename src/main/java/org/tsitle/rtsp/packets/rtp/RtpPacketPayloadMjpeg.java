package org.tsitle.rtsp.packets.rtp;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.avdata.JpegInfo;

/**
 * RTP Packet Payload for MJPEG.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc2435">RFC-2435</a>
 */
public class RtpPacketPayloadMjpeg extends RtpPacketPayloadBase {

	/** Maximum width and height of an image */
	public static final int IMAGE_MAX_WIDTH_HEIGHT = 2040;

	/** Size of the main payload-specific RTP header */
	public static final int HEADER_MAIN_SIZE = 8;
	/** Size of the QT RTP header without the tables */
	public static final int HEADER_QT_PRE_SIZE = 4;

	/** Type-specific first byte (8 bits)<br />
	 *   0=Image is progressively scanned<br />
	 *   1=Image is an odd field of an interlaced video signal<br />
	 *   2=Image is an even field of an interlaced video signal<br />
	 *   3=Image is a single field from an interlaced video signal
	 */
	private final byte hdFirstByte;
	/** Fragment Offset (offset in bytes of the current packet in the JPEG frame data) (24 bits) */
	private final int hdFragmentOffset;
	/**
	 * JPEG Type (8 bits)<br />
	 *   0=YCbCr 4:2:2<br />
	 *   1=YCbCr 4:2:0
	 */
	private final byte hdType;
	/** Q value (8 bits) */
	private final byte hdQ;
	/** Image width divided by 8 pixels, max. is 255*8=2040 pixels (8 bits) */
	private final byte hdImageWidthDiv8;
	/** Image height divided by 8 pixels, max. is 255*8=2040 pixels (8 bits) */
	private final byte hdImageHeightDiv8;

	/**
	 * Constructor.
	 * @param fragmentOffset Fragment Offset (offset in bytes of the current packet in the JPEG frame data) (24 bits)
	 * @param jpegInfo JPEG info
	 * @param payloadData Payload data
	 */
	public RtpPacketPayloadMjpeg(int fragmentOffset, JpegInfo jpegInfo, BufferExt payloadData) {
		super();

		//
		if (fragmentOffset < 0 || fragmentOffset > 0xFFFFFF) {
			throw new IllegalArgumentException("Invalid fragment offset");
		}
		if (jpegInfo == null ||
				jpegInfo.sof0_imgWidth <= 0 || jpegInfo.sof0_imgWidth > IMAGE_MAX_WIDTH_HEIGHT ||
				jpegInfo.sof0_imgHeight <= 0 || jpegInfo.sof0_imgHeight > IMAGE_MAX_WIDTH_HEIGHT ||
				(jpegInfo.sof0_channelEncoding != JpegInfo.ChannelEncoding.YCBCR420 &&
						jpegInfo.sof0_channelEncoding != JpegInfo.ChannelEncoding.YCBCR422) ||
				jpegInfo.sof0_precision != 8 ||
				jpegInfo.sos_scanDataOffs < 0 || jpegInfo.sos_scanDataLength < 1 ||
				jpegInfo.sof2_isProgressive ||
				jpegInfo.dqt_table16bitCount != 0 ||
				! jpegInfo.foundEoi || jpegInfo.usesDri) {
			throw new IllegalArgumentException("Cannot process this kind of JPEG");
		}

		// set inner main header fields
		this.hdFirstByte = (byte)0;
		this.hdFragmentOffset = fragmentOffset;
		this.hdType = (byte)(jpegInfo.sof0_channelEncoding == JpegInfo.ChannelEncoding.YCBCR420 ? 1 : 0);
		this.hdQ = (byte)255;
		this.hdImageWidthDiv8 = (byte)(jpegInfo.sof0_imgWidth / 8);
		this.hdImageHeightDiv8 = (byte)(jpegInfo.sof0_imgHeight / 8);

		// build the inner header bitstream (main header + optional QT header)
		byte[] tmpRtpXxxHeader = buildRawInnerHeaderFromFields(fragmentOffset == 0, jpegInfo);
		this.rawInnerHeaderData.copyOf(tmpRtpXxxHeader);

		// copy the inner payload bitstream
		this.rawInnerPayloadData.copyOf(payloadData);
	}

	/**
	 * Constructor.
	 * @param rawInnerHeaderAndPayloadData Payload-specific header and payload of the RTP packet
	 */
	public RtpPacketPayloadMjpeg(BufferExt rawInnerHeaderAndPayloadData) {
		super();

		if (rawInnerHeaderAndPayloadData.getUsed() < HEADER_MAIN_SIZE) {
			throw new IllegalArgumentException("Invalid RTP packet size");
		}

		// parse inner main header fields
		this.hdFirstByte = rawInnerHeaderAndPayloadData.get(0);
		this.hdFragmentOffset = (((rawInnerHeaderAndPayloadData.get(1) << 16) |
				(rawInnerHeaderAndPayloadData.get(2) << 8) |
				rawInnerHeaderAndPayloadData.get(3)) & 0xFFFFFF);
		this.hdType = rawInnerHeaderAndPayloadData.get(4);
		this.hdQ = rawInnerHeaderAndPayloadData.get(5);
		this.hdImageWidthDiv8 = rawInnerHeaderAndPayloadData.get(6);
		this.hdImageHeightDiv8 = rawInnerHeaderAndPayloadData.get(7);

		// copy the inner header bitstream (main header + optional QT header)
		final int tmpQtHdLength = (this.hdFragmentOffset == 0 &&
					rawInnerHeaderAndPayloadData.getUsed() > HEADER_MAIN_SIZE + HEADER_QT_PRE_SIZE ?
				HEADER_QT_PRE_SIZE + parseInnerHeaderQuantTableLength(rawInnerHeaderAndPayloadData) : 0);
		this.rawInnerHeaderData.copyOf(
				rawInnerHeaderAndPayloadData,
				0,
				HEADER_MAIN_SIZE + tmpQtHdLength
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
		return RtpPacketType.V_JPEG;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"FirstByte: " + Integer.toUnsignedString(hdFirstByte) +
				", FragmentOffset: " + Integer.toUnsignedString(hdFragmentOffset) +
				", Type: " + Integer.toUnsignedString(hdType) +
				", Q: " + Integer.toUnsignedString(hdQ) +
				", ImageWidth: " + Integer.toUnsignedString(hdImageWidthDiv8 * 8) +
				", ImageHeight: " + Integer.toUnsignedString(hdImageHeightDiv8 * 8) +
				", innerHeaderSz: " + Integer.toUnsignedString(rawInnerHeaderData.getUsed()) +
				", innerPayloadSz: " + Integer.toUnsignedString(rawInnerPayloadData.getUsed()) +
				"]";
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static int parseInnerHeaderQuantTableLength(BufferExt data) {
		return ((((data.get(HEADER_MAIN_SIZE + 2) & 0xFF) << 8) & 0xFF00) |
				(data.get(HEADER_MAIN_SIZE + 3) & 0xFF));
	}

	// -----------------------------------------------------------------------------------------------------------------

	private byte[] buildRawInnerHeaderFromFields(boolean withQtHeader, JpegInfo jpegInfo) {
		int qtHdLength = 0;
		if (withQtHeader) {
			if (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelY] == null) {
				throw new IllegalArgumentException("Invalid JPEG info: Luma quantization table precision not found");
			}
			if (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCb] == null ||
					jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCr] == null) {
				throw new IllegalArgumentException("Invalid JPEG info: Chroma quantization table precision not found");
			}
			qtHdLength += HEADER_QT_PRE_SIZE + (
					64 * (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelY] == JpegInfo.QuantizationTablePrecision.INT8 ?
							1 : 2) +
					64 * (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCb] == JpegInfo.QuantizationTablePrecision.INT8 ?
							1 : 2)
				);
		}
		final int completeHdLength = HEADER_MAIN_SIZE + qtHdLength;
		final byte[] resA = new byte[completeHdLength];

		// main RTP/JPEG header
		resA[0] = hdFirstByte;
		resA[1] = (byte)((hdFragmentOffset >> 16) & 0xFF);
		resA[2] = (byte)((hdFragmentOffset >> 8) & 0xFF);
		resA[3] = (byte)(hdFragmentOffset & 0xFF);
		resA[4] = hdType;
		resA[5] = hdQ;
		resA[6] = hdImageWidthDiv8;
		resA[7] = hdImageHeightDiv8;

		// The JPEG Quantization Table RTP header is only present in the first packet of a frame
		if (! withQtHeader) {
			return resA;
		}
		// Quantization Table: JPEG type 0 and 1 use two tables: one for
		//   the luminance component and one shared by the chrominance components.
		//   Each table is an array of 64 values.
		///
		int offs = HEADER_MAIN_SIZE;
		/// MBZ - purpose unknown (8 bits)
		resA[offs++] = 0;
		/// Precision (8 bits): the Precision field specifies the size of the coefficients in the table.
		///   The lowest bit corresponds to the first table. The second bit corresponds to the second table.
		///   If a bit is zero, the coefficients are 8 bits yielding a table length of 64 bytes.
		///   If a bit is one, the coefficients are 16 bits for a table length of 128 bytes.
		resA[offs++] = (byte)(
				(jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelY] == JpegInfo.QuantizationTablePrecision.INT8 ?
						0 : 1) |
				(jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCb] == JpegInfo.QuantizationTablePrecision.INT8 ?
						0 : 2)
			);
		/// QT Table Length (16 bits)
		final int tmpHdLength = (
				64 * (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelY] == JpegInfo.QuantizationTablePrecision.INT8 ?
						1 : 2) +
				64 * (jpegInfo.dqt_tablePrecisions[jpegInfo.sof0_quantTableSelCb] == JpegInfo.QuantizationTablePrecision.INT8 ?
						1 : 2)
			);
		resA[offs++] = (byte)((tmpHdLength >> 8) & 0xFF);
		resA[offs++] = (byte)(tmpHdLength & 0xFF);
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
		if (outputLqt.length + outputCqt.length != tmpHdLength) {
			throw new IllegalArgumentException("Invalid JPEG info: Invalid Quantization table sizes");
		}
		System.arraycopy(outputLqt, 0, resA, offs, outputLqt.length);
		offs += outputLqt.length;
		System.arraycopy(outputCqt, 0, resA, offs, outputCqt.length);

		return resA;
	}

	private static byte[] getQuantizationTableData(JpegInfo jpegInfo, byte tableSel) {
		return (
				jpegInfo.dqt_tablePrecisions[tableSel] == JpegInfo.QuantizationTablePrecision.INT8 ?
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

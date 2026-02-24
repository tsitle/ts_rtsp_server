package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.util.Objects;

public final class VideoJpegParser {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull String logThreadId;
	private long debugStreamOffset = 0;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param logThreadId Thread ID for logging messages
	 */
	public VideoJpegParser(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull String logThreadId
			) {
		this.logMsgInterface = logMsgInterface;
		this.logThreadId = logThreadId;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the JPEG data and returns a JpegInfo object with the parsed information.
	 * @param jpegBuf JPEG data
	 * @param debugStreamOffset Offset of the JPEG data in the MJPEG stream (used for error messages)
	 * @return Parsed JPEG information
	 */
	public @NonNull VideoJpegInfo parseJpegData(long debugStreamOffset, @NonNull BufferExt jpegBuf)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = VideoJpegParser.class.getSimpleName() + ".parseJpegData()";

		this.debugStreamOffset = debugStreamOffset;

		//
		VideoJpegInfo resObj = new VideoJpegInfo();

		int offs = 0;
		if (jpegBuf.getUsed() < 4) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG data size");
		}
		// find SOI marker (Start of Image: 0xFFD8)
		while (offs + 1 < jpegBuf.getUsed()) {
			if (jpegBuf.get(offs) != (byte)0xFF || jpegBuf.get(offs + 1) != (byte)0xD8) {
				logError(FNC_NAME, String.format("skipping invalid JPEG data @ 0x%08X: 0x%02X 0x%02X%n",
						offs + debugStreamOffset, jpegBuf.get(offs), jpegBuf.get(offs + 1)));
				if (offs > 20) {
					throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG data - too much garbage");
				}
				++offs;
				continue;
			}
			break;
		}
		offs += 2;
		while (offs + 1 < jpegBuf.getUsed()) {
			byte marker = jpegBuf.get(offs++);
			if (marker != (byte)0xFF) {
				throw new AvInvalidCodecDataException(
						String.format("%s: Invalid JPEG data - invalid marker @ 0x%08X: 0x%02X",
								FNC_NAME, offs + debugStreamOffset - 1, marker)
					);
			}
			int curBlockOffset = offs - 1;
			marker = jpegBuf.get(offs++);

			if (marker == (byte)0xC0) {
				// SOF0 marker (Start of Frame - Baseline DCT: 0xFFC0)
				offs = parseBlockSOF0(jpegBuf, resObj, curBlockOffset);
				continue;
			}
			if (marker == (byte)0xDA) {
				// SOS marker (Start of Scan: 0xFFDA) followed immediately by the entropy-coded scan data
				offs = parseBlockSOS(jpegBuf, resObj, curBlockOffset);
				continue;
			}
			if (marker == (byte)0xDB) {
				// DQT marker (Define Quantization Table: 0xFFDB)
				offs = parseBlockDQT(jpegBuf, resObj, curBlockOffset);
				continue;
			}

			if (marker == (byte)0xD9) {
				// EOI marker (End of Image: 0xFFD9)
				//logDebug(FNC_NAME, curBlockOffset, "EOI");
				resObj.foundEoi = true;
				break;
			}

			if (marker == (byte)0xC2) {
				// SOF2 marker (progressive: 0xFFC2)
				resObj.sof2_isProgressive = true;
				//logDebug(FNC_NAME, curBlockOffset, "SOF2");
			} else if (marker == (byte)0xC4) {
				// DHT marker (Define Huffman Table: 0xFFC4)
				++resObj.dht_tableCount;
				//logDebug(FNC_NAME, curBlockOffset, "DHT");
			} else if (marker >= (byte)0xD0 && marker <= (byte)0xD7) {
				// Restart marker (if DRI is used: 0xFFD0..FFD7) - this probably can only occur inside the scan data
				logDebug(FNC_NAME, curBlockOffset,
						String.format("RESTART(#%d,0x%02X)", marker - (byte)0xD0, marker));  // @TODO
			} else if (marker == (byte)0xDD) {
				// DRI marker (Define Restart Interval: 0xFFDD)
				resObj.usesDri = true;
				//logDebug(FNC_NAME, curBlockOffset, "DRI");
			} else if (marker >= (byte)0xE0 && marker <= (byte)0xEF) {
				// APPn marker (JFIF/EXIF/etc.: 0xFFE0..FFEF)
				++resObj.app_blockCount;
				/*logDebug(FNC_NAME, curBlockOffset,
						String.format("APP(#%d,0x%02X)", marker - (byte)0xE0, marker));*/
			} else if (marker == (byte)0xFE) {
				// COM marker (0xFFFE)
				resObj.foundCom = true;
				//logDebug(FNC_NAME, curBlockOffset, "COM");
			} else {
				// unknown marker
				logError(FNC_NAME, String.format("__ unknown marker @ 0x%08X: 0xFF 0x%02X%n",
						debugStreamOffset + curBlockOffset, marker));
			}

			// skip over the block data
			int blockLen = parseBlockLength(jpegBuf, curBlockOffset);
			offs += 2 + blockLen;
		}
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the length of a block.
	 * @param jpegBuf JPEG data
	 * @param blockOffset Start offset of the block marker
	 * @return Block length
	 */
	private int parseBlockLength(@NonNull BufferExt jpegBuf, final int blockOffset)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = VideoJpegParser.class.getSimpleName() + ".parseBlockLength()";

		if (blockOffset + 4 >= jpegBuf.getUsed()) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG data size");
		}
		int curOffs = blockOffset + 2;
		// the block length includes the two bytes for the length itself
		int blockLen = ( ( ((jpegBuf.get(curOffs) << 8) & 0xFF00) | (jpegBuf.get(curOffs + 1) & 0xFF) ) & 0xFFFF);
		if (blockLen < 2) {
			throw new AvInvalidCodecDataException(
					String.format("%s: Invalid JPEG data - invalid block length %d @ 0x%08X",
							FNC_NAME, blockLen, debugStreamOffset + curOffs)
				);
		}
		if (blockOffset + 2 + blockLen >= jpegBuf.getUsed()) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG data - data too small");
		}
		//logDebug(FNC_NAME, curOffs, String.format("__ blockLen %d", blockLen));
		return blockLen - 2;
	}

	/**
	 * Parses the SOS (Start of Scan: 0xFFDA) block.
	 * @param jpegBuf JPEG data
	 * @param jpegInfo Parsed JPEG information
	 * @param blockOffset Start offset of the block marker
	 * @return Offset after the block in the JPEG data
	 */
	private int parseBlockSOS(@NonNull BufferExt jpegBuf, @NonNull VideoJpegInfo jpegInfo, final int blockOffset)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = VideoJpegParser.class.getSimpleName() + ".parseBlockSOS()";

		/*
		 * After the SOS marker, one cannot use a length field to skip the entire scan data.
		 * We can only skip over the SOS block header and then find the end of the scan data.
		 * The scan data ends when the decoder encounters the next marker, using “byte stuffing” rules.
		 * The next marker is an 0xFF followed by a byte that is not 0x00.
		 */
		//logDebug(FNC_NAME, blockOffset, "SOS");
		int blockLen = parseBlockLength(jpegBuf, blockOffset);
		int curOffs = blockOffset + 2 + 2 + blockLen;
		// now find the end of the scan data
		jpegInfo.sos_scanDataOffs = curOffs;
		while (curOffs + 1 < jpegBuf.getUsed()) {
			if (jpegBuf.get(curOffs) == (byte)0xFF &&
					jpegBuf.get(curOffs + 1) != (byte)0x00 &&
					(jpegBuf.get(curOffs + 1) < (byte)0xD0 || jpegBuf.get(curOffs + 1) > (byte)0xD7)) {
				break;
			}
			if (jpegBuf.get(curOffs) == (byte)0xFF &&
					jpegBuf.get(curOffs + 1) >= (byte)0xD0 && jpegBuf.get(curOffs + 1) <= (byte)0xD7) {
				jpegInfo.usesDri = true;
				logDebug(FNC_NAME, curOffs,
						String.format("RESTART(#%d,0x%02X)", jpegBuf.get(curOffs + 1) - (byte)0xD0, jpegBuf.get(curOffs + 1)));  // @TODO
			}
			++curOffs;
		}
		jpegInfo.sos_scanDataLength = curOffs - jpegInfo.sos_scanDataOffs;
		//logDebug(FNC_NAME, curOffs, String.format("__ SOS skipped over %d bytes", jpegInfo.sos_scanDataLength));
		return curOffs;
	}

	/**
	 * Parses the SOF0 (Start of Frame - Baseline DCT: 0xFFC0) block.
	 * @param jpegBuf JPEG data
	 * @param jpegInfo Parsed JPEG information
	 * @param blockOffset Start offset of the block marker
	 * @return Offset after the block in the JPEG data
	 */
	private int parseBlockSOF0(@NonNull BufferExt jpegBuf, @NonNull VideoJpegInfo jpegInfo, final int blockOffset)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = VideoJpegParser.class.getSimpleName() + ".parseBlockSOF0()";

		//logDebug(FNC_NAME, blockOffset, "SOF0");
		int blockLen = parseBlockLength(jpegBuf, blockOffset);
		int curOffs = blockOffset + 2 + 2;

		if (blockLen < 6) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG block size");
		}

		jpegInfo.sof0_hasBaselineDCT = true;

		int innerOffs = curOffs;

		// precision field
		jpegInfo.sof0_precision = jpegBuf.get(innerOffs++);
		//logDebug(FNC_NAME, innerOffs - 1, String.format("__ prec %d", jpegInfo.sof0_precision));

		// image dimensions
		jpegInfo.sof0_imgHeight = ( ( ((jpegBuf.get(innerOffs++) << 8) & 0xFF00) | (jpegBuf.get(innerOffs++) & 0xFF) ) & 0xFFFF);
		jpegInfo.sof0_imgWidth = ( ( ((jpegBuf.get(innerOffs++) << 8) & 0xFF00) | (jpegBuf.get(innerOffs++) & 0xFF) ) & 0xFFFF);
		//logDebug(FNC_NAME, innerOffs - 4, String.format("__ image %d x %d", jpegInfo.sof0_imgWidth, jpegInfo.sof0_imgHeight));

		// channel encoding (e.g. 'YCbCr 4:2:0')
		byte paramNf = jpegBuf.get(innerOffs++);
		//logDebug(FNC_NAME, innerOffs - 1, String.format("__ Nf %d", paramNf));
		if (blockLen < 6 + (paramNf * 3)) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG block size");
		}
		byte tmpMaxH = 0;
		byte tmpMaxV = 0;
		for (byte componentIx = 0; componentIx < paramNf; ++componentIx) {
			byte componentId = jpegBuf.get(innerOffs++);
			if (componentId < 0 || componentId > 3) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid component ID");
			}
			/*
			 * We are going to assume that the components come in the order Y, Cb, Cr - regardless of ID value.
			 */
			byte componentTmpHiVi = jpegBuf.get(innerOffs++);
			byte componentHi = (byte)((componentTmpHiVi >> 4) & 0x0F);
			tmpMaxH = (byte)(Math.max(tmpMaxH, componentHi));
			byte componentVi = (byte)(componentTmpHiVi & 0x0F);
			tmpMaxV = (byte)(Math.max(tmpMaxV, componentVi));
			byte componentQuantTableSel = jpegBuf.get(innerOffs++);
			/*String tmpDebugCompName = switch (componentIx) { case 0 -> "Y"; case 1 -> "Cb"; default -> "Cr"; };
			logDebug(FNC_NAME, innerOffs - 3,
					String.format("__ Ci %d (%s), HiVi %d (%d / %d), Tqi %d",
							componentId, tmpDebugCompName,
							componentTmpHiVi, componentHi, componentVi,
							componentQuantTableSel));*/
			if (componentQuantTableSel < 0 || componentQuantTableSel > 3) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid componentQuantTableSel");
			}
			switch (componentIx) {
				case 0 -> jpegInfo.sof0_quantTableSelY = componentQuantTableSel;
				case 1 -> jpegInfo.sof0_quantTableSelCb = componentQuantTableSel;
				default -> jpegInfo.sof0_quantTableSelCr = componentQuantTableSel;
			}
		}
		if (tmpMaxH == 4 && tmpMaxV == 1) {
			jpegInfo.sof0_channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR411;
		} else if (tmpMaxH == 2 && tmpMaxV == 2) {
			jpegInfo.sof0_channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR420;
		} else if (tmpMaxH == 2 && tmpMaxV == 1) {
			jpegInfo.sof0_channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR422;
		} else if (tmpMaxH == 1 && tmpMaxV == 2) {
			jpegInfo.sof0_channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR440;
		} else if (tmpMaxH == 1 && tmpMaxV == 1) {
			jpegInfo.sof0_channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR444;
		} else {
			jpegInfo.sof0_channelEncoding = VideoJpegInfo.ChannelEncoding.UNKNOWN;
		}
		/*logDebug(FNC_NAME, innerOffs,
				String.format(
						"__ CE %s (QT Y=%d, Cb=%d, Cr=%d)",
						jpegInfo.sof0_channelEncoding.name(),
						jpegInfo.sof0_quantTableSelY, jpegInfo.sof0_quantTableSelCb, jpegInfo.sof0_quantTableSelCr));*/

		//
		if (curOffs + blockLen != innerOffs) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": sanity check failed");
		}

		return curOffs + blockLen;
	}

	/**
	 * Parses the DQT (Define Quantization Table: 0xFFDB) block.
	 * @param jpegBuf JPEG data
	 * @param jpegInfo Parsed JPEG information
	 * @param blockOffset Start offset of the block marker
	 * @return Offset after the block in the JPEG data
	 */
	private int parseBlockDQT(@NonNull BufferExt jpegBuf, @NonNull VideoJpegInfo jpegInfo, final int blockOffset)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = VideoJpegParser.class.getSimpleName() + ".parseBlockDQT()";

		//logDebug(FNC_NAME, blockOffset, "DQT");
		int blockLen = parseBlockLength(jpegBuf, blockOffset);
		int curOffs = blockOffset + 2 + 2;

		int innerOffs = curOffs;

		//
		byte tmpPqTq = jpegBuf.get(innerOffs++);
		byte tmpPq = (byte)((tmpPqTq >> 4) & 0x0F);  // Pq=0 for 8-bit, Pq=1 for 16-bit quantization tables
		if (tmpPq != 0 && tmpPq != 1) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Unsupported DQT Table Precision");
		}
		byte tmpTq = (byte)(tmpPqTq & 0x0F);  // Table ID
		if ((tmpPq == 0 && tmpTq >= jpegInfo.dqt_tables8Bit.length) ||
				(tmpPq == 1 && tmpTq >= jpegInfo.dqt_tables16Bit.length)) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid DQT Table ID");
		}
		if (jpegInfo.dqt_tablePrecisions[tmpTq] != null) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Duplicate DQT table");
		}
		if (jpegInfo.dqt_tables8Bit[tmpTq] != null || jpegInfo.dqt_tables16Bit[tmpTq] != null) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Duplicate DQT table");
		}
		if (tmpPq == 0) {
			jpegInfo.dqt_tablePrecisions[tmpTq] = VideoJpegInfo.QuantizationTablePrecision.INT8;
			if (jpegInfo.dqt_table8bitCount >= jpegInfo.dqt_tables8Bit.length) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Too many DQT tables");
			}
			jpegInfo.dqt_tables8Bit[tmpTq] = new VideoJpegInfo.DqtTable8Bit(tmpTq);
		} else {
			jpegInfo.dqt_tablePrecisions[tmpTq] = VideoJpegInfo.QuantizationTablePrecision.INT16;
			if (jpegInfo.dqt_table16bitCount >= jpegInfo.dqt_tables16Bit.length) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Too many DQT tables");
			}
			jpegInfo.dqt_tables16Bit[tmpTq] = new VideoJpegInfo.DqtTable16Bit(tmpTq);
		}
		if ((tmpPq == 0 && blockLen != Objects.requireNonNull(jpegInfo.dqt_tables8Bit[tmpTq]).tableData.length + 1) ||
				(tmpPq == 1 && blockLen != Objects.requireNonNull(jpegInfo.dqt_tables16Bit[tmpTq]).tableData.length + 1)) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG block size");
		}
		//logDebug(FNC_NAME, innerOffs - 1, String.format("__ table ID %d", tmpTq));

		int copyLen = (tmpPq == 0 ?
				Objects.requireNonNull(jpegInfo.dqt_tables8Bit[tmpTq]).tableData.length
				: Objects.requireNonNull(jpegInfo.dqt_tables16Bit[tmpTq]).tableData.length);
		jpegBuf.copyInto(
				innerOffs,
				tmpPq == 0 ?
						Objects.requireNonNull(jpegInfo.dqt_tables8Bit[tmpTq]).tableData
						: Objects.requireNonNull(jpegInfo.dqt_tables16Bit[tmpTq]).tableData,
				0,
				copyLen
			);
		innerOffs += copyLen;

		if (tmpPq == 0) {
			++jpegInfo.dqt_table8bitCount;
		} else {
			++jpegInfo.dqt_table16bitCount;
		}

		//
		if (curOffs + blockLen != innerOffs) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": sanity check failed");
		}

		return curOffs + blockLen;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, int offset, @NonNull String msg) {
		if (debugStreamOffset != 0) { return; }
		logMsgInterface.addMsgForLogThread(
				RtxpLogLevel.DEBUG,
				logThreadId,
				String.format("%s: __ @ 0x%08X: %s", fncName, debugStreamOffset + offset, msg)
			);
	}

	private void logError(@NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(
				RtxpLogLevel.ERROR,
				logThreadId,
				String.format("%s: %s", fncName, msg)
			);
	}

}

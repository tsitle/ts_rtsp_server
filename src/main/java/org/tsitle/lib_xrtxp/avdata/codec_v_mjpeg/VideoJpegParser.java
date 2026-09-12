package org.tsitle.lib_xrtxp.avdata.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;

/**
 * JPEG parser for video data.<br />
 * See <a href="https://workspace.hackschule.de/jpeg">Decoding JPEG Images</a>
 * and <a href="https://www.ccoderun.ca/programming/2017-01-31_jpeg/">Anatomy of a JPEG</a>
 */
public final class VideoJpegParser {

	public static final byte[] MJPEG_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xD8};
	public static final String MJPEG_HUFFMAN_TABLES_DEFAULT_HASH = "1A799F2EB783C160690792564D0893B1";

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
	 * @param inputBv JPEG data
	 * @param debugStreamOffset Offset of the JPEG data in the MJPEG stream (used for error messages)
	 * @return Parsed JPEG information
	 */
	public @NonNull VideoJpegInfo parseJpegData(long debugStreamOffset, @NonNull BufferView inputBv)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseJpegData()";

		this.debugStreamOffset = debugStreamOffset;

		//
		VideoJpegInfo resObj = new VideoJpegInfo();

		int offs = 0;
		if (inputBv.getLength() < 4) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG data size");
		}
		// find SOI marker (Start of Image: 0xFFD8)
		while (offs + 1 < inputBv.getLength()) {
			if (inputBv.getByte(offs) != MJPEG_FRAME_START_MAGICBYTES[0] ||
					inputBv.getByte(offs + 1) != MJPEG_FRAME_START_MAGICBYTES[1]) {
				logError(FNC_NAME, String.format("skipping invalid JPEG data @ 0x%08X: 0x%02X 0x%02X%n",
						offs + debugStreamOffset, inputBv.getByte(offs), inputBv.getByte(offs + 1)));
				if (offs > 20) {
					throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG data - too much garbage");
				}
				++offs;
				continue;
			}
			break;
		}
		offs += 2;
		while (offs + 1 < inputBv.getLength()) {
			byte marker = inputBv.getByte(offs++);
			if (marker != (byte)0xFF) {
				throw new AvInvalidCodecDataException(
						String.format("%s: Invalid JPEG data - invalid marker @ 0x%08X: 0x%02X",
								FNC_NAME, offs + debugStreamOffset - 1, marker)
					);
			}
			int curBlockOffset = offs - 1;
			marker = inputBv.getByte(offs++);

			if (marker == (byte)0xC0) {
				// SOF0 marker (Start of Frame - Baseline DCT: 0xFFC0)
				offs = parseBlockSOF0(inputBv, resObj, curBlockOffset);
				continue;
			}
			if (marker == (byte)0xDA) {
				// SOS marker (Start of Scan: 0xFFDA) followed immediately by the entropy-coded scan data
				offs = parseBlockSOS(inputBv, resObj, curBlockOffset);
				continue;
			}
			if (marker == (byte)0xDB) {
				// DQT marker (Define Quantization Table: 0xFFDB)
				offs = parseBlockDQT(inputBv, resObj, curBlockOffset);
				continue;
			}
			if (marker == (byte)0xC4) {
				// DHT marker (Define Huffman Table: 0xFFC4)
				offs = parseBlockDHT(inputBv, resObj, curBlockOffset);
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
				resObj.segmSOF2.isProgressive = true;
				//logDebug(FNC_NAME, curBlockOffset, "SOF2");
			} else if (marker >= (byte)0xD0 && marker <= (byte)0xD7) {
				// Restart marker (if DRI is used: 0xFFD0..FFD7) - this probably can only occur inside the scan data
				logDebug(FNC_NAME, curBlockOffset,
						String.format("RESTART(#%d,0x%02X)", marker - (byte)0xD0, marker));
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
			int blockLen = parseBlockLength(inputBv, curBlockOffset);
			offs += 2 + blockLen;
		}
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the length of a block.
	 * @param inputBv JPEG data
	 * @param blockOffset Start offset of the block marker
	 * @return Block length
	 */
	private int parseBlockLength(@NonNull BufferView inputBv, final int blockOffset)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseBlockLength()";

		if (blockOffset + 4 >= inputBv.getLength()) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG data size");
		}
		int curOffs = blockOffset + 2;
		// the block length includes the two bytes for the length itself
		int blockLen = ( ( ((inputBv.getByte(curOffs) << 8) & 0xFF00) | (inputBv.getByte(curOffs + 1) & 0xFF) ) & 0xFFFF);
		if (blockLen < 2) {
			throw new AvInvalidCodecDataException(
					String.format("%s: Invalid JPEG data - invalid block length %d @ 0x%08X",
							FNC_NAME, blockLen, debugStreamOffset + curOffs)
				);
		}
		if (blockOffset + 2 + blockLen >= inputBv.getLength()) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG data - data too small");
		}
		//logDebug(FNC_NAME, curOffs, String.format("__ blockLen %d", blockLen));
		return blockLen - 2;
	}

	/**
	 * Parses the SOS (Start of Scan: 0xFFDA) block.
	 * @param inputBv JPEG data
	 * @param jpegInfo Parsed JPEG information
	 * @param blockOffset Start offset of the block marker
	 * @return Offset after the block in the JPEG data
	 */
	private int parseBlockSOS(@NonNull BufferView inputBv, @NonNull VideoJpegInfo jpegInfo, final int blockOffset)
			throws AvInvalidCodecDataException {
		//final String FNC_NAME = getClass().getSimpleName() + ".parseBlockSOS()";

		/*
		 * After the SOS marker, one cannot use a length field to skip the entire scan data.
		 * We can only skip over the SOS block header and then find the end of the scan data.
		 * The scan data ends when the decoder encounters the next marker, using “byte stuffing” rules.
		 * The next marker is an 0xFF followed by a byte that is not 0x00.
		 */
		//logDebug(FNC_NAME, blockOffset, "SOS");
		int blockLen = parseBlockLength(inputBv, blockOffset);
		parseBlockSOS_fields(inputBv, jpegInfo, blockOffset + 2 + 2);
		int curOffs = blockOffset + 2 + 2 + blockLen;
		// now find the end of the scan data
		jpegInfo.segmSOS.scanDataOffs = curOffs;
		while (curOffs + 1 < inputBv.getLength()) {
			if (inputBv.getByte(curOffs) == (byte)0xFF &&
					inputBv.getByte(curOffs + 1) != (byte)0x00 &&
					(inputBv.getByte(curOffs + 1) < (byte)0xD0 || inputBv.getByte(curOffs + 1) > (byte)0xD7)) {
				break;
			}
			if (inputBv.getByte(curOffs) == (byte)0xFF &&
					inputBv.getByte(curOffs + 1) >= (byte)0xD0 && inputBv.getByte(curOffs + 1) <= (byte)0xD7) {
				jpegInfo.usesDri = true;
				/*logDebug(FNC_NAME, curOffs,
						String.format("RESTART(#%d,0x%02X)", inputBv.getByte(curOffs + 1) - (byte)0xD0, inputBv.getByte(curOffs + 1)));*/
			}
			++curOffs;
		}
		jpegInfo.segmSOS.scanDataLength = curOffs - jpegInfo.segmSOS.scanDataOffs;
		//logDebug(FNC_NAME, curOffs, String.format("__ SOS skipped over %d bytes", jpegInfo.segmSOS.scanDataLength));
		return curOffs;
	}

	private void parseBlockSOS_fields(@NonNull BufferView inputBv, @NonNull VideoJpegInfo jpegInfo, int fieldsOffset) {
		// number of components (1=monochrome, 3=color)
		jpegInfo.segmSOS.numComp = inputBv.getByte(fieldsOffset++);

		// Y component ID
		jpegInfo.segmSOS.compId_y = inputBv.getByte(fieldsOffset++);
		// Y huffman DC and AC table selectors
		byte tmpHuffTblIdAndClass = inputBv.getByte(fieldsOffset++);
		jpegInfo.segmSOS.huff_y_dc = (byte)((tmpHuffTblIdAndClass >> 4) & 0x0F);
		jpegInfo.segmSOS.huff_y_ac = (byte)(tmpHuffTblIdAndClass & 0x0F);

		// CB component ID
		jpegInfo.segmSOS.compId_cb = inputBv.getByte(fieldsOffset++);
		// CB huffman DC and AC table selectors
		tmpHuffTblIdAndClass = inputBv.getByte(fieldsOffset++);
		jpegInfo.segmSOS.huff_cb_dc = (byte)((tmpHuffTblIdAndClass >> 4) & 0x0F);
		jpegInfo.segmSOS.huff_cb_ac = (byte)(tmpHuffTblIdAndClass & 0x0F);

		// CR component ID
		jpegInfo.segmSOS.compId_cr = inputBv.getByte(fieldsOffset++);
		// CR huffman DC and AC table selectors
		tmpHuffTblIdAndClass = inputBv.getByte(fieldsOffset++);
		jpegInfo.segmSOS.huff_cr_dc = (byte)((tmpHuffTblIdAndClass >> 4) & 0x0F);
		jpegInfo.segmSOS.huff_cr_ac = (byte)(tmpHuffTblIdAndClass & 0x0F);

		// start of spectral selection or predictor selection (should be 0x00)
		jpegInfo.segmSOS.startSpectralSel = inputBv.getByte(fieldsOffset++);
		// end of spectral selection (should be 0x3F)
		jpegInfo.segmSOS.endSpectralSel = inputBv.getByte(fieldsOffset++);
		// successive approximation bit position or point transform (should be 0x00)
		jpegInfo.segmSOS.successiveApproxBitPos = inputBv.getByte(fieldsOffset);
	}

	/*private int dbgPktNum = 0;*/

	/**
	 * Parses the SOF0 (Start of Frame - Baseline DCT: 0xFFC0) block.
	 * @param inputBv JPEG data
	 * @param jpegInfo Parsed JPEG information
	 * @param blockOffset Start offset of the block marker
	 * @return Offset after the block in the JPEG data
	 */
	private int parseBlockSOF0(@NonNull BufferView inputBv, @NonNull VideoJpegInfo jpegInfo, final int blockOffset)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseBlockSOF0()";

		//logDebug(FNC_NAME, blockOffset, "SOF0");
		int blockLen = parseBlockLength(inputBv, blockOffset);
		int curOffs = blockOffset + 2 + 2;

		if (blockLen < 6) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG block size (is=" + blockLen + ", min=6)");
		}

		jpegInfo.segmSOF0.hasBaselineDCT = true;

		int innerOffs = curOffs;

		// precision field
		jpegInfo.segmSOF0.precision = inputBv.getByte(innerOffs++);
		//logDebug(FNC_NAME, innerOffs - 1, String.format("__ prec %d", jpegInfo.segmSOF0.precision));

		// image dimensions
		jpegInfo.segmSOF0.imgHeight = ( ( ((inputBv.getByte(innerOffs++) << 8) & 0xFF00) | (inputBv.getByte(innerOffs++) & 0xFF) ) & 0xFFFF);
		jpegInfo.segmSOF0.imgWidth = ( ( ((inputBv.getByte(innerOffs++) << 8) & 0xFF00) | (inputBv.getByte(innerOffs++) & 0xFF) ) & 0xFFFF);
		//logDebug(FNC_NAME, innerOffs - 4, String.format("__ image %d x %d", jpegInfo.segmSOF0.imgWidth, jpegInfo.segmSOF0.imgHeight));

		// channel encoding (e.g. 'YCbCr 4:2:0')
		byte paramNf = inputBv.getByte(innerOffs++);
		//logDebug(FNC_NAME, innerOffs - 1, String.format("__ Nf %d", paramNf));
		if (paramNf < 1) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid number of components");
		}
		if (blockLen < 6 + (paramNf * 3)) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG block size " +
					"(is=" + blockLen + ", min=" + (6 + (paramNf * 3)) + ", nf=" + paramNf + ")");
		}

		byte yH = 0, yV = 0;
		byte cbH = 0, cbV = 0;
		byte crH = 0, crV = 0;

		for (byte componentIx = 0; componentIx < paramNf; ++componentIx) {
			byte componentId = inputBv.getByte(innerOffs++);
			if (componentId < 0 || componentId > 3) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid component ID");
			}
			/*
			 * We are going to assume that the components come in the order Y, Cb, Cr - regardless of ID value.
			 */
			byte componentTmpHiVi = inputBv.getByte(innerOffs++);
			byte componentHi = (byte)((componentTmpHiVi >> 4) & 0x0F);
			byte componentVi = (byte)(componentTmpHiVi & 0x0F);
			if (componentHi < 1 || componentVi < 1) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid sampling factors");
			}

			switch (componentIx) {
				case 0 -> { yH = componentHi; yV = componentVi; }
				case 1 -> { cbH = componentHi; cbV = componentVi; }
				case 2 -> { crH = componentHi; crV = componentVi; }
				default -> { }
			}

			byte componentQuantTableSel = inputBv.getByte(innerOffs++);
			/*if (dbgPktNum == 0) {
				String tmpDebugCompName = switch (componentIx) { case 0 -> "Y"; case 1 -> "Cb"; default -> "Cr"; };
				logDebug(FNC_NAME, innerOffs - 3,
						String.format("__ Ci %d (%s), HiVi 0x%02x (0x%02x / 0x%02x), Tqi %d",
								componentId, tmpDebugCompName,
								componentTmpHiVi, componentHi, componentVi,
								componentQuantTableSel));
			}*/
			if (componentQuantTableSel < 0 || componentQuantTableSel > 3) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid componentQuantTableSel");
			}
			switch (componentIx) {
				case 0 -> jpegInfo.segmSOF0.quantTableSelY = componentQuantTableSel;
				case 1 -> jpegInfo.segmSOF0.quantTableSelCb = componentQuantTableSel;
				default -> jpegInfo.segmSOF0.quantTableSelCr = componentQuantTableSel;
			}
		}
		/*++dbgPktNum;*/

		jpegInfo.segmSOF0.channelEncoding = VideoJpegInfo.ChannelEncoding.UNKNOWN;
		if (paramNf >= 3 && cbH == crH && cbV == crV && cbH > 0 && cbV > 0) {
			if ((yH % cbH) == 0 && (yV % cbV) == 0) {
				int hSub = yH / cbH;
				int vSub = yV / cbV;

				if (hSub == 4 && vSub == 1) {
					jpegInfo.segmSOF0.channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR411;
				} else if (hSub == 2 && vSub == 2) {
					jpegInfo.segmSOF0.channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR420;
				} else if (hSub == 2 && vSub == 1) {
					jpegInfo.segmSOF0.channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR422;
				} else if (hSub == 1 && vSub == 2) {
					jpegInfo.segmSOF0.channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR440;
				} else if (hSub == 1 && vSub == 1) {
					jpegInfo.segmSOF0.channelEncoding = VideoJpegInfo.ChannelEncoding.YCBCR444;
				}
			}
		}

		/*logDebug(FNC_NAME, innerOffs,
				String.format(
						"__ CE %s (QT Y=%d, Cb=%d, Cr=%d)",
						jpegInfo.segmSOF0.channelEncoding.name(),
						jpegInfo.segmSOF0.quantTableSelY, jpegInfo.segmSOF0.quantTableSelCb, jpegInfo.segmSOF0.quantTableSelCr));*/

		//
		if (curOffs + blockLen != innerOffs) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": sanity check failed");
		}

		return curOffs + blockLen;
	}

	/**
	 * Parses the DQT (Define Quantization Table: 0xFFDB) block.
	 * @param inputBv JPEG data
	 * @param jpegInfo Parsed JPEG information
	 * @param blockOffset Start offset of the block marker
	 * @return Offset after the block in the JPEG data
	 */
	private int parseBlockDQT(@NonNull BufferView inputBv, @NonNull VideoJpegInfo jpegInfo, final int blockOffset)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseBlockDQT()";

		//logDebug(FNC_NAME, blockOffset, "DQT");
		int blockLen = parseBlockLength(inputBv, blockOffset);
		int curOffs = blockOffset + 2 + 2;

		int innerOffs = curOffs;

		//
		do {
			byte tmpPqTq = inputBv.getByte(innerOffs++);
			byte tmpPq = (byte) ((tmpPqTq >> 4) & 0x0F);  // Pq=0 for 8-bit, Pq=1 for 16-bit quantization tables
			if (tmpPq != 0 && tmpPq != 1) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Unsupported DQT Table Precision");
			}
			byte tmpTq = (byte) (tmpPqTq & 0x0F);  // Table ID
			//logDebug(FNC_NAME, blockOffset, "DQT - Table ID: " + tmpTq);
			if (jpegInfo.segmDQT.tablePrecisionsMap.containsKey(tmpTq)) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Duplicate DQT table");
			}
			if (jpegInfo.segmDQT.tables8BitMap.containsKey(tmpTq) || jpegInfo.segmDQT.tables16BitMap.containsKey(tmpTq)) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Duplicate DQT table");
			}
			if (tmpPq == 0) {
				jpegInfo.segmDQT.tablePrecisionsMap.put(tmpTq, VideoJpegInfo.QuantizationTablePrecision.INT8);
				jpegInfo.segmDQT.tables8BitMap.put(tmpTq, new VideoJpegInfo.DqtTable8Bit(tmpTq));
			} else {
				jpegInfo.segmDQT.tablePrecisionsMap.put(tmpTq, VideoJpegInfo.QuantizationTablePrecision.INT16);
				jpegInfo.segmDQT.tables16BitMap.put(tmpTq, new VideoJpegInfo.DqtTable16Bit(tmpTq));
			}
			if ((tmpPq == 0 && blockLen != jpegInfo.segmDQT.tables8BitMap.get(tmpTq).getTableDataPtr().length + 1) ||
					(tmpPq == 1 && blockLen != jpegInfo.segmDQT.tables16BitMap.get(tmpTq).getTableDataPtr().length + 1)) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid JPEG block size");
			}
			//logDebug(FNC_NAME, innerOffs - 1, String.format("__ table ID %d", tmpTq));

			byte[] tmpTargetPtr = (tmpPq == 0 ?
					jpegInfo.segmDQT.tables8BitMap.get(tmpTq).getTableDataPtr()
					: jpegInfo.segmDQT.tables16BitMap.get(tmpTq).getTableDataPtr());
			copyFromBufferView(inputBv, innerOffs, tmpTargetPtr.length, tmpTargetPtr);
			innerOffs += tmpTargetPtr.length;
		} while (curOffs + blockLen > innerOffs);

		//
		if (curOffs + blockLen != innerOffs) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": sanity check failed");
		}

		return curOffs + blockLen;
	}

	/**
	 * Parses the DHT (Define Huffman Table: 0xFFC4) block.
	 * @param inputBv JPEG data
	 * @param jpegInfo Parsed JPEG information
	 * @param blockOffset Start offset of the block marker
	 * @return Offset after the block in the JPEG data
	 */
	private int parseBlockDHT(@NonNull BufferView inputBv, @NonNull VideoJpegInfo jpegInfo, final int blockOffset)
			throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseBlockDHT()";

		//logDebug(FNC_NAME, blockOffset, "DHT");
		int blockLen = parseBlockLength(inputBv, blockOffset);
		int curOffs = blockOffset + 2 + 2;

		int innerOffs = curOffs;

		do {
			// huffman table class and number
			byte tmpClNo = inputBv.getByte(innerOffs++);
			byte tmpCl = (byte) ((tmpClNo >> 4) & 0x0F);
			if (tmpCl != 0 && tmpCl != 1) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Unsupported DHT Table Class");
			}
			byte tmpNo = (byte) (tmpClNo & 0x0F);  // Table Number
			//logDebug(FNC_NAME, blockOffset, "DHT - Table: cl=" + tmpCl + ", no=" + tmpNo);

			if (jpegInfo.segmDHT.tablesMap.containsKey(tmpClNo)) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Duplicate DHT table");
			}

			VideoJpegInfo.DhtTable dhtTable = new VideoJpegInfo.DhtTable(tmpCl, tmpNo);

			// codelens
			byte[] tmpTargetPtrC = dhtTable.getTableDataCodelensPtr();
			copyFromBufferView(inputBv, innerOffs, tmpTargetPtrC.length, tmpTargetPtrC);
			innerOffs += tmpTargetPtrC.length;

			// symbols
			int symbolsLen = 0;
			for (byte tmpX : tmpTargetPtrC) {
				symbolsLen += Byte.toUnsignedInt(tmpX);
			}
			byte[] tmpTargetS = new byte[symbolsLen];
			copyFromBufferView(inputBv, innerOffs, tmpTargetS.length, tmpTargetS);
			innerOffs += tmpTargetS.length;
			dhtTable.setTableDataSymbols(tmpTargetS);

			jpegInfo.segmDHT.tablesMap.put(tmpClNo, dhtTable);
		} while (curOffs + blockLen > innerOffs);

		//
		if (curOffs + blockLen != innerOffs) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": sanity check failed");
		}

		return curOffs + blockLen;
	}

	private static void copyFromBufferView(@NonNull BufferView inputBv, int startOffs, int len, byte @NonNull [] targetBa) {
		BufferView bvForCopy = inputBv.clone();
		bvForCopy.setOffset(startOffs);
		bvForCopy.setLength(len);
		BufferExt beForCopy = new BufferExt();
		bvForCopy.copyViewIntoBe(beForCopy);
		beForCopy.copyInto(0, targetBa, 0, len);
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

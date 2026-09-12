package org.tsitle.lib_xrtxp.avdata.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class VideoJpegInfo implements CodecInfoInterface<VideoJpegInfo>, Cloneable {

	public enum ChannelEncoding {
		UNKNOWN,
		YCBCR411,
		YCBCR420,
		YCBCR422,
		YCBCR440,
		/**
		 * full color information retained, 1:1:1 sampling ratio
		 */
		YCBCR444
	}

	public enum QuantizationTablePrecision {
		INT8,
		INT16
	}

	public abstract static class DqtTableBase implements Cloneable {
		private final boolean isPrecision8bit;
		private final byte tableId;
		private byte @NonNull [] tableData;

		protected DqtTableBase(boolean isPrecision8bit, byte tableId, byte @NonNull [] tableData) {
			this.isPrecision8bit = isPrecision8bit;
			this.tableId = tableId;
			this.tableData = tableData;
		}

		@SuppressWarnings("unused")
		public boolean isPrecision8bit() {
			return isPrecision8bit;
		}

		public byte getTableId() {
			return tableId;
		}

		public byte @NonNull [] getTableDataPtr() {
			return tableData;
		}

		@Override
		public @NonNull DqtTableBase clone() {
			try {
				DqtTableBase cloned = (DqtTableBase)super.clone();
				cloned.tableData = new byte[this.tableData.length];
				System.arraycopy(this.tableData, 0, cloned.tableData, 0, this.tableData.length);
				return cloned;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}

		public @NonNull String hashSum() {
			return hashSum(false);
		}

		public @NonNull String hashSum(boolean onlyTableData) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			if (! onlyTableData) {
				baos.write(isPrecision8bit ? 1 : 0);
				baos.write(tableId);
			}
			try {
				baos.write(tableData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
		}
	}

	public static class DqtTable8Bit extends DqtTableBase {
		public DqtTable8Bit(byte tableId) {
			super(true, tableId, new byte[64]);
		}
	}

	public static class DqtTable16Bit extends DqtTableBase {
		public DqtTable16Bit(byte tableId) {
			super(false, tableId, new byte[64 * 2]);
		}
	}

	/** Segment 'Start of Frame - Baseline DCT' */
	public static class SegmentSOF0 {
		public @NonNull ChannelEncoding channelEncoding;
		public int imgWidth;
		public int imgHeight;
		public boolean hasBaselineDCT;
		public byte precision;
		public byte quantTableSelY;
		public byte quantTableSelCb;
		public byte quantTableSelCr;

		public SegmentSOF0() {
			reset();
		}

		public void reset() {
			channelEncoding = ChannelEncoding.UNKNOWN;
			imgWidth = 0;
			imgHeight = 0;
			hasBaselineDCT = false;
			precision = 0;
			quantTableSelY = -1;
			quantTableSelCb = -1;
			quantTableSelCr = -1;
		}

		public void copyOf(@NonNull SegmentSOF0 other) {
			reset();

			channelEncoding = other.channelEncoding;
			imgWidth = other.imgWidth;
			imgHeight = other.imgHeight;
			hasBaselineDCT = other.hasBaselineDCT;
			precision = other.precision;
			quantTableSelY = other.quantTableSelY;
			quantTableSelCb = other.quantTableSelCb;
			quantTableSelCr = other.quantTableSelCr;
		}

		@Override
		public @NonNull String toString() {
			return toString(false);
		}

		public @NonNull String toString(boolean shortOutput) {
			String longFields = "";
			if (! shortOutput) {
				String tmpSbQts = "[" +
						"Y=" + quantTableSelY +
						", Cb=" + quantTableSelCb +
						", Cr=" + quantTableSelCr +
						"]";

				longFields =
						", hasBaselineDCT=" + (hasBaselineDCT ? "T" : "F") +
						", precision=" + precision +
						", quantTableSel=" + tmpSbQts;
			}
			return "[" +
					"channelEncoding=" + channelEncoding +
					", imgW=" + Integer.toUnsignedString(imgWidth) +
					", imgH=" + imgHeight +
					longFields +
					"]";
		}

		public @NonNull String hashSum() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(channelEncoding.ordinal());
			baos.write(imgWidth);
			baos.write(imgHeight);
			baos.write(hasBaselineDCT ? 1 : 0);
			baos.write(precision);
			baos.write(quantTableSelY);
			baos.write(quantTableSelCb);
			baos.write(quantTableSelCr);

			return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
		}
	}

	/** Segment 'Start of Frame - Progressive DCT' */
	public static class SegmentSOF2 {
		public boolean isProgressive;

		public SegmentSOF2() {
			reset();
		}

		public void reset() {
			isProgressive = false;
		}

		public void copyOf(@NonNull SegmentSOF2 other) {
			reset();

			isProgressive = other.isProgressive;
		}

		@Override
		public @NonNull String toString() {
			return "[" +
					"isProgressive=" + (isProgressive ? "T" : "F") +
					"]";
		}

		public @NonNull String hashSum() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(isProgressive ? 1 : 0);

			return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
		}
	}

	/** Segment 'Start of Scan' */
	public static class SegmentSOS {
		public int scanDataOffs;
		public int scanDataLength;

		/** number of components (1=monochrome, 3=color) */
		public byte numComp;

		/** Y component ID */
		public byte compId_y;
		/** Y Huffman Table ID */
		public byte huff_y_id;
		/** Y Huffman Table Class */
		public byte huff_y_class;

		/** CB component ID */
		public byte compId_cb;
		/** CB Huffman Table ID */
		public byte huff_cb_id;
		/** CB Huffman Table Class */
		public byte huff_cb_class;

		/** CR component ID */
		public byte compId_cr;
		/** CR Huffman Table ID */
		public byte huff_cr_id;
		/** CR Huffman Table Class */
		public byte huff_cr_class;

		/** start of spectral selection or predictor selection (should be 0x00) */
		public byte startSpectralSel;
		/** end of spectral selection (should be 0x3F) */
		public byte endSpectralSel;
		/** successive approximation bit position or point transform (should be 0x00) */
		public byte successiveApproxBitPos;

		public SegmentSOS() {
			reset();
		}

		public void reset() {
			scanDataOffs = 0;
			scanDataLength = 0;

			numComp = 0;
			compId_y = -1;
			huff_y_id = -1;
			huff_y_class = -1;
			compId_cb = -1;
			huff_cb_id = -1;
			huff_cb_class = -1;
			compId_cr = -1;
			huff_cr_id = -1;
			huff_cr_class = -1;

			startSpectralSel = 0;
			endSpectralSel = 0;
			successiveApproxBitPos = 0;
		}

		public void copyOf(@NonNull SegmentSOS other) {
			reset();

			scanDataOffs = other.scanDataOffs;
			scanDataLength = other.scanDataLength;

			numComp = other.numComp;
			compId_y = other.compId_y;
			huff_y_id = other.huff_y_id;
			huff_y_class = other.huff_y_class;
			compId_cb = other.compId_cb;
			huff_cb_id = other.huff_cb_id;
			huff_cb_class = other.huff_cb_class;
			compId_cr = other.compId_cr;
			huff_cr_id = other.huff_cr_id;
			huff_cr_class = other.huff_cr_class;

			startSpectralSel = other.startSpectralSel;
			endSpectralSel = other.endSpectralSel;
			successiveApproxBitPos = other.successiveApproxBitPos;
		}

		@Override
		public @NonNull String toString() {
			StringBuilder sb = new StringBuilder();
			if (numComp >= 1) {
				sb
						.append("c0:{")
							.append("cID=").append(Byte.toUnsignedInt(compId_y))
							.append(", hID=").append(Byte.toUnsignedInt(huff_y_id))
							.append(", hCL=").append(Byte.toUnsignedInt(huff_y_class))
						.append("}");
			}
			if (numComp > 1) {
				sb
						.append(", c1:{")
							.append("cID=").append(Byte.toUnsignedInt(compId_cb))
							.append(", hID=").append(Byte.toUnsignedInt(huff_cb_id))
							.append(", hCL=").append(Byte.toUnsignedInt(huff_cb_class))
						.append("}");
			}
			if (numComp > 2) {
				sb
						.append(", c2:{")
							.append("cID=").append(Byte.toUnsignedInt(compId_cr))
							.append(", hID=").append(Byte.toUnsignedInt(huff_cr_id))
							.append(", hCL=").append(Byte.toUnsignedInt(huff_cr_class))
						.append("}");
			}

			return "[" +
					"scanDataOffs=" + scanDataOffs +
					", scanDataLength=" + scanDataLength +
					", numComp=" + Byte.toUnsignedInt(numComp) +
					", comp=[" + sb + "]" +
					"]";
		}

		public @NonNull String hashSum() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(scanDataOffs);
			baos.write(scanDataLength);

			baos.write(numComp);
			baos.write(compId_y);
			baos.write(huff_y_id);
			baos.write(huff_y_class);
			baos.write(compId_cb);
			baos.write(huff_cb_id);
			baos.write(huff_cb_class);
			baos.write(compId_cr);
			baos.write(huff_cr_id);
			baos.write(huff_cr_class);

			baos.write(startSpectralSel);
			baos.write(endSpectralSel);
			baos.write(successiveApproxBitPos);

			return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
		}
	}

	/** Segment 'Define Quantization Table' */
	public static class SegmentDQT {
		public final Map<@NonNull Byte, @NonNull DqtTable8Bit> tables8BitMap = new HashMap<>();
		public final Map<@NonNull Byte, @NonNull DqtTable16Bit> tables16BitMap = new HashMap<>();
		public final Map<@NonNull Byte, @NonNull QuantizationTablePrecision> tablePrecisionsMap = new HashMap<>();

		public SegmentDQT() {
			reset();
		}

		public void reset() {
			tables8BitMap.clear();
			tables16BitMap.clear();
			tablePrecisionsMap.clear();
		}

		public void copyOf(@NonNull SegmentDQT other) {
			reset();

			for (Map.Entry<Byte, DqtTable8Bit> entry : other.tables8BitMap.entrySet()) {
				tables8BitMap.put(entry.getKey(), (DqtTable8Bit)entry.getValue().clone());
			}
			for (Map.Entry<Byte, DqtTable16Bit> entry : other.tables16BitMap.entrySet()) {
				tables16BitMap.put(entry.getKey(), (DqtTable16Bit)entry.getValue().clone());
			}
			tablePrecisionsMap.putAll(other.tablePrecisionsMap);
		}

		@Override
		public @NonNull String toString() {
			StringBuilder tmpSb8bit = new StringBuilder();
			tmpSb8bit.append("[");
			boolean tmpIsFirst = true;
			for (Map.Entry<Byte, DqtTable8Bit> entry : tables8BitMap.entrySet()) {
				if (! tmpIsFirst) {
					tmpSb8bit.append(", ");
				}
				tmpIsFirst = false;
				tmpSb8bit.append("{");
				tmpSb8bit.append(String.format("tId=%d", entry.getValue().getTableId()));
				tmpSb8bit.append(String.format(", hash=%s", entry.getValue().hashSum()));
				tmpSb8bit.append("}");
			}
			tmpSb8bit.append("]");

			StringBuilder tmpSb16bit = new StringBuilder();
			tmpSb16bit.append("[");
			tmpIsFirst = true;
			for (Map.Entry<Byte, DqtTable16Bit> entry : tables16BitMap.entrySet()) {
				if (! tmpIsFirst) {
					tmpSb16bit.append(", ");
				}
				tmpIsFirst = false;
				tmpSb16bit.append("{");
				tmpSb16bit.append(String.format("tId=%d", entry.getValue().getTableId()));
				tmpSb16bit.append(String.format(", hash=%s", entry.getValue().hashSum()));
				tmpSb16bit.append("}");
			}
			tmpSb16bit.append("]");

			StringBuilder tmpSbPrec = new StringBuilder();
			tmpSbPrec.append("[");
			tmpIsFirst = true;
			for (Map.Entry<Byte, QuantizationTablePrecision> entry : tablePrecisionsMap.entrySet()) {
				if (! tmpIsFirst) {
					tmpSbPrec.append(", ");
				}
				tmpIsFirst = false;
				tmpSbPrec.append("{");
				tmpSbPrec.append(String.format("tId=%d", entry.getKey()));
				tmpSbPrec.append(String.format(", p=%s", entry.getValue().toString()));
				tmpSbPrec.append("}");
			}
			tmpSbPrec.append("]");

			return "[" +
					"tables8Bit=" + tmpSb8bit +
					", tables16Bit=" + tmpSb16bit +
					", tablePrecisions=" + tmpSbPrec +
					"]";
		}

		public @NonNull String hashSum() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(tables8BitMap.size());
			for (Map.Entry<Byte, DqtTable8Bit> entry : tables8BitMap.entrySet()) {
				baos.write(entry.getKey());
				try {
					baos.write(entry.getValue().hashSum().getBytes(StandardCharsets.UTF_8));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			baos.write(tables16BitMap.size());
			for (Map.Entry<Byte, DqtTable16Bit> entry : tables16BitMap.entrySet()) {
				baos.write(entry.getKey());
				try {
					baos.write(entry.getValue().hashSum().getBytes(StandardCharsets.UTF_8));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			baos.write(tablePrecisionsMap.size());
			for (Map.Entry<Byte, QuantizationTablePrecision> entry : tablePrecisionsMap.entrySet()) {
				baos.write(entry.getKey());
				baos.write(entry.getValue().ordinal());
			}

			return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
		}
	}

	public final SegmentSOF0 segmSOF0 = new SegmentSOF0();
	public final SegmentSOF2 segmSOF2 = new SegmentSOF2();
	public final SegmentSOS segmSOS = new SegmentSOS();
	public final SegmentDQT segmDQT = new SegmentDQT();
	public int dht_tableCount;
	public int app_blockCount;
	public boolean usesDri;
	public boolean foundEoi;
	public boolean foundCom;

	public VideoJpegInfo() {
		reset();
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public @NonNull String getValidationErrorMsg() {
		return "";
	}

	@Override
	public int getPayloadOffset() {
		return segmSOS.scanDataOffs;
	}

	@Override
	public int getPayloadLength() {
		return segmSOS.scanDataLength;
	}

	@Override
	public void reset() {
		segmSOF0.reset();
		segmSOF2.reset();
		segmSOS.reset();
		segmDQT.reset();
		dht_tableCount = 0;
		app_blockCount = 0;
		usesDri = false;
		foundEoi = false;
		foundCom = false;
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<VideoJpegInfo> src) {
		reset();

		VideoJpegInfo tmpSrc = (VideoJpegInfo)src;
		segmSOF0.copyOf(tmpSrc.segmSOF0);
		segmSOF2.copyOf(tmpSrc.segmSOF2);
		segmSOS.copyOf(tmpSrc.segmSOS);
		segmDQT.copyOf(tmpSrc.segmDQT);
		dht_tableCount = tmpSrc.dht_tableCount;
		app_blockCount = tmpSrc.app_blockCount;
		usesDri = tmpSrc.usesDri;
		foundEoi = tmpSrc.foundEoi;
		foundCom = tmpSrc.foundCom;
	}

	@Override
	@SuppressWarnings("MethodDoesntCallSuperMethod")
	public @NonNull VideoJpegInfo clone() {
		VideoJpegInfo clone = new VideoJpegInfo();
		clone.copyOf(this);
		return clone;
	}

	@Override
	public @NonNull String toString() {
		return toString(false);
	}

	public @NonNull String toString(boolean shortOutput) {
		String longFields = "";
		if (! shortOutput) {

			longFields =
					", SOF2=" + segmSOF2 +
					", SOS=" + segmSOS +
					", DQT=" + segmDQT +
					", dht_tableCount=" + dht_tableCount +
					", app_blockCount=" + app_blockCount +
					", usesDri=" + (usesDri ? "T" : "F") +
					", foundEoi=" + (foundEoi ? "T" : "F") +
					", foundCom=" + (foundCom ? "T" : "F");
		}
		String tmpSof0Stuff;
		if (shortOutput) {
			tmpSof0Stuff =
					"channelEncoding=" + segmSOF0.channelEncoding +
					", imgW=" + Integer.toUnsignedString(segmSOF0.imgWidth) +
					", imgH=" + segmSOF0.imgHeight;
		} else {
			tmpSof0Stuff = "SOF0=" + segmSOF0.toString(false);
		}
		return getClass().getSimpleName() + " [" +
				tmpSof0Stuff +
				longFields +
				"]";
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			baos.write(segmSOF0.hashSum().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			baos.write(segmSOF2.hashSum().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			baos.write(segmSOS.hashSum().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			baos.write(segmDQT.hashSum().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		baos.write(dht_tableCount);
		baos.write(app_blockCount);
		baos.write(usesDri ? 1 : 0);
		baos.write(foundEoi ? 1 : 0);
		baos.write(foundCom ? 1 : 0);

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}

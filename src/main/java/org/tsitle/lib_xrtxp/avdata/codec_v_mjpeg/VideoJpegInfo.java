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

	public @NonNull ChannelEncoding sof0_channelEncoding;
	public int sof0_imgWidth;
	public int sof0_imgHeight;
	public boolean sof0_hasBaselineDCT;
	public byte sof0_precision;
	public byte sof0_quantTableSelY;
	public byte sof0_quantTableSelCb;
	public byte sof0_quantTableSelCr;
	public boolean sof2_isProgressive;
	public int sos_scanDataOffs;
	public int sos_scanDataLength;
	public final Map<@NonNull Byte, @NonNull DqtTable8Bit> dqt_tables8BitMap = new HashMap<>();
	public final Map<@NonNull Byte, @NonNull DqtTable16Bit> dqt_tables16BitMap = new HashMap<>();
	public final Map<@NonNull Byte, @NonNull QuantizationTablePrecision> dqt_tablePrecisionsMap = new HashMap<>();
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
		return sos_scanDataOffs;
	}

	@Override
	public int getPayloadLength() {
		return sos_scanDataLength;
	}

	@Override
	public void reset() {
		sof0_channelEncoding = ChannelEncoding.UNKNOWN;
		sof0_imgWidth = 0;
		sof0_imgHeight = 0;
		sof0_hasBaselineDCT = false;
		sof0_precision = 0;
		sof0_quantTableSelY = -1;
		sof0_quantTableSelCb = -1;
		sof0_quantTableSelCr = -1;
		sof2_isProgressive = false;
		sos_scanDataOffs = -1;
		sos_scanDataLength = 0;
		dqt_tables8BitMap.clear();
		dqt_tables16BitMap.clear();
		dqt_tablePrecisionsMap.clear();
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
		sof0_channelEncoding = tmpSrc.sof0_channelEncoding;
		sof0_imgWidth = tmpSrc.sof0_imgWidth;
		sof0_imgHeight = tmpSrc.sof0_imgHeight;
		sof0_hasBaselineDCT = tmpSrc.sof0_hasBaselineDCT;
		sof0_precision = tmpSrc.sof0_precision;
		sof0_quantTableSelY = tmpSrc.sof0_quantTableSelY;
		sof0_quantTableSelCb = tmpSrc.sof0_quantTableSelCb;
		sof0_quantTableSelCr = tmpSrc.sof0_quantTableSelCr;
		sof2_isProgressive = tmpSrc.sof2_isProgressive;
		sos_scanDataOffs = tmpSrc.sos_scanDataOffs;
		sos_scanDataLength = tmpSrc.sos_scanDataLength;
		for (Map.Entry<Byte, DqtTable8Bit> entry : tmpSrc.dqt_tables8BitMap.entrySet()) {
			dqt_tables8BitMap.put(entry.getKey(), (DqtTable8Bit)entry.getValue().clone());
		}
		for (Map.Entry<Byte, DqtTable16Bit> entry : tmpSrc.dqt_tables16BitMap.entrySet()) {
			dqt_tables16BitMap.put(entry.getKey(), (DqtTable16Bit)entry.getValue().clone());
		}
		dqt_tablePrecisionsMap.putAll(tmpSrc.dqt_tablePrecisionsMap);
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

	@Override
	public @NonNull String toString(boolean shortOutput) {
		String longFields = "";
		if (! shortOutput) {
			String tmpSbQts = "[" +
					"Y=" + sof0_quantTableSelY +
					", Cb=" + sof0_quantTableSelCb +
					", Cr=" + sof0_quantTableSelCr +
					"]";

			StringBuilder tmpSb8bit = new StringBuilder();
			tmpSb8bit.append("[");
			boolean tmpIsFirst = true;
			for (Map.Entry<Byte, DqtTable8Bit> entry : dqt_tables8BitMap.entrySet()) {
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
			for (Map.Entry<Byte, DqtTable16Bit> entry : dqt_tables16BitMap.entrySet()) {
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
			for (Map.Entry<Byte, QuantizationTablePrecision> entry : dqt_tablePrecisionsMap.entrySet()) {
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

			longFields =
					", sof0_hasBaselineDCT=" + (sof0_hasBaselineDCT ? "T" : "F") +
					", sof0_precision=" + sof0_precision +
					", sof0_quantTableSel=" + tmpSbQts +
					", sof2_isProgressive=" + (sof2_isProgressive ? "T" : "F") +
					", sos_scanDataOffs=" + sos_scanDataOffs +
					", sos_scanDataLength=" + sos_scanDataLength +
					", dqt_tables8Bit=" + tmpSb8bit +
					", dqt_tables16Bit=" + tmpSb16bit +
					", dqt_tablePrecisions=" + tmpSbPrec +
					", dht_tableCount=" + dht_tableCount +
					", app_blockCount=" + app_blockCount +
					", usesDri=" + (usesDri ? "T" : "F") +
					", foundEoi=" + (foundEoi ? "T" : "F") +
					", foundCom=" + (foundCom ? "T" : "F");
		}
		return getClass().getSimpleName() + " [" +
				"channelEncoding=" + sof0_channelEncoding +
				", imgW=" + Integer.toUnsignedString(sof0_imgWidth) +
				", imgH=" + sof0_imgHeight +
				longFields +
				"]";
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(sof0_channelEncoding.ordinal());
		baos.write(sof0_imgWidth);
		baos.write(sof0_imgHeight);
		baos.write(sof0_hasBaselineDCT ? 1 : 0);
		baos.write(sof0_precision);
		baos.write(sof0_quantTableSelY);
		baos.write(sof0_quantTableSelCb);
		baos.write(sof0_quantTableSelCr);
		baos.write(sof2_isProgressive ? 1 : 0);
		baos.write(sos_scanDataOffs);
		baos.write(sos_scanDataLength);

		baos.write(dqt_tables8BitMap.size());
		for (Map.Entry<Byte, DqtTable8Bit> entry : dqt_tables8BitMap.entrySet()) {
			baos.write(entry.getKey());
			try {
				baos.write(entry.getValue().hashSum().getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		baos.write(dqt_tables16BitMap.size());
		for (Map.Entry<Byte, DqtTable16Bit> entry : dqt_tables16BitMap.entrySet()) {
			baos.write(entry.getKey());
			try {
				baos.write(entry.getValue().hashSum().getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		baos.write(dqt_tablePrecisionsMap.size());
		for (Map.Entry<Byte, QuantizationTablePrecision> entry : dqt_tablePrecisionsMap.entrySet()) {
			baos.write(entry.getKey());
			baos.write(entry.getValue().ordinal());
		}

		baos.write(dht_tableCount);
		baos.write(app_blockCount);
		baos.write(usesDri ? 1 : 0);
		baos.write(foundEoi ? 1 : 0);
		baos.write(foundCom ? 1 : 0);

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}

package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;

public final class JpegInfo extends AvInfoBase<JpegInfo> {

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
		public final byte tableId;
		public final byte[] tableData;

		protected DqtTableBase(byte tableId, byte[] tableData) {
			this.tableId = tableId;
			this.tableData = tableData;
		}

		@Override
		public DqtTableBase clone() {
			try {
				return (DqtTableBase)super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	public static class DqtTable8Bit extends DqtTableBase {
		public DqtTable8Bit(byte tableId) {
			super(tableId, new byte[64]);
		}
	}

	public static class DqtTable16Bit extends DqtTableBase {
		public DqtTable16Bit(byte tableId) {
			super(tableId, new byte[64 * 2]);
		}
	}

	public ChannelEncoding sof0_channelEncoding;
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
	public int dqt_table8bitCount;
	public int dqt_table16bitCount;
	public DqtTable8Bit[] dqt_tables8Bit;
	public DqtTable16Bit[] dqt_tables16Bit;
	public QuantizationTablePrecision[] dqt_tablePrecisions;
	public int dht_tableCount;
	public int app_blockCount;
	public boolean usesDri;
	public boolean foundEoi;
	public boolean foundCom;

	public JpegInfo() {
		reset();
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
		dqt_table8bitCount = 0;
		dqt_table16bitCount = 0;
		dqt_tables8Bit = new DqtTable8Bit[4];
		dqt_tables16Bit = new DqtTable16Bit[4];
		dqt_tablePrecisions = new QuantizationTablePrecision[4];
		dht_tableCount = 0;
		app_blockCount = 0;
		usesDri = false;
		foundEoi = false;
		foundCom = false;
	}

	@Override
	public void copyOf(@NonNull JpegInfo other) {
		reset();

		sof0_channelEncoding = other.sof0_channelEncoding;
		sof0_imgWidth = other.sof0_imgWidth;
		sof0_imgHeight = other.sof0_imgHeight;
		sof0_hasBaselineDCT = other.sof0_hasBaselineDCT;
		sof0_precision = other.sof0_precision;
		sof0_quantTableSelY = other.sof0_quantTableSelY;
		sof0_quantTableSelCb = other.sof0_quantTableSelCb;
		sof0_quantTableSelCr = other.sof0_quantTableSelCr;
		sof2_isProgressive = other.sof2_isProgressive;
		sos_scanDataOffs = other.sos_scanDataOffs;
		sos_scanDataLength = other.sos_scanDataLength;
		dqt_table8bitCount = other.dqt_table8bitCount;
		dqt_table16bitCount = other.dqt_table16bitCount;
		dqt_tables8Bit = other.dqt_tables8Bit.clone();
		dqt_tables16Bit = other.dqt_tables16Bit.clone();
		dqt_tablePrecisions = other.dqt_tablePrecisions.clone();
		dht_tableCount = other.dht_tableCount;
		app_blockCount = other.app_blockCount;
		usesDri = other.usesDri;
		foundEoi = other.foundEoi;
		foundCom = other.foundCom;
	}

}

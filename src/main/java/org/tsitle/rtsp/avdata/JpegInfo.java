package org.tsitle.rtsp.avdata;

public class JpegInfo {

	public enum ChannelEncoding {
		UNKNOWN,
		YCBCR411,
		YCBCR420,
		YCBCR422,
		YCBCR440,
		/** full color information retained, 1:1:1 sampling ratio */
		YCBCR444
	}
	public enum QuantizationTablePrecision {
		INT8,
		INT16
	}

	public abstract static class DqtTableBase {
		public final byte tableId;
		public final byte[] tableData;
		protected DqtTableBase(byte tableId, byte[] tableData) { this.tableId = tableId; this.tableData = tableData; }
	}
	public static class DqtTable8Bit extends DqtTableBase {
		public DqtTable8Bit(byte tableId) { super(tableId, new byte[64]); }
	}
	public static class DqtTable16Bit extends DqtTableBase {
		public DqtTable16Bit(byte tableId) { super(tableId, new byte[64 * 2]); }
	}

	public ChannelEncoding sof0_channelEncoding = ChannelEncoding.UNKNOWN;
	public int sof0_imgWidth = 0;
	public int sof0_imgHeight = 0;
	public boolean sof0_hasBaselineDCT = false;
	public byte sof0_precision = 0;
	public byte sof0_quantTableSelY = -1;
	public byte sof0_quantTableSelCb = -1;
	public byte sof0_quantTableSelCr = -1;
	public boolean sof2_isProgressive = false;
	public int sos_scanDataOffs = -1;
	public int sos_scanDataLength = 0;
	public int dqt_table8bitCount = 0;
	public int dqt_table16bitCount = 0;
	public DqtTable8Bit[] dqt_tables8Bit = new DqtTable8Bit[4];
	public DqtTable16Bit[] dqt_tables16Bit = new DqtTable16Bit[4];
	public QuantizationTablePrecision[] dqt_tablePrecisions = new QuantizationTablePrecision[4];
	public int dht_tableCount = 0;
	public int app_blockCount = 0;
	public boolean usesDri = false;
	public boolean foundEoi = false;
	public boolean foundCom = false;

}

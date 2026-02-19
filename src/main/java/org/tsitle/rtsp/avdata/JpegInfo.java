package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class JpegInfo implements CodecInfoInterface<JpegInfo>, Cloneable {

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

		public String hashSum() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(tableId);
			try {
				baos.write(tableData);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			return HashMd5Helper.hashOfBytes(baos.toByteArray());
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
	public void copyOf(@NonNull CodecInfoInterface<JpegInfo> src) {
		reset();

		JpegInfo tmpSrc = (JpegInfo)src;
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
		dqt_table8bitCount = tmpSrc.dqt_table8bitCount;
		dqt_table16bitCount = tmpSrc.dqt_table16bitCount;
		for (int i = 0; i < tmpSrc.dqt_table8bitCount; ++i) {
			dqt_tables8Bit[i] = (DqtTable8Bit)tmpSrc.dqt_tables8Bit[i].clone();
		}
		for (int i = 0; i < tmpSrc.dqt_table16bitCount; ++i) {
			dqt_tables16Bit[i] = (DqtTable16Bit)tmpSrc.dqt_tables16Bit[i].clone();
		}
		if (tmpSrc.dht_tableCount > 0) {
			System.arraycopy(tmpSrc.dqt_tablePrecisions, 0, dqt_tablePrecisions, 0, tmpSrc.dht_tableCount);
		}
		dht_tableCount = tmpSrc.dht_tableCount;
		app_blockCount = tmpSrc.app_blockCount;
		usesDri = tmpSrc.usesDri;
		foundEoi = tmpSrc.foundEoi;
		foundCom = tmpSrc.foundCom;
	}

	@Override
	public JpegInfo clone() {
		try {
			JpegInfo clone = (JpegInfo)super.clone();
			for (int i = 0; i < dqt_table8bitCount; ++i) {
				clone.dqt_tables8Bit[i] = (DqtTable8Bit)dqt_tables8Bit[i].clone();
			}
			for (int i = 0; i < dqt_table16bitCount; ++i) {
				clone.dqt_tables16Bit[i] = (DqtTable16Bit)dqt_tables16Bit[i].clone();
			}
			if (dht_tableCount > 0) {
				System.arraycopy(dqt_tablePrecisions, 0, clone.dqt_tablePrecisions, 0, dht_tableCount);
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"[" +
				"channelEncoding=" + sof0_channelEncoding +
				", imgW=" + Integer.toUnsignedString(sof0_imgWidth) +
				", imgH=" + sof0_imgHeight +
				"]";
	}

	@Override
	public String toString(boolean shortOutput) {
		return toString();
	}

	@Override
	public String hashSum() {
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
		baos.write(dqt_table8bitCount);
		baos.write(dqt_table16bitCount);
		for (DqtTable8Bit tmpDqtTable : dqt_tables8Bit) {
			if (tmpDqtTable == null) {
				break;
			}
			try {
				baos.write(tmpDqtTable.hashSum().getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		for (DqtTable16Bit tmpDqtTable : dqt_tables16Bit) {
			if (tmpDqtTable == null) {
				break;
			}
			try {
				baos.write(tmpDqtTable.hashSum().getBytes(StandardCharsets.UTF_8));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		for (QuantizationTablePrecision tmpQtTablePrec : dqt_tablePrecisions) {
			if (tmpQtTablePrec == null) {
				break;
			}
			baos.write(tmpQtTablePrec.ordinal());
		}
		baos.write(dht_tableCount);
		baos.write(app_blockCount);
		baos.write(usesDri ? 1 : 0);
		baos.write(foundEoi ? 1 : 0);
		baos.write(foundCom ? 1 : 0);

		return HashMd5Helper.hashOfBytes(baos.toByteArray());
	}

}

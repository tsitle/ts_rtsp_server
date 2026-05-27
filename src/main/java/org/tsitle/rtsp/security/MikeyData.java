package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.security.constants.KeySizes;
import org.tsitle.rtsp.security.constants.MikeyMsgKemacKv;
import org.tsitle.rtsp.security.constants.MikeyMsgKemacPayloadType;
import org.tsitle.rtsp.security.constants.MikeyMsgTimestampType;

public class MikeyData {

	int hdCsbId;
	/**
	 * Number of entries in the {@code hdCsIdMapInfoPolArr}, {@code hdCsIdMapInfoSsrcArr} and {@code hdCsIdMapInfoRocArr} arrays.<br />
	 * Also used to reference Security Policies to those arrays.
	 */
	byte hdCsNr;
	byte[] hdCsIdMapInfoPolArr = new byte[0];
	int[] hdCsIdMapInfoSsrcArr = new int[0];
	int[] hdCsIdMapInfoRocArr = new int[0];

	MikeyMsgTimestampType tsType = MikeyMsgTimestampType.MMTST_UNKNOWN;
	long tsValueNtc;
	int tsValueCnt;

	final BufferExt randRandData = new BufferExt();

	int spEncrKeyLen = SrtxpKmd.DEFAULT_ENCR_KEY_LEN;
	int spAuthKeyLen = SrtxpKmd.DEFAULT_AUTH_KEY_LEN;
	int spSaltLen = KeySizes.SALT_SIZE;
	int spAuthTagLen = SrtxpKmd.DEFAULT_AUTH_TAG_LEN;
	boolean spEnabledEncrRtp = false;
	boolean spEnabledEncrRtcp = false;
	boolean spEnabledAuthRtxp = false;
	@NonNull DynInteger spKdr = DynInteger.createEmpty();

	final BufferExt kemacMasterKey = new BufferExt();
	final BufferExt kemacMasterSalt = new BufferExt();
	MikeyMsgKemacPayloadType kemacPt = MikeyMsgKemacPayloadType.MMKEMPT_UNKNOWN;
	final BufferExt kemacTekTgkSalt = new BufferExt();
	MikeyMsgKemacKv kemacKvType = MikeyMsgKemacKv.MMKEMKV_UNKNOWN;
	@NonNull DynInteger kemacKvDataSpiOrMki = DynInteger.createEmpty();
	final BufferExt kemacKvDataIntvF = new BufferExt();
	final BufferExt kemacKvDataIntvT = new BufferExt();
	boolean kemacHaveKeys = false;
	boolean kemacHaveNonTekOnlyPayload = false;
	final BufferExt kemacMacData = new BufferExt();

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName())
				.append(" [")
				.append(String.format("hdCsbId=0x%08X", hdCsbId))
				.append(", hdCsNr=").append(Byte.toUnsignedInt(hdCsNr))
				.append(", hdCsIdMapInfo={");
		for (int i = 0; i < hdCsIdMapInfoPolArr.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb
					.append(String.format("{Policy_no_%d=%d", i, hdCsIdMapInfoPolArr[i]))
					.append(String.format(", SSRC_%d=0x%08X", i, hdCsIdMapInfoSsrcArr[i]))
					.append(String.format(", ROC_%d=0x%08X}", i, hdCsIdMapInfoRocArr[i]));
		}
		sb
				.append("}")
				.append(", tsType=").append(tsType);
		switch (tsType) {
			case MikeyMsgTimestampType.MMTST_NTP_DEF:
			case MikeyMsgTimestampType.MMTST_NTP_UTC:
				sb.append(", tsValueNtc=").append(Long.toUnsignedString(tsValueNtc));
				break;
			case MikeyMsgTimestampType.MMTST_COUNTER:
				sb.append(", tsValueCnt=").append(Long.toUnsignedString(tsValueCnt));
				break;
			default:
				break;
		}
		sb
				.append(", randRandData=").append(randRandData.toHexString(true))
				.append(", spEncrKeyLen=").append(Integer.toUnsignedString(spEncrKeyLen))
				.append(", spAuthKeyLen=").append(Integer.toUnsignedString(spAuthKeyLen))
				.append(", spSaltLen=").append(Integer.toUnsignedString(spSaltLen))
				.append(", spAuthTagLen=").append(Integer.toUnsignedString(spAuthTagLen))
				.append(", spEnabledEncrRtp=").append(spEnabledEncrRtp ? "T" : "F")
				.append(", spEnabledEncrRtcp=").append(spEnabledEncrRtcp ? "T" : "F")
				.append(", spEnabledAuthRtxp=").append(spEnabledAuthRtxp ? "T" : "F")
				.append(", spKdr=").append(spKdr)
				.append(", kemacMasterKey=").append(kemacMasterKey.toHexString(true))
				.append(", kemacMasterSalt=").append(kemacMasterSalt.toHexString(true))
				.append(", kemacPt=").append(kemacPt);
		if (kemacPt == MikeyMsgKemacPayloadType.MMKEMPT_TEK_SALT || kemacPt == MikeyMsgKemacPayloadType.MMKEMPT_TGK_SALT) {
			sb.append(", kemacTekTgkSalt=").append(kemacTekTgkSalt.toHexString(true));
		}
		sb.append(", kemacKvType=").append(kemacKvType);
		if (kemacKvType == MikeyMsgKemacKv.MMKEMKV_SPI_OR_MKI) {
			sb.append(", kemacKvDataSpiOrMki=").append(kemacKvDataSpiOrMki);
		} else if (kemacKvType == MikeyMsgKemacKv.MMKEMKV_INTV) {
			sb
					.append(", kemacKvDataIntvF=").append(kemacKvDataIntvF.toHexString(true))
					.append(", kemacKvDataIntvT=").append(kemacKvDataIntvT.toHexString(true));
		}
		sb
				.append(", kemacMacData=").append(kemacMacData.toHexString(true))
				.append("]");
		return sb.toString();
	}

}

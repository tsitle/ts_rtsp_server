package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.helpers.NtpTimestampHelper;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.constants.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Base64;

public final class MikeyGenerator {

	private MikeyGenerator() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Generate a MIKEY message according to RFC-3830 Section 6.2 (Key data transport payload).
	 * @param kmd SRTxP Key Management Data
	 * @return Base64 encoded MIKEY message
	 */
	public static @NonNull String generate(@NonNull SrtxpKmd kmd) {
		if (kmd.authKeyLen() != KeySizes.AUTH_KEY_SIZE_080 && kmd.authKeyLen() != KeySizes.AUTH_KEY_SIZE_160) {
			throw new IllegalArgumentException("Unsupported auth key length");
		}

		//
		BufferExt tmpBe = new BufferExt();
		tmpBe.increaseSize(1024);
		ByteBuffer msgBb = ByteBuffer.wrap(tmpBe.getBaPtr()).order(ByteOrder.BIG_ENDIAN);

		// ---- Common Header ----
		writeCommonHeader(msgBb, MikeyMsgPayloadType.MMPT_T, kmd.ssrcId());

		// ---- MIKEY payloads ----
		writePtTimestamp(msgBb, MikeyMsgPayloadType.MMPT_RAND);
		writePtRand(msgBb, MikeyMsgPayloadType.MMPT_SP);
		writePtSp(msgBb, MikeyMsgPayloadType.MMPT_KEMAC, kmd);
		writePtKemac(msgBb, MikeyMsgPayloadType.MMPT_LAST, kmd);

		//
		msgBb.flip();
		tmpBe.setUsed(msgBb.limit());
		byte[] tmpBa = new byte[tmpBe.getUsed()];
		tmpBe.copyInto(0, tmpBa, 0, tmpBe.getUsed());
		return Base64.getEncoder().encodeToString(tmpBa);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
	private static void writeCommonHeader(ByteBuffer msgBb, MikeyMsgPayloadType nextPt, int rtspSsrcId) {
		// version
		msgBb.put(MikeyOtherConstants.MOC_CHD_VERSION);
		// data type
		msgBb.put(MikeyMsgDataType.MMDT_PRE_SHARED_KEY.getValue());
		// next payload
		msgBb.put(nextPt.getValue());
		// V | PRF_FUNC
		msgBb.put((byte)(
				MikeyOtherConstants.MOC_CHD_V_BIT_NORESP
				| MikeyOtherConstants.MOC_CHD_PRF_FUNC_MIKEY1
			));
		// CSB_ID
		msgBb.putInt(RandomHelper.getRandomUint32());
		// #CS (indicates the number of Crypto Sessions that will be handled within the CBS)
		msgBb.put((byte)0x01);
		// CS_ID_map_type
		msgBb.put(MikeyOtherConstants.MOC_CHD_CS_ID_MAP_TYPE_SRTP_ID);
		// CS_ID_map_info aka 'SRTP ID'
		/*
		 * RFC-3830 Errata 2654:
		 *   CS ID map info (variable length): identifies the crypto session(s) for
		 *   which the SA should be created.  The currently defined map type is
		 *   the SRTP-ID (defined in Section 6.1.1).
		 */
		msgBb.put((byte)0x00);  // Policy_no_x
		msgBb.putInt(rtspSsrcId);  // SSRC_x
		msgBb.putInt(0);  // ROC_x
	}

	@SuppressWarnings("SameParameterValue")
	private static void writePtTimestamp(ByteBuffer msgBb, MikeyMsgPayloadType nextPt) {
		// next payload
		msgBb.put(nextPt.getValue());
		// timestamp type
		msgBb.put(MikeyMsgTimestampType.MMTST_NTP_UTC.getValue());
		// timestamp value 64-bits
		msgBb.putLong(NtpTimestampHelper.instantToNtpTimestamp(Instant.now()));
	}

	@SuppressWarnings("SameParameterValue")
	private static void writePtRand(ByteBuffer msgBb, MikeyMsgPayloadType nextPt) {
		// next payload
		msgBb.put(nextPt.getValue());
		// RAND length
		msgBb.put((byte)16);
		// RAND value
		BufferExt tmpRandData = new BufferExt();
		RandomHelper.getSecureRandomBytes(16, tmpRandData);
		msgBb.put(tmpRandData.getBaPtr(), 0, tmpRandData.getUsed());
	}

	@SuppressWarnings("SameParameterValue")
	private static void writePtSp(ByteBuffer msgBb, MikeyMsgPayloadType nextPt, @NonNull SrtxpKmd kmd) {
		// next payload
		msgBb.put(nextPt.getValue());
		// Policy No
		final byte policyNo = 0x00;
		msgBb.put(policyNo);
		// Prot Type
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_PROT_SRTP);
		//
		BufferExt tmpPolicyData = new BufferExt();
		buildSpParams(kmd, tmpPolicyData);
		// Policy param length
		msgBb.putShort((short)tmpPolicyData.getUsed());
		// Policy parameters
		msgBb.put(tmpPolicyData.getBaPtr(), 0, tmpPolicyData.getUsed());
	}

	@SuppressWarnings("SameParameterValue")
	private static void buildSpParams(@NonNull SrtxpKmd kmd, BufferExt outputPolicyData) {
		outputPolicyData.increaseSize(1024);
		ByteBuffer msgBb = ByteBuffer.wrap(outputPolicyData.getBaPtr()).order(ByteOrder.BIG_ENDIAN);

		// -- ENCALG --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_ENCALG.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_ENC_ALG_AESCM);

		// -- SEKL --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SEKL.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put((byte)KeySizes.AES_128_KEY_SIZE);

		// -- AUTHALG --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_AUTHALG.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_AUTH_ALG_HMACSHA1);

		// -- SAKL --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SAKL.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put((byte)kmd.authKeyLen());

		// -- SSKL --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SSKL.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put((byte)KeySizes.SALT_SIZE);

		// -- SPRF --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SPRF.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_PRF_ALG_AESCM);

		// -- SRTPENCEN --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SRTPENCEN.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_ENABLED);

		// -- SRTCPENCEN --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SRTCPENCEN.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_ENABLED);

		// -- SRTPAUTHEN --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SRTPAUTHEN.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_ENABLED);

		// -- AUTHTAGLENGTH --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_AUTHTAGLENGTH.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put((byte)KeySizes.AUTH_TAG_SIZE);

		//
		outputPolicyData.setUsed(msgBb.position());
	}

	@SuppressWarnings("SameParameterValue")
	private static void writePtKemac(ByteBuffer msgBb, MikeyMsgPayloadType nextPt, @NonNull SrtxpKmd kmd) {
		// next payload
		msgBb.put(nextPt.getValue());
		// encryption algorithm (for the KEMAC data)
		msgBb.put(MikeyMsgKemacEncrAlg.MMEA_NULL.getValue());
		//
		BufferExt tmpKeyData = new BufferExt();
		buildKemacData(kmd, nextPt, tmpKeyData);
		// KEMAC data length
		msgBb.putShort((short)tmpKeyData.getUsed());
		// KEMAC data
		msgBb.put(tmpKeyData.getBaPtr(), 0, tmpKeyData.getUsed());
		// MAC algorithm (for the KEMAC data)
		msgBb.put(MikeyOtherConstants.MOC_KEMAC_MAC_ALG_NONE);
	}

	@SuppressWarnings("SameParameterValue")
	private static void buildKemacData(@NonNull SrtxpKmd kmd, MikeyMsgPayloadType nextPt, BufferExt outputKeyData) {
		outputKeyData.increaseSize(1024);
		ByteBuffer msgBb = ByteBuffer.wrap(outputKeyData.getBaPtr()).order(ByteOrder.BIG_ENDIAN);

		// next payload (repeated here for some reason)
		msgBb.put(nextPt.getValue());

		// type and KV (key validity period)
		MikeyMsgKemacKv tmpKvType = (kmd.mki().isEmpty() ? MikeyMsgKemacKv.MMKEMKV_NULL : MikeyMsgKemacKv.MMKEMKV_SPI_OR_MKI);
		msgBb.put((byte)(
				((MikeyMsgKemacPayloadType.MMKEMPT_TEK_ONLY.getValue() << 4) & 0xF0)
				| (tmpKvType.getValue() & 0x0F)
			));

		// key data length
		short keyDataLen = (short)(KeySizes.AES_128_KEY_SIZE + KeySizes.SALT_SIZE);
		msgBb.putShort(keyDataLen);
		// key data
		msgBb.put(kmd.masterKey().getBaPtr(), 0, kmd.masterKey().getUsed());
		msgBb.put(kmd.masterSalt().getBaPtr(), 0, kmd.masterSalt().getUsed());

		//
		if (tmpKvType == MikeyMsgKemacKv.MMKEMKV_SPI_OR_MKI) {
			// KV data length
			msgBb.put((byte)kmd.mki().getUsed());
			// KV data
			if (! kmd.mki().isEmpty()) {
				msgBb.put(kmd.mki().getBaPtr(), 0, kmd.mki().getUsed());
			}
		}

		//
		outputKeyData.setUsed(msgBb.position());
	}

}

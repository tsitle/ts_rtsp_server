package org.tsitle.lib_xrtxp.kmd;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.constants.*;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.common.types.NtpTimestamp;
import org.tsitle.lib_xrtxp.common.helpers.RandomHelper;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Generator for MIKEY messages.<br />
 * MIKEY: Multimedia Internet KEYing, see <a href="https://datatracker.ietf.org/doc/html/rfc3830">RFC-3830</a>
 */
public final class MikeyGenerator {

	private MikeyGenerator() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Generate a MIKEY message.
	 * @param kmd SRTxP Key Management Data
	 * @return Base64-encoded MIKEY message
	 */
	public static @NonNull String generate(@NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyGenerator.class.getSimpleName() + ".generate()";

		if (kmd.authKeyLen() != KeySizes.AUTH_KEY_SIZE_080 && kmd.authKeyLen() != KeySizes.AUTH_KEY_SIZE_160) {
			throw new SrtxpSecurityException(FNC_NAME + ": Unsupported Auth Key length: " + kmd.authKeyLen() + " bytes");
		}

		//
		BufferExt tmpBe = new BufferExt();
		tmpBe.increaseSize(1024);
		ByteBuffer msgBb = ByteBuffer.wrap(tmpBe.getBaPtr()).order(ByteOrder.BIG_ENDIAN);

		// ---- Common Header ----
		writeCommonHeader(msgBb, MikeyMsgPayloadType.MMPT_T, kmd.ssrcId().getId32bit().orElse(0L).intValue());

		// ---- MIKEY payloads ----
		writePtTimestamp(msgBb, MikeyMsgPayloadType.MMPT_RAND);
		writePtRand(msgBb, MikeyMsgPayloadType.MMPT_SP);
		writePtSp(msgBb, MikeyMsgPayloadType.MMPT_KEMAC, kmd);
		writePtKemac(msgBb, MikeyMsgPayloadType.MMPT_LAST, kmd);

		//
		msgBb.flip();
		tmpBe.setUsed(msgBb.limit());
		return tmpBe.toBase64String();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Write Common Header payload (RFC-3830 Section 6.1)
	 */
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
		msgBb.putInt(RandomHelper.getRandomUint32(false));
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

	/**
	 * Write Timestamp payload (RFC-3830 Section 6.6)
	 */
	@SuppressWarnings("SameParameterValue")
	private static void writePtTimestamp(ByteBuffer msgBb, MikeyMsgPayloadType nextPt) {
		// next payload
		msgBb.put(nextPt.getValue());
		// timestamp type
		msgBb.put(MikeyMsgTimestampType.MMTST_NTP_UTC.getValue());
		// timestamp value 64-bits
		NtpTimestamp tmpTsVal = NtpTimestamp.ofNow();
		msgBb.putLong(tmpTsVal.getTsAsUnsigned64bit().orElseThrow());
	}

	/**
	 * Write RAND payload (RFC-3830 Section 6.11)
	 */
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

	/**
	 * Write Security Policy payload (RFC-3830 Section 6.10)
	 */
	@SuppressWarnings("SameParameterValue")
	private static void writePtSp(ByteBuffer msgBb, MikeyMsgPayloadType nextPt, @NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		// next payload
		msgBb.put(nextPt.getValue());
		// Policy No
		final byte policyNo = 0x00;
		msgBb.put(policyNo);
		// Prot Type
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_PROT_SRTP);
		//
		BufferExt tmpPolicyData = new BufferExt();
		buildSubSpParams(kmd, tmpPolicyData);
		// Policy param length
		msgBb.putShort((short)tmpPolicyData.getUsed());
		// Policy parameters
		msgBb.put(tmpPolicyData.getBaPtr(), 0, tmpPolicyData.getUsed());
	}

	@SuppressWarnings("SameParameterValue")
	private static void buildSubSpParams(@NonNull SrtxpKmd kmd, BufferExt outputPolicyData) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyGenerator.class.getSimpleName() + ".buildSubSpParams()";

		outputPolicyData.increaseSize(1024);
		ByteBuffer msgBb = ByteBuffer.wrap(outputPolicyData.getBaPtr()).order(ByteOrder.BIG_ENDIAN);

		// -- ENCALG --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_ENCALG.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_ENC_ALG_AESCM);

		// -- SEKL --
		if (kmd.encrKeyLen() < 0 || kmd.encrKeyLen() > 255) { throw new SrtxpSecurityException(FNC_NAME + ": Invalid Encr Key length"); }
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SEKL.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put((byte)kmd.encrKeyLen());

		// -- AUTHALG --
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_AUTHALG.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put(MikeyOtherConstants.MOC_PT_SP_AUTH_ALG_HMACSHA1);

		// -- SAKL --
		if (kmd.authKeyLen() < 0 || kmd.authKeyLen() > 255) { throw new SrtxpSecurityException(FNC_NAME + ": Invalid Auth Key length"); }
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_SAKL.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put((byte)kmd.authKeyLen());

		// -- SSKL --
		if (KeySizes.SALT_SIZE < 0 || KeySizes.SALT_SIZE > 255) { throw new SrtxpSecurityException(FNC_NAME + ": Invalid Salt length"); }
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
		if (kmd.authTagLen() < 0 || kmd.authTagLen() > 255) { throw new SrtxpSecurityException(FNC_NAME + ": Invalid Auth Tag length"); }
		msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_AUTHTAGLENGTH.getValue());  // Parameter Type
		msgBb.put((byte)0x01);  // Parameter Length
		msgBb.put((byte)kmd.authTagLen());

		// -- KDR --
		if (! kmd.kdr().isEmpty()) {
			msgBb.put(MikeyMsgSecPolicyParamType.MMSPPT_KDR.getValue());  // Parameter Type
			if (kmd.kdr().getSizeBytes() <= 4) {
				msgBb.put((byte)0x04);  // Parameter Length
				msgBb.putInt(kmd.kdr().getValue().orElseThrow().intValue());
			} else {
				msgBb.put((byte)0x08);  // Parameter Length
				msgBb.putLong(kmd.kdr().getValue().orElseThrow());
			}
		}

		//
		outputPolicyData.setUsed(msgBb.position());
	}

	/**
	 * Write Key data transport payload aka KEMAC (RFC-3830 Section 6.2)
	 */
	@SuppressWarnings("SameParameterValue")
	private static void writePtKemac(ByteBuffer msgBb, MikeyMsgPayloadType nextPt, @NonNull SrtxpKmd kmd) throws SrtxpSecurityException {
		// next payload
		msgBb.put(nextPt.getValue());
		// encryption algorithm (for the KEMAC data)
		msgBb.put(MikeyMsgKemacEncrAlg.MMEA_NULL.getValue());
		//
		BufferExt tmpKeyData = new BufferExt();
		buildSubPtKemacData(kmd, MikeyMsgPayloadType.MMPT_LAST, tmpKeyData);
		// KEMAC data length
		msgBb.putShort((short)tmpKeyData.getUsed());
		// KEMAC data
		msgBb.put(tmpKeyData.getBaPtr(), 0, tmpKeyData.getUsed());
		// MAC algorithm (for the KEMAC data)
		msgBb.put(MikeyOtherConstants.MOC_KEMAC_MAC_ALG_NONE);
	}

	/**
	 * Write the KEMAC key data sub-payload (RFC-3830 Section 6.13)
	 */
	@SuppressWarnings("SameParameterValue")
	private static void buildSubPtKemacData(
				@NonNull SrtxpKmd kmd,
				MikeyMsgPayloadType nextPt,
				BufferExt outputKeyData
			) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyGenerator.class.getSimpleName() + ".buildSubPtKemacData()";

		outputKeyData.increaseSize(1024);
		ByteBuffer msgBb = ByteBuffer.wrap(outputKeyData.getBaPtr()).order(ByteOrder.BIG_ENDIAN);

		// next payload (in case we have more than one sub-payload)
		msgBb.put(nextPt.getValue());

		// type and KV (key validity period)
		MikeyMsgKemacKv tmpKvType = (kmd.mki().isEmpty() ? MikeyMsgKemacKv.MMKEMKV_NULL : MikeyMsgKemacKv.MMKEMKV_SPI_OR_MKI);
		msgBb.put((byte)(
				((MikeyMsgKemacPayloadType.MMKEMPT_TEK_ONLY.getValue() << 4) & 0xF0)
				| (tmpKvType.getValue() & 0x0F)
			));

		// key data length
		if (kmd.encrKeyLen() != kmd.masterKey().getUsed()) {
			throw new SrtxpSecurityException(FNC_NAME + ": Encr Key length doesn't match Master Key length");
		}
		if (kmd.masterSalt().getUsed() != KeySizes.SALT_SIZE) {
			throw new SrtxpSecurityException(FNC_NAME + ": Salt length doesn't match Master Salt length");
		}
		short keyDataLen = (short)(kmd.encrKeyLen() + KeySizes.SALT_SIZE);
		msgBb.putShort(keyDataLen);
		// key data
		msgBb.put(kmd.masterKey().getBaPtr(), 0, kmd.masterKey().getUsed());
		msgBb.put(kmd.masterSalt().getBaPtr(), 0, kmd.masterSalt().getUsed());

		//
		if (tmpKvType == MikeyMsgKemacKv.MMKEMKV_SPI_OR_MKI) {
			// KV data length
			int tmpMkiSz = kmd.mki().getSizeBytes();
			if (tmpMkiSz != 0 && tmpMkiSz != 1 && tmpMkiSz != 2 && tmpMkiSz != 4 && tmpMkiSz != 8) {
				throw new SrtxpSecurityException(FNC_NAME + ": Invalid KV SPI/MKI length - must be 0/1/2/4/8");
			}
			msgBb.put((byte)tmpMkiSz);
			// KV data
			kmd.mki().writeToByteBuffer(msgBb);
		}

		//
		outputKeyData.setUsed(msgBb.position());
	}

}

package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.constants.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

public final class MikeyParser {

	private MikeyParser() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse a MIKEY message according to RFC-3830 Section 6.2 (Key data transport payload).
	 * @param msgB64 Base64 encoded MIKEY message
	 * @return SRTxP Key Management Data
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public static @NonNull SrtxpKmd parseKeyMgmtData(@NonNull String msgB64) throws SrtpSecurityException {
		byte[] msgBa;
		try {
			msgBa = Base64.getDecoder().decode(msgB64);
		} catch (IllegalArgumentException e) {
			throw new SrtpSecurityException("Invalid base64 encoding");
		}

		/*
		 * Common Header: 01000500 FD3B3879 0100 0008CFD4AA00000000 (will change with every request)
		 *
		 * Payloads (will change with every request):
		 *                    4                           24                                                          34                                                         30                                    1
		 * Payloads Ex. 1: 0B00ED69 3E26791937080A103AA888B96FE168054BEA5B32327C465A 010000001500010101011002010103010A0701010801010A0101000000220020001E B8B0EE27F00909578C1BFFC342BA0CA05115A3C4FBDB29603FE11D752036 00
		 * Payloads Ex. 2: 0B00ED69 3EB07453E2D60A101A1CCA564649E27AAE74395BB5206796 010000001500010101011002010103010A0701010801010A0101000000220020001E 8E4F1A96439D4C84597D6295FB0BA1A395D335E6230B3297C5404323585B 00
		 * Payloads Ex. 3: 0B00ED69 3EB1345985AD0A10181C2AE0DEFE1B1C907F08B94A5BEE50 010000001500010101011002010103010A0701010801010A0101000000220020001E D2AD978F862662A28534B5DDBAF8275B82196042C00E81B5AD2A8B76AC14 00
		 * Payloads Ex. 4: 0B00ED69 402CB4A7E73A0A102EE28DEA01C5FDD14DD2EEC9DE64D921 010000001500010101011002010103010A0701010801010A0101000000220020001E F749256D46DE3781410BD1D723B0784813569423CBA972A7BAB1099AF44D 00
		 */

		//
		final MikeyData mikeyData = new MikeyData();

		//
		ByteBuffer msgBb = ByteBuffer.wrap(msgBa).order(ByteOrder.BIG_ENDIAN);

		// parse MIKEY header -- RFC-3830 Section 6.1 (Common Header payload aka HDR)
		MickeyMsgPayloadType nextPayloadType = parseCommonHeader(msgBb, mikeyData);

		// parse MIKEY payloads
		try {
			while (msgBb.remaining() >= 4) {
				byte tmpBy = msgBb.get();
				MickeyMsgPayloadType tmpNextPt = MickeyMsgPayloadType.of(tmpBy);
				if (tmpNextPt == MickeyMsgPayloadType.MMPT_UNKNOWN) {
					throw new SrtpSecurityException(String.format("Unknown MIKEY payload type: 0x%02X", tmpBy));
				}

				//
				boolean stopLoop = false;
				switch (nextPayloadType) {
					case MMPT_T: parsePtTimestamp(msgBb); break;
					case MMPT_RAND: parsePtRand(msgBb, mikeyData); break;
					case MMPT_SP: parsePtSecurityPolicy(msgBb, mikeyData); break;
					case MMPT_KEMAC: tmpNextPt = parsePtKemac(msgBb, mikeyData); break;
					default:
						stopLoop = true;
				}
				if (stopLoop) {
					break;
				}

				//
				nextPayloadType = tmpNextPt;
				if (nextPayloadType == MickeyMsgPayloadType.MMPT_LAST) {
					break;
				}
			}
		} catch (BufferUnderflowException e) {
			throw new SrtpSecurityException("Invalid MIKEY payload length");
		}

		if (! mikeyData.kemacHaveKeys) {
			throw new SrtpSecurityException("No KEMAC payload found");
		}
		if (mikeyData.kemacKvType != MickeyMsgKemacKv.MMKEMKV_SPI_OR_MKI && mikeyData.kemacKvType != MickeyMsgKemacKv.MMKEMKV_NULL) {
			throw new SrtpSecurityException("Unsupported KEMAC KV type");
		}
		if (mikeyData.kemacHaveNonTekOnlyPayload) {
			throw new SrtpSecurityException("Unsupported KEMAC payload type (only TEK w/o Salt is supported)");
		}
		return new SrtxpKmd(
				mikeyData.kemacMasterKey,
				mikeyData.kemacMasterSalt,
				mikeyData.spAuthKeyLen,
				mikeyData.kemacKvDataSpiOrMki,
				mikeyData.hdCsIdMapInfoSsrcArr[0]
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static MickeyMsgPayloadType parseCommonHeader(ByteBuffer msgBb, final MikeyData mikeyData) throws SrtpSecurityException {
		// version
		byte tmpHdVers = msgBb.get();
		if (tmpHdVers != MickeyOtherConstants.MOC_CHD_VERSION) {
			throw new SrtpSecurityException("Unsupported MIKEY version");
		}
		// data type
		byte tmpHdDt = msgBb.get();
		MickeyMsgDataType tmpDataType = MickeyMsgDataType.of(tmpHdDt);
		if (tmpDataType != MickeyMsgDataType.MMDT_PRE_SHARED_KEY) {
			throw new SrtpSecurityException(String.format("Unsupported MIKEY data type: 0x%02X", tmpHdDt));
		}
		// next payload
		byte tmpHdNp = msgBb.get();
		MickeyMsgPayloadType nextPayloadType = MickeyMsgPayloadType.of(tmpHdNp);
		if (nextPayloadType == MickeyMsgPayloadType.MMPT_UNKNOWN) {
			throw new SrtpSecurityException(String.format("Unknown MIKEY payload type: 0x%02X", tmpHdNp));
		}
		// V | PRF_FUNC
		byte tmpHdVandPrf = msgBb.get();
		byte tmpHdV = (byte)(tmpHdVandPrf & MickeyOtherConstants.MOC_CHD_V_BIT_RESP);
		if (tmpHdV == MickeyOtherConstants.MOC_CHD_V_BIT_RESP) {
			throw new SrtpSecurityException(String.format("Unsupported MIKEY V value: 0x%02X", tmpHdV));
		}
		byte tmpHdPrf = (byte)(tmpHdVandPrf & (byte)0x7F);
		if (tmpHdPrf != MickeyOtherConstants.MOC_CHD_PRF_FUNC_MICKEY1) {
			throw new SrtpSecurityException(String.format("Unsupported MIKEY PRF value: 0x%02X", tmpHdPrf));
		}
		// CSB_ID
		mikeyData.hdCsbId = msgBb.getInt();
		// #CS (indicates the number of Crypto Sessions that will be handled within the CBS)
		mikeyData.hdCsNr = msgBb.get();
		// CS_ID_map_type
		byte tmpCsIdMapType = msgBb.get();
		if (tmpCsIdMapType != MickeyOtherConstants.MOC_CHD_CS_ID_MAP_TYPE_SRTP_ID) {
			throw new SrtpSecurityException(String.format("Unsupported MIKEY CS_ID_map_type value: 0x%02X", tmpCsIdMapType));
		}

		// parse CS_ID_map_info aka 'SRTP ID'
		/*
		 * RFC-3830 Errata 2654:
		 *   CS ID map info (variable length): identifies the crypto session(s) for
		 *   which the SA should be created.  The currently defined map type is
		 *   the SRTP-ID (defined in Section 6.1.1).
		 */
		mikeyData.hdCsIdMapInfoPolArr = new byte[mikeyData.hdCsNr];
		mikeyData.hdCsIdMapInfoSsrcArr = new int[mikeyData.hdCsNr];
		mikeyData.hdCsIdMapInfoRocArr = new int[mikeyData.hdCsNr];
		for (int i = 0; i < mikeyData.hdCsNr; i++) {
			mikeyData.hdCsIdMapInfoPolArr[i] = msgBb.get();  // Policy_no_x
			mikeyData.hdCsIdMapInfoSsrcArr[i] = msgBb.getInt();  // SSRC_x
			mikeyData.hdCsIdMapInfoRocArr[i] = msgBb.getInt();  // ROC_x
		}
		if (mikeyData.hdCsNr != 1) {
			// yes, we could have skipped the above loop
			throw new SrtpSecurityException("Multiple MIKEY Security Policies are not supported");
		}
		if (mikeyData.hdCsIdMapInfoRocArr[0] != 0) {
			throw new SrtpSecurityException("Initial ROC in MIKEY CHD is not 0");
		}

		return nextPayloadType;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void parsePtTimestamp(ByteBuffer msgBb) throws SrtpSecurityException {
		byte tmpBy = msgBb.get();
		MickeyMsgTimestampType tmpTsTp = MickeyMsgTimestampType.of(tmpBy);
		if (tmpTsTp == MickeyMsgTimestampType.MMTST_UNKNOWN) {
			throw new SrtpSecurityException(String.format("Unknown MIKEY Timestamp type: 0x%02X", tmpBy));
		}
		if (tmpTsTp != MickeyMsgTimestampType.MMTST_COUNTER) {
			msgBb.getLong();  // TS value 64-bits
		} else {
			msgBb.getInt();  // TS value 32-bits
		}
	}

	private static void parsePtRand(ByteBuffer msgBb, final MikeyData mikeyData) throws SrtpSecurityException {
		byte tmpBy = msgBb.get();
		extractBytes("RAND", msgBb, tmpBy, mikeyData.hdRandData);
	}

	private static void parsePtSecurityPolicy(ByteBuffer msgBb, final MikeyData mikeyData) throws SrtpSecurityException {
		byte tmpBy = msgBb.get();  // Policy No
		if (tmpBy != 0x00) {
			throw new SrtpSecurityException("Multiple MIKEY Security Policies are not supported");
		}
		tmpBy = msgBb.get();  // Prot Type
		if (tmpBy != 0x00) {  // PROT==0x00 => SRTP
			throw new SrtpSecurityException(String.format("Unsupported MIKEY SP prot type: 0x%02X", tmpBy));
		}
		short tmpSpLen = msgBb.getShort();  // Policy param length
		if (msgBb.remaining() < tmpSpLen) {
			throw new SrtpSecurityException("Invalid MIKEY payload length (needed " +
					tmpSpLen + ", got " + msgBb.remaining() + ")");
		}
		byte[] tmpPolicyData = new byte[tmpSpLen];
		msgBb.get(tmpPolicyData);
		//
		ByteBuffer tmpSpParamsBuf = ByteBuffer.wrap(tmpPolicyData).order(ByteOrder.BIG_ENDIAN);
		while (tmpSpParamsBuf.remaining() >= 2) {
			tmpBy = tmpSpParamsBuf.get();  // Parameter Type
			MickeyMsgSecPolicyParamType mmspType = MickeyMsgSecPolicyParamType.of(tmpBy);
			if (mmspType == MickeyMsgSecPolicyParamType.MMSPPT_UNKNOWN) {
				throw new SrtpSecurityException(String.format("Unknown MIKEY SP Parameter type: 0x%02X", tmpBy));
			}
			tmpBy = tmpSpParamsBuf.get();  // Parameter Length
			if (tmpSpParamsBuf.remaining() < tmpBy) {
				throw new SrtpSecurityException("Invalid MIKEY SP payload length (needed " +
						tmpBy + ", got " + tmpSpParamsBuf.remaining() + ")");
			}
			if (tmpBy == 0) {
				continue;
			}
			byte[] tmpPolParamData = new byte[tmpBy];
			tmpSpParamsBuf.get(tmpPolParamData);

			switch (mmspType) {
				case MMSPPT_ENCALG:
					if (tmpPolParamData[0] != MickeyOtherConstants.MOC_PT_SP_ENC_ALG_AESCM) {
						throw new SrtpSecurityException(
								String.format("Unsupported MIKEY SP Encr Algo type 0x%02X", tmpPolParamData[0])
							);
					}
					break;
				case MMSPPT_SEKL:
					if (tmpPolParamData[0] != KeySizes.AES_128_KEY_SIZE) {
						throw new SrtpSecurityException(
								String.format("Unsupported MIKEY SP Encr Key Length 0x%02X", tmpPolParamData[0])
						);
					}
					break;
				case MMSPPT_AUTHALG:
					if (tmpPolParamData[0] != MickeyOtherConstants.MOC_PT_SP_AUTH_ALG_HMACSHA1) {
						throw new SrtpSecurityException(
								String.format("Unsupported MIKEY SP Encr Auth Algo type 0x%02X", tmpPolParamData[0])
							);
					}
					break;
				case MMSPPT_SAKL:
					if (tmpPolParamData[0] != KeySizes.AUTH_KEY_SIZE_080 && tmpPolParamData[0] != KeySizes.AUTH_KEY_SIZE_160) {
						throw new SrtpSecurityException(
								String.format("Unsupported MIKEY SP Auth Key Length 0x%02X", tmpPolParamData[0])
							);
					}
					mikeyData.spAuthKeyLen = tmpPolParamData[0];
					break;
				case MMSPPT_SSKL:
					if (tmpPolParamData[0] != KeySizes.SALT_SIZE) {
						throw new SrtpSecurityException(
								String.format("Unsupported MIKEY SP Salt Key Length 0x%02X", tmpPolParamData[0])
							);
					}
					break;
				case MMSPPT_SPRF:
					if (tmpPolParamData[0] != MickeyOtherConstants.MOC_PT_SP_PRF_ALG_AESCM) {
						throw new SrtpSecurityException(
								String.format("Unsupported MIKEY SP PRF Algo type 0x%02X", tmpPolParamData[0])
							);
					}
					break;
				case MMSPPT_SRTPENCEN:
					if (tmpPolParamData[0] != MickeyOtherConstants.MOC_PT_SP_ENABLED) {
						throw new SrtpSecurityException("Unsupported MIKEY SP SRTP Encr Enabled value");
					}
					break;
				case MMSPPT_SRTCPENCEN:
					if (tmpPolParamData[0] != MickeyOtherConstants.MOC_PT_SP_ENABLED) {
						throw new SrtpSecurityException("Unsupported MIKEY SP SRTCP Encr Enabled value");
					}
					break;
				case MMSPPT_SRTPAUTHEN:
					if (tmpPolParamData[0] != MickeyOtherConstants.MOC_PT_SP_ENABLED) {
						throw new SrtpSecurityException("Unsupported MIKEY SP SRTP Auth Enabled value");
					}
					break;
				case MMSPPT_AUTHTAGLENGTH:
					if (tmpPolParamData[0] != KeySizes.AUTH_TAG_SIZE) {
						throw new SrtpSecurityException(
								String.format("Unsupported MIKEY SP Auth Tag Length 0x%02X", tmpPolParamData[0])
							);
					}
					break;
				case MMSPPT_SFECO:
				case MMSPPT_SRTPREFIXLEN:
				case MMSPPT_KDR:
					// nothing to check for the time being
					break;
				default:
					throw new SrtpSecurityException("Unhandled MIKEY SP Parameter " + mmspType);
			}
		}
	}

	private static MickeyMsgPayloadType parsePtKemac(ByteBuffer msgBb, final MikeyData mikeyData) throws SrtpSecurityException {
		byte tmpBy = msgBb.get();
		MickeyMsgEncrAlg tmpEncrAlg = MickeyMsgEncrAlg.of(tmpBy);  // encryption algorithm (for the KEMAC data)
		validateKemacEncrAlgo(tmpBy, tmpEncrAlg);

		short tmpEncrDataLen = msgBb.getShort();
		if (msgBb.remaining() < tmpEncrDataLen) {
			throw new SrtpSecurityException("Invalid MIKEY payload length (needed " +
					tmpEncrDataLen + ", got " + msgBb.remaining() + ")");
		}
		byte[] tmpEncrData = new byte[tmpEncrDataLen];
		msgBb.get(tmpEncrData);
		MickeyMsgPayloadType resEn = parseKemacKeyDataSubPayload(tmpEncrData, mikeyData);
		//
		tmpBy = msgBb.get();  // MAC algorithm (for the KEMAC data)
		if (tmpBy == MickeyOtherConstants.MOC_KEMAC_MAC_ALG_HMACSHA1160) {
			final int KEMAC_MAC_DATA_LEN = 10;
			if (msgBb.remaining() < KEMAC_MAC_DATA_LEN) {
				throw new SrtpSecurityException("Invalid MIKEY payload length (needed " +
						KEMAC_MAC_DATA_LEN + ", got " + msgBb.remaining() + ")");
			}
			byte[] tmpMacData = new byte[KEMAC_MAC_DATA_LEN];
			msgBb.get(tmpMacData);
		}

		return resEn;
	}

	private static void validateKemacEncrAlgo(byte rawVal, MickeyMsgEncrAlg encrAlg) throws SrtpSecurityException {
		if (encrAlg == MickeyMsgEncrAlg.MMEA_UNKNOWN) {
			throw new SrtpSecurityException(String.format("Unknown MIKEY encr algo: 0x%02X", rawVal));
		}
		if (encrAlg != MickeyMsgEncrAlg.MMEA_NULL) {
			throw new SrtpSecurityException("Unsupported MIKEY encr algo: " + encrAlg);
		}
	}

	/**
	 * Parse the KEMAC key data sub-payload.<br />
	 * See <a href="https://www.rfc-editor.org/rfc/rfc3830.html#section-6.13">RFC-3830 Section 6.13</a>
	 */
	private static MickeyMsgPayloadType parseKemacKeyDataSubPayload(
				byte[] kemacSubPayload,
				final MikeyData mikeyData
			) throws SrtpSecurityException {
		ByteBuffer buf = ByteBuffer.wrap(kemacSubPayload).order(ByteOrder.BIG_ENDIAN);

		/*
		 * 00 20 001E A5F06FCC8793C6631D5FDB536C1CF02E F6CC27DBDD42D136CA70AF6EF81F
		 */

		byte tmpBy = buf.get();  // next payload type
		MickeyMsgPayloadType resEn = MickeyMsgPayloadType.of(tmpBy);
		if (resEn == MickeyMsgPayloadType.MMPT_UNKNOWN) {
			throw new SrtpSecurityException(String.format("Unknown payload type in MIKEY KEMAC: 0x%02X", tmpBy));
		}

		//
 		///
		tmpBy = buf.get();  // type and KV (key validity period)
		///
		byte tmpSubPt = (byte)((tmpBy & 0xF0) >> 4);
		MickeyMsgKemacPayloadType tmpKemacPt = MickeyMsgKemacPayloadType.of(tmpSubPt);
		if (tmpKemacPt == MickeyMsgKemacPayloadType.MMKEMPT_UNKNOWN) {
			throw new SrtpSecurityException(String.format("Unknown sub-payload type in MIKEY KEMAC: 0x%02X", tmpSubPt));
		}
		if (tmpKemacPt != MickeyMsgKemacPayloadType.MMKEMPT_TEK_ONLY) {
			mikeyData.kemacHaveNonTekOnlyPayload = true;
		}
		///
		byte tmpKv = (byte)(tmpBy & 0x0F);
		mikeyData.kemacKvType = MickeyMsgKemacKv.of(tmpKv);
		if (mikeyData.kemacKvType == MickeyMsgKemacKv.MMKEMKV_UNKNOWN) {
			throw new SrtpSecurityException(String.format("Unknown KV in MIKEY KEMAC: 0x%02X", tmpKv));
		}

		short tmpKeyDataLen = buf.getShort();
		if (buf.remaining() < tmpKeyDataLen) {
			throw new SrtpSecurityException("Invalid MIKEY KEMAC payload length (needed " +
					tmpKeyDataLen + ", got " + buf.remaining() + ")");
		}
		byte[] keyData = new byte[tmpKeyDataLen];
		buf.get(keyData);

		// SRTP expects 16 byte key + 14 byte salt
		if (keyData.length != KeySizes.AES_128_KEY_SIZE + KeySizes.SALT_SIZE) {
			throw new SrtpSecurityException("Invalid SRTP key length in MIKEY KEMAC");
		}

		byte[] tmpMasterKey  = new byte[KeySizes.AES_128_KEY_SIZE];
		byte[] tmpMasterSalt = new byte[KeySizes.SALT_SIZE];

		System.arraycopy(keyData, 0, tmpMasterKey, 0, KeySizes.AES_128_KEY_SIZE);
		System.arraycopy(keyData, KeySizes.AES_128_KEY_SIZE, tmpMasterSalt, 0, KeySizes.SALT_SIZE);

		mikeyData.kemacMasterKey.copyOf(tmpMasterKey);
		mikeyData.kemacMasterSalt.copyOf(tmpMasterSalt);
		mikeyData.kemacHaveKeys = true;

		// TGK/TEK Salt
		if (tmpKemacPt == MickeyMsgKemacPayloadType.MMKEMPT_TGK_SALT ||
				tmpKemacPt == MickeyMsgKemacPayloadType.MMKEMPT_TEK_SALT) {
			short tmpSaltLen = buf.getShort();
			extractBytes("KEMAC TGK/TEK Salt", buf, tmpSaltLen, mikeyData.kemacTekTgkSalt);
		}

		// KV data
		if (mikeyData.kemacKvType == MickeyMsgKemacKv.MMKEMKV_SPI_OR_MKI) {
			byte tmpKvLen = buf.get();
			extractBytes("KEMAC KV SPI/MKI", buf, tmpKvLen, mikeyData.kemacKvDataSpiOrMki);
		} else if (mikeyData.kemacKvType == MickeyMsgKemacKv.MMKEMKV_INTV) {
			byte tmpVfLen = buf.get();
			extractBytes("KEMAC KV INTV F", buf, tmpVfLen, mikeyData.kemacKvDataIntvF);
			byte tmpVtLen = buf.get();
			extractBytes("KEMAC KV INTV T", buf, tmpVtLen, mikeyData.kemacKvDataIntvT);
		}

		return resEn;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void extractBytes(String desc, ByteBuffer buf, int length, BufferExt output) throws SrtpSecurityException {
		if (buf.remaining() < length) {
			throw new SrtpSecurityException("Invalid MIKEY " + desc + " length (needed " +
					length + ", got " + buf.remaining() + ")");
		}
		byte[] tmpData = new byte[length];
		buf.get(tmpData);
		output.copyOf(tmpData);
	}

}

package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.security.constants.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * Parser for MIKEY messages.<br />
 * MIKEY: Multimedia Internet KEYing, see <a href="https://datatracker.ietf.org/doc/html/rfc3830">RFC-3830</a>
 */
public final class MikeyParser {

	private MikeyParser() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse a MIKEY message and extract the SRTxP Key Management Data (KMD).
	 * @param msgB64 Base64 encoded MIKEY message
	 * @return SRTxP Key Management Data
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public static @NonNull SrtxpKmd parseMickeyMsgIntoKmd(@NonNull String msgB64) throws SrtxpSecurityException {
		MikeyData mikeyData = MikeyParser.parseMickeyMsg(msgB64);
		return extractKmdFromMd(mikeyData);
	}

	/**
	 * Extract the SRTxP Key Management Data (KMD) from MIKEY data.
	 * @param mikeyData MIKEY data
	 * @return SRTxP Key Management Data
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public static @NonNull SrtxpKmd extractKmdFromMd(@NonNull MikeyData mikeyData) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".extractKmdFromMd()";

		String errMsg = null;
		if (mikeyData.spSaltLen != KeySizes.SALT_SIZE) {
			errMsg = String.format(
					"Unsupported MIKEY SP Salt Key Length %d, only %d supported", mikeyData.spSaltLen, KeySizes.SALT_SIZE
				);
		} else if (! mikeyData.spEnabledEncrRtp) {
			errMsg = "Unsupported MIKEY SP Enable SRTP Encr value - must be enabled";
		} else if (! mikeyData.spEnabledEncrRtcp) {
			errMsg = "Unsupported MIKEY SP Enable SRTCP Encr value - must be enabled";
		} else if (! mikeyData.spEnabledAuthRtxp) {
			errMsg = "Unsupported MIKEY SP Enable SRTxP Auth value - must be enabled";
		} else if (mikeyData.spAuthTagLen < 1) {
			errMsg = String.format("Unsupported MIKEY SP Auth Tag Length %d, must be > 0", mikeyData.spAuthTagLen);
		} else if (! mikeyData.kemacHaveKeys) {
			errMsg = "No KEMAC payload found";
		} else if (mikeyData.kemacKvType != MikeyMsgKemacKv.MMKEMKV_SPI_OR_MKI &&
				mikeyData.kemacKvType != MikeyMsgKemacKv.MMKEMKV_NULL) {
			errMsg = "Unsupported KEMAC KV type - must be SPI/MKI or NULL";
		} else if (mikeyData.kemacHaveNonTekOnlyPayload) {
			errMsg = "Unsupported KEMAC payload type (only TEK w/o Salt is supported)";
		}
		if (errMsg != null) {
			throw new SrtxpSecurityException(FNC_NAME + ": " + errMsg);
		}
		//
		return new SrtxpKmd(
				mikeyData.spEncrKeyLen,
				mikeyData.kemacMasterKey,
				mikeyData.kemacMasterSalt,
				mikeyData.spAuthKeyLen,
				mikeyData.spAuthTagLen,
				mikeyData.kemacKvDataSpiOrMki,
				mikeyData.hdCsIdMapInfoSsrcArr[0],
				mikeyData.spKdr
			);
	}

	/**
	 * Parse a MIKEY message.
	 * @param msgB64 Base64 encoded MIKEY message
	 * @return MIKEY Data
	 * @throws SrtxpSecurityException If any kind of error occurred
	 */
	public static @NonNull MikeyData parseMickeyMsg(@NonNull String msgB64) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".parseMickeyMsg()";

		byte[] msgBa;
		try {
			msgBa = Base64.getDecoder().decode(msgB64);
		} catch (IllegalArgumentException e) {
			throw new SrtxpSecurityException(FNC_NAME + ": Invalid base64 encoding");
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

		// parse MIKEY header
		MikeyMsgPayloadType nextPayloadType = parseCommonHeader(msgBb, mikeyData);

		// parse MIKEY payloads
		try {
			while (msgBb.remaining() >= 4) {
				byte tmpBy = msgBb.get();
				MikeyMsgPayloadType tmpNextPt = MikeyMsgPayloadType.of(tmpBy);
				if (tmpNextPt == MikeyMsgPayloadType.MMPT_UNKNOWN) {
					throw new SrtxpSecurityException(String.format("%s: Unknown MIKEY payload type: 0x%02X", FNC_NAME, tmpBy));
				}

				//
				boolean stopLoop = false;
				switch (nextPayloadType) {
					case MMPT_T: parsePtTimestamp(msgBb, mikeyData); break;
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
				if (nextPayloadType == MikeyMsgPayloadType.MMPT_LAST) {
					break;
				}
			}
		} catch (BufferUnderflowException e) {
			throw new SrtxpSecurityException(FNC_NAME + ": Invalid MIKEY payload length");
		}

		if (msgBb.remaining() != 0) {
			throw new SrtxpSecurityException(FNC_NAME + ": MIKEY payload not entirely consumed");
		}

		return mikeyData;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse Common Header payload (RFC-3830 Section 6.1)
	 */
	private static MikeyMsgPayloadType parseCommonHeader(ByteBuffer msgBb, MikeyData mikeyData) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".parseCommonHeader()";

		// version
		byte tmpHdVers = msgBb.get();
		if (tmpHdVers != MikeyOtherConstants.MOC_CHD_VERSION) {
			throw new SrtxpSecurityException(FNC_NAME + ": Unsupported MIKEY version");
		}
		// data type
		byte tmpHdDt = msgBb.get();
		MikeyMsgDataType tmpDataType = MikeyMsgDataType.of(tmpHdDt);
		if (tmpDataType != MikeyMsgDataType.MMDT_PRE_SHARED_KEY) {
			throw new SrtxpSecurityException(String.format("%s: Unsupported MIKEY data type: 0x%02X", FNC_NAME, tmpHdDt));
		}
		// next payload
		byte tmpHdNp = msgBb.get();
		MikeyMsgPayloadType nextPayloadType = MikeyMsgPayloadType.of(tmpHdNp);
		if (nextPayloadType == MikeyMsgPayloadType.MMPT_UNKNOWN) {
			throw new SrtxpSecurityException(String.format("%s: Unknown MIKEY payload type: 0x%02X", FNC_NAME, tmpHdNp));
		}
		// V | PRF_FUNC
		byte tmpHdVandPrf = msgBb.get();
		byte tmpHdV = (byte)(tmpHdVandPrf & MikeyOtherConstants.MOC_CHD_V_BIT_RESP);
		if (tmpHdV == MikeyOtherConstants.MOC_CHD_V_BIT_RESP) {
			throw new SrtxpSecurityException(String.format("%s: Unsupported MIKEY V value: 0x%02X", FNC_NAME, tmpHdV));
		}
		byte tmpHdPrf = (byte)(tmpHdVandPrf & (byte)0x7F);
		if (tmpHdPrf != MikeyOtherConstants.MOC_CHD_PRF_FUNC_MIKEY1) {
			throw new SrtxpSecurityException(String.format("%s: Unsupported MIKEY PRF value: 0x%02X", FNC_NAME, tmpHdPrf));
		}
		// CSB_ID
		mikeyData.hdCsbId = msgBb.getInt();
		// #CS (indicates the number of Crypto Sessions that will be handled within the CBS)
		mikeyData.hdCsNr = msgBb.get();
		// CS_ID_map_type
		byte tmpCsIdMapType = msgBb.get();
		if (tmpCsIdMapType != MikeyOtherConstants.MOC_CHD_CS_ID_MAP_TYPE_SRTP_ID) {
			throw new SrtxpSecurityException(String.format("%s: Unsupported MIKEY CS_ID_map_type value: 0x%02X", FNC_NAME, tmpCsIdMapType));
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
			throw new SrtxpSecurityException(FNC_NAME + ": Multiple MIKEY Security Policies are not supported");
		}
		if (mikeyData.hdCsIdMapInfoRocArr[0] != 0) {
			throw new SrtxpSecurityException(FNC_NAME + ": Initial ROC in MIKEY CHD is not 0");
		}

		return nextPayloadType;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse Timestamp payload (RFC-3830 Section 6.6)
	 */
	private static void parsePtTimestamp(ByteBuffer msgBb, MikeyData mikeyData) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".parsePtTimestamp()";

		byte tmpBy = msgBb.get();
		mikeyData.tsType = MikeyMsgTimestampType.of(tmpBy);
		switch (mikeyData.tsType) {
			case MikeyMsgTimestampType.MMTST_NTP_DEF:
			case MikeyMsgTimestampType.MMTST_NTP_UTC:
				mikeyData.tsValueNtc = msgBb.getLong();  // TS value 64-bits
				break;
			case MikeyMsgTimestampType.MMTST_COUNTER:
				mikeyData.tsValueCnt = msgBb.getInt();  // TS value 32-bits
				break;
			default:
				throw new SrtxpSecurityException(String.format("%s: Unknown MIKEY Timestamp type: 0x%02X", FNC_NAME, tmpBy));
		}
	}

	/**
	 * Parse RAND payload (RFC-3830 Section 6.11)
	 */
	private static void parsePtRand(ByteBuffer msgBb, MikeyData mikeyData) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".parsePtRand()";

		byte tmpBy = msgBb.get();
		extractBytes(FNC_NAME, "RAND", msgBb, tmpBy, mikeyData.randRandData);
	}

	/**
	 * Parse Security Policy payload (RFC-3830 Section 6.10)
	 */
	private static void parsePtSecurityPolicy(ByteBuffer msgBb, MikeyData mikeyData) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".parsePtSecurityPolicy()";

		byte tmpBy = msgBb.get();  // Policy No
		if (tmpBy != 0) {
			throw new SrtxpSecurityException("Multiple MIKEY Security Policies are not supported yet");
		}
		tmpBy = msgBb.get();  // Prot Type
		if (tmpBy != MikeyOtherConstants.MOC_PT_SP_PROT_SRTP) {
			throw new SrtxpSecurityException(String.format("%s: Unsupported MIKEY SP prot type: 0x%02X", FNC_NAME, tmpBy));
		}
		short tmpSpLen = msgBb.getShort();  // Policy param length
		if (msgBb.remaining() < tmpSpLen) {
			throw new SrtxpSecurityException(FNC_NAME + ": Invalid MIKEY payload length (needed " +
					tmpSpLen + ", got " + msgBb.remaining() + ")");
		}
		byte[] tmpPolicyData = new byte[tmpSpLen];
		msgBb.get(tmpPolicyData);
		//
		parseSubPtSecurityPolicyData(tmpPolicyData, mikeyData);
	}

	private static void parseSubPtSecurityPolicyData(byte[] policyData, MikeyData mikeyData) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".parseSubPtSecurityPolicyData()";

		ByteBuffer tmpSpParamsBuf = ByteBuffer.wrap(policyData).order(ByteOrder.BIG_ENDIAN);

		while (tmpSpParamsBuf.remaining() != 0) {
			byte tmpBy = tmpSpParamsBuf.get();  // Parameter Type
			MikeyMsgSecPolicyParamType mmspType = MikeyMsgSecPolicyParamType.of(tmpBy);
			if (mmspType == MikeyMsgSecPolicyParamType.MMSPPT_UNKNOWN) {
				throw new SrtxpSecurityException(String.format("%s: Unknown MIKEY SP Parameter type: 0x%02X", FNC_NAME, tmpBy));
			}
			tmpBy = tmpSpParamsBuf.get();  // Parameter Length
			if (tmpSpParamsBuf.remaining() < tmpBy) {
				throw new SrtxpSecurityException(FNC_NAME + ": Invalid MIKEY SP payload length (needed " +
						tmpBy + ", got " + tmpSpParamsBuf.remaining() + ")");
			}
			if (tmpBy == 0) {
				continue;
			}
			byte[] tmpPolParamData = new byte[tmpBy];
			tmpSpParamsBuf.get(tmpPolParamData);

			final byte tmpPpdByte1 = tmpPolParamData[0];

			String errMsg = null;
			switch (mmspType) {
				case MMSPPT_ENCALG:
					if (tmpPpdByte1 != MikeyOtherConstants.MOC_PT_SP_ENC_ALG_AESCM) {
						errMsg = String.format("Unsupported MIKEY SP Encr Algo type 0x%02X", tmpPpdByte1);
					}
					break;
				case MMSPPT_SEKL:
					if (tmpPpdByte1 != KeySizes.AES_KEY_SIZE_128 && tmpPpdByte1 != KeySizes.AES_KEY_SIZE_256) {
						errMsg = String.format("Unsupported MIKEY SP Encr Key Length 0x%02X", tmpPpdByte1);
					} else {
						mikeyData.spEncrKeyLen = tmpPpdByte1;
					}
					break;
				case MMSPPT_AUTHALG:
					if (tmpPpdByte1 != MikeyOtherConstants.MOC_PT_SP_AUTH_ALG_HMACSHA1) {
						errMsg = String.format("Unsupported MIKEY SP Encr Auth Algo type 0x%02X", tmpPpdByte1);
					}
					break;
				case MMSPPT_SAKL:
					if (tmpPpdByte1 != KeySizes.AUTH_KEY_SIZE_080 && tmpPpdByte1 != KeySizes.AUTH_KEY_SIZE_160) {
						errMsg = String.format("Unsupported MIKEY SP Auth Key Length 0x%02X", tmpPpdByte1);
					} else {
						mikeyData.spAuthKeyLen = tmpPpdByte1;
					}
					break;
				case MMSPPT_SSKL:
					if (tmpPpdByte1 == 0x00) {
						errMsg = String.format("Unsupported MIKEY SP Salt Key Length 0x%02X", tmpPpdByte1);
					}
					mikeyData.spSaltLen = tmpPpdByte1;
					break;
				case MMSPPT_SPRF:
					if (tmpPpdByte1 != MikeyOtherConstants.MOC_PT_SP_PRF_ALG_AESCM) {
						errMsg = String.format("Unsupported MIKEY SP PRF Algo type 0x%02X", tmpPpdByte1);
					}
					break;
				case MMSPPT_SRTPENCEN:
					mikeyData.spEnabledEncrRtp = (tmpPpdByte1 == MikeyOtherConstants.MOC_PT_SP_ENABLED);
					break;
				case MMSPPT_SRTCPENCEN:
					mikeyData.spEnabledEncrRtcp = (tmpPpdByte1 == MikeyOtherConstants.MOC_PT_SP_ENABLED);
					break;
				case MMSPPT_SRTPAUTHEN:
					mikeyData.spEnabledAuthRtxp = (tmpPpdByte1 == MikeyOtherConstants.MOC_PT_SP_ENABLED);
					break;
				case MMSPPT_AUTHTAGLENGTH:
					if (tmpPpdByte1 > KeySizes.SHA1_SIZE_160) {
						errMsg = String.format("Unsupported MIKEY SP Auth Tag length %d, max. is %d",
								tmpPpdByte1, KeySizes.SHA1_SIZE_160);
					} else {
						mikeyData.spAuthTagLen = tmpPpdByte1;
					}
					break;
				case MMSPPT_SFECO:
				case MMSPPT_SRTPREFIXLEN:
					// nothing to check for the time being
					break;
				case MMSPPT_KDR:
					ByteBuffer tmpKdrBuf = ByteBuffer.wrap(tmpPolParamData).order(ByteOrder.BIG_ENDIAN);
					switch (tmpBy) {
						case 1: mikeyData.spKdr = Byte.toUnsignedLong(tmpKdrBuf.get()); break;
						case 2: mikeyData.spKdr = Short.toUnsignedLong(tmpKdrBuf.getShort()); break;
						case 4: mikeyData.spKdr = Integer.toUnsignedLong(tmpKdrBuf.getInt()); break;
						case 8: mikeyData.spKdr = tmpKdrBuf.getLong(); break;
						default:
							errMsg = String.format("Unsupported MIKEY SP KDR length %d, expected 0/1/2/4/8", tmpBy);
							break;
					}
					break;
				default:
					errMsg = "Unhandled MIKEY SP Parameter " + mmspType;
			}
			if (errMsg != null) {
				throw new SrtxpSecurityException(FNC_NAME + ": " + errMsg);
			}
		}
	}

	/**
	 * Parse Key data transport payload aka KEMAC (RFC-3830 Section 6.2)
	 */
	private static MikeyMsgPayloadType parsePtKemac(ByteBuffer msgBb, MikeyData mikeyData) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".parsePtKemac()";

		byte tmpBy = msgBb.get();
		MikeyMsgKemacEncrAlg tmpEncrAlg = MikeyMsgKemacEncrAlg.of(tmpBy);  // encryption algorithm (for the KEMAC data)
		validateKemacEncrAlgo(tmpBy, tmpEncrAlg);

		short tmpEncrDataLen = msgBb.getShort();
		if (msgBb.remaining() < tmpEncrDataLen) {
			throw new SrtxpSecurityException(FNC_NAME + ": Invalid MIKEY payload length (needed " +
					tmpEncrDataLen + ", got " + msgBb.remaining() + ")");
		}
		byte[] tmpEncrData = new byte[tmpEncrDataLen];
		msgBb.get(tmpEncrData);
		MikeyMsgPayloadType resEn = parseSubPtKemacKeyDataSubPayload(tmpEncrData, mikeyData);
		//
		tmpBy = msgBb.get();  // MAC algorithm (for the KEMAC data)
		if (tmpBy == MikeyOtherConstants.MOC_KEMAC_MAC_ALG_HMACSHA1160) {
			final int KEMAC_MAC_DATA_LEN = 10;
			if (msgBb.remaining() < KEMAC_MAC_DATA_LEN) {
				throw new SrtxpSecurityException(FNC_NAME + ": Invalid MIKEY payload length (needed " +
						KEMAC_MAC_DATA_LEN + ", got " + msgBb.remaining() + ")");
			}
			extractBytes(FNC_NAME, "KEMAC MAC data", msgBb, KEMAC_MAC_DATA_LEN, mikeyData.kemacMacData);
		}

		return resEn;
	}

	private static void validateKemacEncrAlgo(byte rawVal, MikeyMsgKemacEncrAlg encrAlg) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".validateKemacEncrAlgo()";

		if (encrAlg == MikeyMsgKemacEncrAlg.MMEA_UNKNOWN) {
			throw new SrtxpSecurityException(String.format("%s: Unknown MIKEY KEMAC encr algo: 0x%02X", FNC_NAME, rawVal));
		}
		if (encrAlg != MikeyMsgKemacEncrAlg.MMEA_NULL) {
			throw new SrtxpSecurityException(
					String.format(
							"%s: Unsupported MIKEY KEMAC encr algo: 0x%02X - only 0x%02X is supported",
							FNC_NAME, rawVal, MikeyMsgKemacEncrAlg.MMEA_NULL.getValue()
						)
				);
		}
	}

	/**
	 * Parse the KEMAC key data sub-payload (RFC-3830 Section 6.13)
	 */
	private static MikeyMsgPayloadType parseSubPtKemacKeyDataSubPayload(
				byte[] kemacSubPayload,
				final MikeyData mikeyData
			) throws SrtxpSecurityException {
		final String FNC_NAME = MikeyParser.class.getSimpleName() + ".parseSubPtKemacKeyDataSubPayload()";

		ByteBuffer buf = ByteBuffer.wrap(kemacSubPayload).order(ByteOrder.BIG_ENDIAN);

		/*
		 * 00 20 001E A5F06FCC8793C6631D5FDB536C1CF02E F6CC27DBDD42D136CA70AF6EF81F
		 */

		byte tmpBy = buf.get();  // next payload type
		MikeyMsgPayloadType resEn = MikeyMsgPayloadType.of(tmpBy);
		if (resEn == MikeyMsgPayloadType.MMPT_UNKNOWN) {
			throw new SrtxpSecurityException(String.format("%s: Unknown payload type in MIKEY KEMAC: 0x%02X", FNC_NAME, tmpBy));
		}

		//
		tmpBy = buf.get();  // type and KV (key validity period)
		//
		byte tmpSubPt = (byte)((tmpBy & 0xF0) >> 4);
		mikeyData.kemacPt = MikeyMsgKemacPayloadType.of(tmpSubPt);
		if (mikeyData.kemacPt == MikeyMsgKemacPayloadType.MMKEMPT_UNKNOWN) {
			throw new SrtxpSecurityException(String.format("%s: Unknown sub-payload type in MIKEY KEMAC: 0x%02X", FNC_NAME, tmpSubPt));
		}
		if (mikeyData.kemacPt != MikeyMsgKemacPayloadType.MMKEMPT_TEK_ONLY) {
			mikeyData.kemacHaveNonTekOnlyPayload = true;
		}
		//
		byte tmpKv = (byte)(tmpBy & 0x0F);
		mikeyData.kemacKvType = MikeyMsgKemacKv.of(tmpKv);
		if (mikeyData.kemacKvType == MikeyMsgKemacKv.MMKEMKV_UNKNOWN) {
			throw new SrtxpSecurityException(String.format("%s: Unknown KV in MIKEY KEMAC: 0x%02X", FNC_NAME, tmpKv));
		}

		short tmpKeyDataLen = buf.getShort();  // key data length
		if (buf.remaining() < tmpKeyDataLen) {
			throw new SrtxpSecurityException(FNC_NAME + ": Invalid MIKEY KEMAC payload length (needed " +
					tmpKeyDataLen + ", got " + buf.remaining() + ")");
		}

		// needs to be Encryption key + Salt
		if (tmpKeyDataLen != mikeyData.spEncrKeyLen + mikeyData.spSaltLen) {
			throw new SrtxpSecurityException(FNC_NAME + ": Invalid MIKEY KEMAC key length: is=" + tmpKeyDataLen +
					", exp=" + (mikeyData.spEncrKeyLen + mikeyData.spSaltLen));
		}
		extractBytes(FNC_NAME, "KEMAC Master Key data", buf, mikeyData.spEncrKeyLen, mikeyData.kemacMasterKey);
		extractBytes(FNC_NAME, "KEMAC Master Salt data", buf, mikeyData.spSaltLen, mikeyData.kemacMasterSalt);

		mikeyData.kemacHaveKeys = true;

		// TGK/TEK Salt
		if (mikeyData.kemacPt == MikeyMsgKemacPayloadType.MMKEMPT_TGK_SALT ||
				mikeyData.kemacPt == MikeyMsgKemacPayloadType.MMKEMPT_TEK_SALT) {
			short tmpSaltLen = buf.getShort();
			extractBytes(FNC_NAME, "KEMAC TGK/TEK Salt", buf, tmpSaltLen, mikeyData.kemacTekTgkSalt);
		}

		// KV data
		if (mikeyData.kemacKvType == MikeyMsgKemacKv.MMKEMKV_SPI_OR_MKI) {
			byte tmpKvLen = buf.get();
			extractBytes(FNC_NAME, "KEMAC KV SPI/MKI", buf, tmpKvLen, mikeyData.kemacKvDataSpiOrMki);
		} else if (mikeyData.kemacKvType == MikeyMsgKemacKv.MMKEMKV_INTV) {
			byte tmpVfLen = buf.get();
			extractBytes(FNC_NAME, "KEMAC KV INTV F", buf, tmpVfLen, mikeyData.kemacKvDataIntvF);
			byte tmpVtLen = buf.get();
			extractBytes(FNC_NAME, "KEMAC KV INTV T", buf, tmpVtLen, mikeyData.kemacKvDataIntvT);
		}

		return resEn;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void extractBytes(
				String fncName,
				String desc,
				ByteBuffer buf,
				int length,
				BufferExt output
			) throws SrtxpSecurityException {
		if (buf.remaining() < length) {
			throw new SrtxpSecurityException(fncName + ": Invalid MIKEY " + desc + " length (needed " +
					length + ", got " + buf.remaining() + ")");
		}
		byte[] tmpData = new byte[length];
		buf.get(tmpData);
		output.copyOf(tmpData);
	}

}

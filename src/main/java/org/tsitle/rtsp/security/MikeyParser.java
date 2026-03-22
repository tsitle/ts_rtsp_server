package org.tsitle.rtsp.security;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.security.constants.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.HexFormat;

public class MikeyParser {

	public record SrtpKeys(byte[] masterKey, byte[] masterSalt) { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse a MIKEY message according to RFC-3830 Section 6.2 (Key data transport payload aka KEMAC).
	 * @param msgB64 Base64 encoded MIKEY message
	 * @return SRTP keys
	 * @throws SrtpSecurityException If any kind of error occurred
	 */
	public static @NonNull SrtpKeys parseKeyMgmtData(@NonNull String msgB64) throws SrtpSecurityException {
		byte[] msg;
		try {
			msg = Base64.getDecoder().decode(msgB64);
		} catch (IllegalArgumentException e) {
			throw new SrtpSecurityException("Invalid base64 encoding");
		}
		ByteBuffer buf = ByteBuffer.wrap(msg).order(ByteOrder.BIG_ENDIAN);

		System.err.println("MIKEY: " + "0x" + HexFormat.of().withUpperCase().formatHex(msg));  // @TODO
		// parse MIKEY header -- RFC-3830 Section 6.1 (Common Header payload aka HDR)
		///
		byte tmpHdVers = buf.get();  // version
		if (tmpHdVers != 0x01) {
			throw new SrtpSecurityException("Unsupported MIKEY version");
		}
		byte tmpHdDt = buf.get();  // data type
		byte tmpHdNp = buf.get();  // next payload
		buf.get();  // V | PRF_FUNC
		///
		buf.getInt();  // CSB_ID
		///
		buf.get();  // #CS
		byte tmpCsIdMapType = buf.get();  // CS_ID_map_type
		buf.getShort();  // CS_ID_map_info
		MickeyMsgDataType tmpDataType = MickeyMsgDataType.of(tmpHdDt);
		System.err.println("MIKEY: hdr.dataTp=" + tmpDataType);  // @TODO
		MickeyMsgPayloadType nextPayloadType = MickeyMsgPayloadType.of(tmpHdNp);
		System.err.println("MIKEY: hdr.nextPay=" + nextPayloadType);  // @TODO
		if (tmpDataType != MickeyMsgDataType.MMDT_PRE_SHARED_KEY) {
			throw new SrtpSecurityException("Unsupported MIKEY data type: " + String.format(" (0x%02X)", tmpHdDt));
		}
		if (nextPayloadType == MickeyMsgPayloadType.MMPT_UNKNOWN) {
			throw new SrtpSecurityException("Unknown MIKEY payload type: " + String.format(" (0x%02X)", tmpHdNp));
		}
		if (tmpCsIdMapType == 0x00) {  // SRTP-ID
			/*
			 * these 7 bytes could not be identified from RFC-3830. just skipping them for now
			 */
			buf.get();
			buf.get();
			buf.get();
			buf.getInt();
		}

		try {
			BufferExt outpMasterKey = new BufferExt();
			BufferExt outpMasterSalt = new BufferExt();
			boolean foundKemacPayload = false;

			while (buf.remaining() >= 4) {
				/*
				 * Common Header: 01000500 FD3B3879 01000008 (will change with every request)
				 * CS ID Map    : CFD4AA 00000000            (will change with every request)
				 *
				 * Payloads (will change with every request):
				 *                    4                           24                                                          34                                                         30                                    1
				 * Payloads Ex. 1: 0B00ED69 3E26791937080A103AA888B96FE168054BEA5B32327C465A 010000001500010101011002010103010A0701010801010A0101000000220020001E B8B0EE27F00909578C1BFFC342BA0CA05115A3C4FBDB29603FE11D752036 00
				 * Payloads Ex. 2: 0B00ED69 3EB07453E2D60A101A1CCA564649E27AAE74395BB5206796 010000001500010101011002010103010A0701010801010A0101000000220020001E 8E4F1A96439D4C84597D6295FB0BA1A395D335E6230B3297C5404323585B 00
				 * Payloads Ex. 3: 0B00ED69 3EB1345985AD0A10181C2AE0DEFE1B1C907F08B94A5BEE50 010000001500010101011002010103010A0701010801010A0101000000220020001E D2AD978F862662A28534B5DDBAF8275B82196042C00E81B5AD2A8B76AC14 00
				 * Payloads Ex. 4: 0B00ED69 402CB4A7E73A0A102EE28DEA01C5FDD14DD2EEC9DE64D921 010000001500010101011002010103010A0701010801010A0101000000220020001E F749256D46DE3781410BD1D723B0784813569423CBA972A7BAB1099AF44D 00
				 */
				byte tmpBy = buf.get();
				MickeyMsgPayloadType tmpNextPt = MickeyMsgPayloadType.of(tmpBy);
				System.err.println("MIKEY: pay.nextPay=" + tmpNextPt + String.format(" (0x%02X)", tmpBy));  // @TODO
				if (tmpNextPt == MickeyMsgPayloadType.MMPT_UNKNOWN) {
					throw new SrtpSecurityException("Unknown MIKEY payload type: " + String.format(" (0x%02X)", tmpBy));
				}

				//
				boolean stopLoop = false;
				switch (nextPayloadType) {
					case MMPT_T:
						tmpBy = buf.get();
						MickeyMsgTimestampType tmpTsTp = MickeyMsgTimestampType.of(tmpBy);
						System.err.println("MIKEY: pay.T.ts=" + tmpTsTp + String.format(" (0x%02X)", tmpBy));  // @TODO
						if (tmpTsTp == MickeyMsgTimestampType.MMTST_UNKNOWN) {
							throw new SrtpSecurityException("Unknown MIKEY timestamp type: " + String.format(" (0x%02X)", tmpBy));
						}
						if (tmpTsTp != MickeyMsgTimestampType.MMTST_COUNTER) {
							buf.getLong();  // TS value 64-bits
						} else {
							buf.getInt();  // TS value 32-bits
						}
						break;
					case MMPT_RAND:
						tmpBy = buf.get();  // RAND_LEN
						System.err.println("MIKEY: pay.RAND.l=" + Integer.toUnsignedString(tmpBy));  // @TODO
						if (buf.remaining() < tmpBy) {
							throw new SrtpSecurityException("Invalid MIKEY payload length (needed " +
									tmpBy + ", got " + buf.remaining() + ")");
						}
						byte[] tmpRandData = new byte[tmpBy];
						buf.get(tmpRandData);
						System.err.println("MIKEY: pay.RAND.data=" + "0x" +
								HexFormat.of().withUpperCase().formatHex(tmpRandData));  // @TODO
						break;
					case MMPT_SP:
						tmpBy = buf.get();  // Policy No
						System.err.println("MIKEY: pay.SP.polno=" + Integer.toUnsignedString(tmpBy));  // @TODO
						tmpBy = buf.get();  // Prot Type
						System.err.println("MIKEY: pay.SP.prottp=" + Integer.toUnsignedString(tmpBy));  // @TODO
						short tmpSpLen = buf.getShort();  // Policy param length
						if (buf.remaining() < tmpSpLen) {
							throw new SrtpSecurityException("Invalid MIKEY payload length (needed " +
									tmpSpLen + ", got " + buf.remaining() + ")");
						}
						byte[] tmpPolicyData = new byte[tmpSpLen];
						buf.get(tmpPolicyData);
						//
						ByteBuffer tmpSpParamsBuf = ByteBuffer.wrap(tmpPolicyData).order(ByteOrder.BIG_ENDIAN);
						while (tmpSpParamsBuf.remaining() > 2) {
							tmpBy = tmpSpParamsBuf.get();  // Parameter Type
							System.err.println("MIKEY: pay.SP.x.partp=" + Integer.toUnsignedString(tmpBy));  // @TODO
							tmpBy = tmpSpParamsBuf.get();  // Parameter Length
							if (tmpSpParamsBuf.remaining() < tmpBy) {
								throw new SrtpSecurityException("Invalid MIKEY SP payload length (needed " +
										tmpBy + ", got " + tmpSpParamsBuf.remaining() + ")");
							}
							byte[] tmpPolParamData = new byte[tmpBy];
							tmpSpParamsBuf.get(tmpPolParamData);
							System.err.println("MIKEY: pay.SP.x.pardata=" + "0x" +
									HexFormat.of().withUpperCase().formatHex(tmpPolParamData));  // @TODO
						}
						break;
					case MMPT_KEMAC:
						tmpBy = buf.get();
						MickeyMsgEncrAlg tmpEncrAlg = MickeyMsgEncrAlg.of(tmpBy);
						System.err.println("MIKEY: pay.KEMAC.ea=" + tmpEncrAlg + String.format(" (0x%02X)", tmpBy));  // @TODO
						if (tmpEncrAlg == MickeyMsgEncrAlg.MMEA_UNKNOWN) {
							throw new SrtpSecurityException("Unknown MIKEY encr algo: " + String.format(" (0x%02X)", tmpBy));
						}
						short tmpEncrDataLen = buf.getShort();
						System.err.println("MIKEY: pay.KEMAC.edl=" + Integer.toUnsignedString(tmpEncrDataLen));  // @TODO
						if (buf.remaining() < tmpEncrDataLen) {
							throw new SrtpSecurityException("Invalid MIKEY payload length (needed " +
									tmpEncrDataLen + ", got " + buf.remaining() + ")");
						}
						byte[] tmpEncrData = new byte[tmpEncrDataLen];
						buf.get(tmpEncrData);
						System.err.println("MIKEY: pay.KEMAC.encrdata=" + "0x" +
								HexFormat.of().withUpperCase().formatHex(tmpEncrData));  // @TODO
						parseKemacKeyDataSubPayload(tmpEncrData, outpMasterKey, outpMasterSalt);
						//
						tmpBy = buf.get();
						System.err.println("MIKEY: pay.KEMAC.ma=" + String.format(" (0x%02X)", tmpBy));  // @TODO
						if (tmpBy == 1) {
							final int KEMAC_MAC_DATA_LEN = 10;
							if (buf.remaining() < KEMAC_MAC_DATA_LEN) {
								throw new SrtpSecurityException("Invalid MIKEY payload length (needed " +
										KEMAC_MAC_DATA_LEN + ", got " + buf.remaining() + ")");
							}
							byte[] tmpMacData = new byte[KEMAC_MAC_DATA_LEN];
							buf.get(tmpMacData);
							System.err.println("MIKEY: pay.KEMAC.macdata=" + "0x" +
									HexFormat.of().withUpperCase().formatHex(tmpMacData));  // @TODO
						}
						//
						foundKemacPayload = true;
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

			if (foundKemacPayload) {
				byte[] tmpMk = new byte[outpMasterKey.getUsed()];
				outpMasterKey.copyInto(0, tmpMk, 0, tmpMk.length);
				byte[] tmpMs = new byte[outpMasterSalt.getUsed()];
				outpMasterSalt.copyInto(0, tmpMs, 0, tmpMs.length);
				System.err.println("MIKEY: master Key : " + "0x" + HexFormat.of().withUpperCase().formatHex(tmpMk));  // @TODO
				System.err.println("MIKEY: master Salt: " + "0x" + HexFormat.of().withUpperCase().formatHex(tmpMs));  // @TODO
				return new MikeyParser.SrtpKeys(tmpMk, tmpMs);
			}
		} catch (BufferUnderflowException e) {
			throw new SrtpSecurityException("Invalid MIKEY payload length");
		}

		throw new SrtpSecurityException("No KEMAC payload found");
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse the KEMAC key data sub-payload.<br />
	 * See <a href="https://www.rfc-editor.org/rfc/rfc3830.html#section-6.13">RFC-3830 Section 6.13</a>
	 */
	private static void parseKemacKeyDataSubPayload(byte[] kemac, BufferExt outpMasterKey, BufferExt outpMasterSalt)
			throws SrtpSecurityException {
		ByteBuffer buf = ByteBuffer.wrap(kemac).order(ByteOrder.BIG_ENDIAN);

		/*
		 * 00 20 001E A5F06FCC8793C6631D5FDB536C1CF02E F6CC27DBDD42D136CA70AF6EF81F
		 */

		byte tmpBy = buf.get();  // next payload type
		MickeyMsgPayloadType tmpNextPt = MickeyMsgPayloadType.of(tmpBy);
		if (tmpNextPt == MickeyMsgPayloadType.MMPT_UNKNOWN) {
			throw new SrtpSecurityException("Invalid payload type in MIKEY KEMAC");
		}

		tmpBy = buf.get();  // type and KV (key validity period)
		byte tmpSubPt = (byte)((tmpBy & 0xF0) >> 4);
		MickeyMsgKemacPayloadType tmpKemacPt = MickeyMsgKemacPayloadType.of(tmpSubPt);
		System.err.println("MIKEY: pay.KEMAC.spt=" + tmpKemacPt + String.format(" (0x%02X)", tmpBy));  // @TODO
		if (tmpKemacPt == MickeyMsgKemacPayloadType.MMKEMPT_UNKNOWN) {
			throw new SrtpSecurityException("Invalid sub-payload type in MIKEY KEMAC");
		}
		byte tmpKv = (byte)(tmpBy & 0x0F);
		MickeyMsgKemacKv tmpKemacKv = MickeyMsgKemacKv.of(tmpKv);
		System.err.println("MIKEY: pay.KEMAC.kv=" + tmpKemacKv + String.format(" (0x%02X)", tmpBy));  // @TODO
		if (tmpKemacKv == MickeyMsgKemacKv.MMKEMKV_UNKNOWN) {
			throw new SrtpSecurityException("Invalid KV in MIKEY KEMAC");
		}

		short tmpKeyDataLen = buf.getShort();
		if (buf.remaining() < tmpKeyDataLen) {
			throw new SrtpSecurityException("Invalid MIKEY KEMAC payload length (needed " +
					tmpKeyDataLen + ", got " + buf.remaining() + ")");
		}
		byte[] keyData = new byte[tmpKeyDataLen];
		buf.get(keyData);

		// SRTP expects 16 byte key + 14 byte salt
		if (keyData.length < KeySizes.AES_128_KEY_SIZE + KeySizes.SALT_SIZE) {
			throw new SrtpSecurityException("Invalid SRTP key length in MIKEY KEMAC");
		}

		byte[] tmpMasterKey  = new byte[KeySizes.AES_128_KEY_SIZE];
		byte[] tmpMasterSalt = new byte[KeySizes.SALT_SIZE];

		System.arraycopy(keyData, 0, tmpMasterKey, 0, KeySizes.AES_128_KEY_SIZE);
		System.arraycopy(keyData, KeySizes.AES_128_KEY_SIZE, tmpMasterSalt, 0, KeySizes.SALT_SIZE);

		outpMasterKey.copyOf(tmpMasterKey);
		outpMasterSalt.copyOf(tmpMasterSalt);

		// Master Salt
		if (tmpKemacPt == MickeyMsgKemacPayloadType.MMKEMPT_TGK_SALT ||
				tmpKemacPt == MickeyMsgKemacPayloadType.MMKEMPT_TEK_SALT) {
			short tmpSaltLen = buf.getShort();
			if (buf.remaining() < tmpSaltLen) {
				throw new SrtpSecurityException("Invalid MIKEY KEMAC payload length (needed " +
						tmpSaltLen + ", got " + buf.remaining() + ")");
			}
			byte[] tmpSaltData = new byte[tmpSaltLen];
			buf.get(tmpSaltData);
		}

		// KV data
		if (tmpKemacKv == MickeyMsgKemacKv.MMKEMKV_SPI) {
			byte tmpKvLen = buf.get();
			if (buf.remaining() < tmpKvLen) {
				throw new SrtpSecurityException("Invalid MIKEY KEMAC payload length (needed " +
						tmpKvLen + ", got " + buf.remaining() + ")");
			}
			byte[] tmpKvData = new byte[tmpKvLen];
			buf.get(tmpKvData);
		} else if (tmpKemacKv == MickeyMsgKemacKv.MMKEMKV_INTV) {
			byte tmpVfLen = buf.get();
			if (buf.remaining() < tmpVfLen) {
				throw new SrtpSecurityException("Invalid MIKEY KEMAC payload length (needed " +
						tmpVfLen + ", got " + buf.remaining() + ")");
			}
			byte[] tmpVfData = new byte[tmpVfLen];
			buf.get(tmpVfData);
			//
			byte tmpVtLen = buf.get();
			if (buf.remaining() < tmpVtLen) {
				throw new SrtpSecurityException("Invalid MIKEY KEMAC payload length (needed " +
						tmpVtLen + ", got " + buf.remaining() + ")");
			}
			byte[] tmpVtData = new byte[tmpVtLen];
			buf.get(tmpVtData);
		}
	}

}

package org.tsitle.rtsp.security.constants;

public class MikeyOtherConstants {

	/** Common Header: version */
	public static final byte MOC_CHD_VERSION = (byte)0x01;
	/** Common Header: V==0x80 => response expected, 0x00 => no response expected */
	public static final byte MOC_CHD_V_BIT_RESP = (byte)0x80;
	/** Common Header: V==0x80 => response expected, 0x00 => no response expected */
	public static final byte MOC_CHD_V_BIT_NORESP = (byte)0x00;
	/** Common Header: PRF_FUNC==0x00 => MIKEY-1 */
	public static final byte MOC_CHD_PRF_FUNC_MIKEY1 = (byte)0x00;
	/** Common Header: CS_ID_map_type==0 => SRTP-ID */
	public static final byte MOC_CHD_CS_ID_MAP_TYPE_SRTP_ID = (byte)0x00;

	/** Payload type SP: PROT==0x00 => SRTP */
	public static final byte MOC_PT_SP_PROT_SRTP = (byte)0x00;
	/** Payload type SP: ENC_ALG==0x01 => AES-CM (AES-128-CTR) */
	public static final byte MOC_PT_SP_ENC_ALG_AESCM = (byte)0x01;
	/** Payload type SP: AUTH_ALG==0x01 => HMAC-SHA-1 */
	public static final byte MOC_PT_SP_AUTH_ALG_HMACSHA1 = (byte)0x01;
	/** Payload type SP: PRF_ALG==0x00 => AES-CM (AES-128-CTR) */
	public static final byte MOC_PT_SP_PRF_ALG_AESCM = (byte)0x00;
	/** Payload type SP: ENABLED==0x01 => True */
	public static final byte MOC_PT_SP_ENABLED = (byte)0x01;

	/** Payload type KEMAC: KEMAC_MAC_ALG==0x01 => HMAC-SHA-1-160 */
	public static final byte MOC_KEMAC_MAC_ALG_NONE = (byte)0x00;
	/** Payload type KEMAC: KEMAC_MAC_ALG==0x01 => HMAC-SHA-1-160 */
	public static final byte MOC_KEMAC_MAC_ALG_HMACSHA1160 = (byte)0x01;

}

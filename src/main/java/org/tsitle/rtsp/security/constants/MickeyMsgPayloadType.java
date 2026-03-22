package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MickeyMsgPayloadType {

	/** Last payload */
	MMPT_LAST((byte)0x00),
	/** KEMAC */
	MMPT_KEMAC((byte)0x01),
	/** PKE */
	MMPT_PKE((byte)0x02),
	/** DH */
	MMPT_DH((byte)0x03),
	/** SIGN */
	MMPT_SIGN((byte)0x04),
	/** Timestamp payload T */
	MMPT_T((byte)0x05),
	/** ID */
	MMPT_ID((byte)0x06),
	/** CERT */
	MMPT_CERT((byte)0x07),
	/** CHASH */
	MMPT_CHASH((byte)0x08),
	/** V */
	MMPT_V((byte)0x09),
	/** SP */
	MMPT_SP((byte)0x0A),
	/** RAND */
	MMPT_RAND((byte)0x0B),
	/** ERR */
	MMPT_ERR((byte)0x0C),
	/** Key data */
	MMPT_KEY_DATA((byte)20),
	/** General Extension */
	MMPT_GENERAL_EXT((byte)21),
	/** Unknown */
	MMPT_UNKNOWN((byte)0xFF);

	private final byte value;
	MickeyMsgPayloadType(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MickeyMsgPayloadType of(byte value) {
		for (MickeyMsgPayloadType type : MickeyMsgPayloadType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMPT_UNKNOWN;
	}

}

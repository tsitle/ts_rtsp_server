package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MikeyMsgKemacPayloadType {

	/** TGK (TEK Generation Key) */
	MMKEMPT_TGK_ONLY((byte)0x00),
	/** TGK+SALT (TEK Generation Key) */
	MMKEMPT_TGK_SALT((byte)0x01),
	/** TEK (Traffic Encryption Key) */
	MMKEMPT_TEK_ONLY((byte)0x02),
	/** TEK+SALT (Traffic Encryption Key) */
	MMKEMPT_TEK_SALT((byte)0x03),
	/** Unknown */
	MMKEMPT_UNKNOWN((byte)0xFF);

	private final byte value;
	MikeyMsgKemacPayloadType(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MikeyMsgKemacPayloadType of(byte value) {
		for (MikeyMsgKemacPayloadType type : MikeyMsgKemacPayloadType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMKEMPT_UNKNOWN;
	}

}

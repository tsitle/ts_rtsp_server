package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MickeyMsgKemacPayloadType {

	/** TGK */
	MMKEMPT_TGK_ONLY((byte)0x00),
	/** TGK+SALT */
	MMKEMPT_TGK_SALT((byte)0x01),
	/** TEK */
	MMKEMPT_TEK_ONLY((byte)0x02),
	/** TEK+SALT */
	MMKEMPT_TEK_SALT((byte)0x03),
	/** Unknown */
	MMKEMPT_UNKNOWN((byte)0xFF);

	private final byte value;
	MickeyMsgKemacPayloadType(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MickeyMsgKemacPayloadType of(byte value) {
		for (MickeyMsgKemacPayloadType type : MickeyMsgKemacPayloadType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMKEMPT_UNKNOWN;
	}

}

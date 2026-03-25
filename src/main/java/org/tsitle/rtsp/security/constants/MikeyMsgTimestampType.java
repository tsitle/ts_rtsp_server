package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MikeyMsgTimestampType {

	/** NTP-UTC, 64-bits timestamp value */
	MMTST_NTP_UTC((byte)0x00),
	/** NTP, 64-bits timestamp value */
	MMTST_NTP_DEF((byte)0x01),
	/** COUNTER, 32-bits timestamp value */
	MMTST_COUNTER((byte)0x02),
	/** Unknown */
	MMTST_UNKNOWN((byte)0xFF);

	private final byte value;
	MikeyMsgTimestampType(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MikeyMsgTimestampType of(byte value) {
		for (MikeyMsgTimestampType type : MikeyMsgTimestampType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMTST_UNKNOWN;
	}

}

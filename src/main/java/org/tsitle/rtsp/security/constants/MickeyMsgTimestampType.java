package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MickeyMsgTimestampType {

	/** NTP-UTC, 64-bits timestamp value */
	MMTST_NTP_UTC((byte)0x00),
	/** NTP, 64-bits timestamp value */
	MMTST_NTP_DEF((byte)0x01),
	/** COUNTER, 32-bits timestamp value */
	MMTST_COUNTER((byte)0x02),
	/** Unknown */
	MMTST_UNKNOWN((byte)0xFF);

	private final byte value;
	MickeyMsgTimestampType(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MickeyMsgTimestampType of(byte value) {
		for (MickeyMsgTimestampType type : MickeyMsgTimestampType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMTST_UNKNOWN;
	}

}

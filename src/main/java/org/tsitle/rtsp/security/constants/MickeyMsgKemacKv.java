package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MickeyMsgKemacKv {

	/** Null */
	MMKEMKV_NULL((byte)0x00),
	/** The key is associated with the SPI/MKI */
	MMKEMKV_SPI((byte)0x01),
	/** The key has a start and expiration time (e.g., an SRTP TEK) */
	MMKEMKV_INTV((byte)0x02),
	/** Unknown */
	MMKEMKV_UNKNOWN((byte)0xFF);

	private final byte value;
	MickeyMsgKemacKv(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MickeyMsgKemacKv of(byte value) {
		for (MickeyMsgKemacKv type : MickeyMsgKemacKv.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMKEMKV_UNKNOWN;
	}

}

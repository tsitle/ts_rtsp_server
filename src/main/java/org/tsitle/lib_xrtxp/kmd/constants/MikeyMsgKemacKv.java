package org.tsitle.lib_xrtxp.kmd.constants;

import org.jspecify.annotations.NonNull;

public enum MikeyMsgKemacKv {

	/** Null */
	MMKEMKV_NULL((byte)0x00),
	/** The key is associated with the SPI/MKI */
	MMKEMKV_SPI_OR_MKI((byte)0x01),
	/** The key has a start and expiration time (e.g., an SRTP TEK) */
	MMKEMKV_INTV((byte)0x02),
	/** Unknown */
	MMKEMKV_UNKNOWN((byte)0xFF);

	private final byte value;
	MikeyMsgKemacKv(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MikeyMsgKemacKv of(byte value) {
		for (MikeyMsgKemacKv type : MikeyMsgKemacKv.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMKEMKV_UNKNOWN;
	}

}

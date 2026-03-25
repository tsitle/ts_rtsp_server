package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MikeyMsgKemacEncrAlg {

	/** NULL */
	MMEA_NULL((byte)0x00),
	/** AES-CM-128 */
	MMEA_AESCM128((byte)0x01),
	/** AES Key Wrap using a 128-bit key */
	MMEA_AESKW128((byte)0x02),
	/** Unknown */
	MMEA_UNKNOWN((byte)0xFF);

	private final byte value;
	MikeyMsgKemacEncrAlg(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MikeyMsgKemacEncrAlg of(byte value) {
		for (MikeyMsgKemacEncrAlg type : MikeyMsgKemacEncrAlg.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMEA_UNKNOWN;
	}

}

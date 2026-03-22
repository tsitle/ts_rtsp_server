package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MickeyMsgEncrAlg {

	/** NULL */
	MMEA_NULL((byte)0x00),
	/** AES-CM-128 */
	MMEA_AESCM128((byte)0x01),
	/** AES-KW-128 */
	MMEA_AESKW128((byte)0x02),
	/** Unknown */
	MMEA_UNKNOWN((byte)0xFF);

	private final byte value;
	MickeyMsgEncrAlg(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MickeyMsgEncrAlg of(byte value) {
		for (MickeyMsgEncrAlg type : MickeyMsgEncrAlg.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMEA_UNKNOWN;
	}

}

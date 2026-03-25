package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum MikeyMsgDataType {

	/** Initiator's pre-shared key message */
	MMDT_PRE_SHARED_KEY((byte)0x00),
	/** Verification message of a Pre-shared key message */
	MMDT_PSKEY_VERIFICATION((byte)0x01),
	/** Initiator's public-key transport message */
	MMDT_PUBLIC_KEY((byte)0x02),
	/** Verification message of a public-key message */
	MMDT_PK_VERIFICATION((byte)0x03),
	/** Initiator's DH exchange message */
	MMDT_DH_INIT((byte)0x04),
	/** Responder's DH exchange message */
	MMDT_DH_RESPONSE((byte)0x05),
	/** Error message */
	MMDT_ERROR((byte)0x06),
	/** Unknown */
	MMDT_UNKNOWN((byte)0xFF);

	private final byte value;
	MikeyMsgDataType(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MikeyMsgDataType of(byte value) {
		for (MikeyMsgDataType type : MikeyMsgDataType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMDT_UNKNOWN;
	}

}

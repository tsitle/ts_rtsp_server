package org.tsitle.rtsp.security.constants;

import org.jspecify.annotations.NonNull;

public enum PrfDeriveLabel {

	/** RTP: Encryption Key */
	PDL_RTP_ENC((byte)0x00),
	/** RTP: Authentication Key */
	PDL_RTP_AUTH((byte)0x01),
	/** RTP: Salt Key */
	PDL_RTP_SALT((byte)0x02),
	/** RTCP: Encryption Key */
	PDL_RTCP_ENC((byte)0x03),
	/** RTCP: Authentication Key */
	PDL_RTCP_AUTH((byte)0x04),
	/** RTCP: Salt Key */
	PDL_RTCP_SALT((byte)0x05),
	/** Unknown */
	PDL_UNKNOWN((byte)0xFF);

	private final byte value;
	PrfDeriveLabel(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull PrfDeriveLabel of(byte value) {
		for (PrfDeriveLabel type : PrfDeriveLabel.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return PDL_UNKNOWN;
	}

}

package org.tsitle.lib_xrtxp.kmd.constants;

import org.jspecify.annotations.NonNull;

public enum MikeyMsgSecPolicyParamType {

	/** Encryption algorithm */
	MMSPPT_ENCALG((byte)0x00),
	/** Session Encr. key length */
	MMSPPT_SEKL((byte)0x01),
	/** Authentication algorithm */
	MMSPPT_AUTHALG((byte)0x02),
	/** Session Auth. key length */
	MMSPPT_SAKL((byte)0x03),
	/** Session Salt key length */
	MMSPPT_SSKL((byte)0x04),
	/** SRTP Pseudo Random Function */
	MMSPPT_SPRF((byte)0x05),
	/** Key derivation rate */
	MMSPPT_KDR((byte)0x06),
	/** SRTP encryption off/on */
	MMSPPT_SRTPENCEN((byte)0x07),
	/** SRTCP encryption off/on */
	MMSPPT_SRTCPENCEN((byte)0x08),
	/** Sender's FEC order */
	MMSPPT_SFECO((byte)0x09),
	/** SRTP authentication off/on */
	MMSPPT_SRTPAUTHEN((byte)0x0A),
	/** Authentication tag length */
	MMSPPT_AUTHTAGLENGTH((byte)0x0B),
	/** SRTP prefix length */
	MMSPPT_SRTPREFIXLEN((byte)0x0C),
	/** Unknown */
	MMSPPT_UNKNOWN((byte)0xFF);

	private final byte value;
	MikeyMsgSecPolicyParamType(byte value) {
		this.value = value;
	}
	public byte getValue() {
		return value;
	}
	public static @NonNull MikeyMsgSecPolicyParamType of(byte value) {
		for (MikeyMsgSecPolicyParamType type : MikeyMsgSecPolicyParamType.values()) {
			if (type.getValue() == value) {
				return type;
			}
		}
		return MMSPPT_UNKNOWN;
	}

}

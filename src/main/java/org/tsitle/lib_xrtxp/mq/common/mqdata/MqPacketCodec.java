package org.tsitle.lib_xrtxp.mq.common.mqdata;

import org.jspecify.annotations.NonNull;

/**
 * Codec used for the payload of A/V packets.
 */
public enum MqPacketCodec {

	/*
	 * Same codecs as in ConfigSsCodec.
	 */

	AACLC("AACLC"),
	PCMU("PCMU"),
	LPCM08U("LPCM08U"),
	LPCM16S("LPCM16S"),

	MJPEG("MJPEG"),
	H264("H264"),
	H265("H265");

	private final @NonNull String codecName;

	MqPacketCodec(@NonNull String codecName) { this.codecName = codecName; }

	public @NonNull String getCodecName() { return codecName; }

	public static @NonNull MqPacketCodec of(@NonNull String codecName) {
		for (MqPacketCodec codec : MqPacketCodec.values()) {
			if (codec.codecName.equalsIgnoreCase(codecName)) {
				return codec;
			}
		}
		throw new IllegalArgumentException("Unknown codec name: '" + codecName + "'");
	}

	public boolean isVideo() { return (this == MJPEG || this == H264 || this == H265); }

}

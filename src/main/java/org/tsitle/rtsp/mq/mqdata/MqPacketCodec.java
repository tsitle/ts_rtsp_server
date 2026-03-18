package org.tsitle.rtsp.mq.mqdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;

/**
 * Codec used for the payload of A/V packets.
 */
public enum MqPacketCodec {

	H264("H264"),
	H265("H265"),
	LPCM16_8K_MONO("LPCM16/8000/1");

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

	public boolean isVideo() { return (this == H264 || this == H265); }

	public @NonNull RtpPacketType getRtpPacketType() {
		return switch (this) {
				case H264 -> RtpPacketType.V_H264;
				case H265 -> RtpPacketType.V_H265;
				case LPCM16_8K_MONO -> RtpPacketType.A_LINEAR_PCM_S16_VAR;
				//noinspection UnnecessaryDefault
				default -> throw new IllegalStateException("Cannot map codec: " + this);
			};
	}

}

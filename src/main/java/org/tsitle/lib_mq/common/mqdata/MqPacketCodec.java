package org.tsitle.lib_mq.common.mqdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * Codec used for the payload of A/V packets.
 */
public enum MqPacketCodec {

	AACLC("AACLC"),
	AC3("AC3"),
	MP2("MP2"),
	MP3("MP3"),
	OPUS("OPUS"),
	PCMA("PCMA"),
	PCMU("PCMU"),
	LPCM08U("LPCM08U"),
	LPCM16S("LPCM16S"),

	H264("H264"),
	H265("H265"),
	MJPEG("MJPEG"),
	VP8("VP8");

	private final @NonNull String codecName;

	MqPacketCodec(@NonNull String codecName) { this.codecName = codecName; }

	public @NonNull String getCodecName() { return codecName; }

	public static @NonNull MqPacketCodec of(@NonNull String codecName) {
		for (MqPacketCodec codec : MqPacketCodec.values()) {
			if (codec.codecName.equalsIgnoreCase(codecName)) {
				return codec;
			}
		}
		throw new IllegalArgumentException(MqPacketCodec.class.getSimpleName() + ".of(): " +
				"Unknown codec name: '" + codecName + "'");
	}

	public boolean isVideo() { return (this == H264 || this == H265 || this == MJPEG || this == VP8); }

	public boolean isAudio() {
		return (isPcmAudio() || this == AACLC || this == AC3 || this == MP2 || this == MP3 || this == OPUS);
	}

	public boolean isPcmAudio() { return (this == PCMA || this == PCMU || this == LPCM08U || this == LPCM16S); }

	public @NonNull RtpPacketType convertToRtpPacketType(
				@NonNull SampleRateEnum audioSamplerate,
				byte audioChannelCount
			) {
		return switch (this) {
				case AACLC -> RtpPacketType.A_AAC;
				case AC3 -> RtpPacketType.A_AC3;
				case MP2, MP3 -> RtpPacketType.A_MPEG;
				case OPUS -> RtpPacketType.A_OPUS;
				case PCMA -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_008000) {
							yield RtpPacketType.A_PCMA_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMA_VAR;
					}
				case PCMU -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_008000) {
							yield RtpPacketType.A_PCMU_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMU_VAR;
					}
				case LPCM08U -> RtpPacketType.A_LINEAR_PCM_U08_VAR;
				case LPCM16S -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_044100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_MONO;
						}
						if (audioChannelCount == 2 && audioSamplerate == SampleRateEnum.SR_044100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_STEREO;
						}
						yield RtpPacketType.A_LINEAR_PCM_S16_VAR;
					}
				case H264 -> RtpPacketType.V_H264;
				case H265 -> RtpPacketType.V_H265;
				case MJPEG -> RtpPacketType.V_MJPEG;
				case VP8 -> RtpPacketType.V_VP8;
			};
	}

}

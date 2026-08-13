package org.tsitle.rtsp_server.config;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

public enum ConfigEsCodec {

	AACLC,
	AC3,
	OPUS,
	PCMA,
	PCMU,
	LPCM08U,
	LPCM16S,

	H264,
	H265,
	MJPEG,
	VP8;

	public @NonNull RtpPacketType convertToRtpPacketType(
				@NonNull SampleRateEnum audioSamplerate,
				byte audioChannelCount
			) {
		return switch (this) {
				case AACLC -> RtpPacketType.A_AAC;
				case AC3 -> RtpPacketType.A_AC3;
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

package org.tsitle.rtsp.config;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;

public enum ConfigSsCodec {

	AACLC,
	PCMU,
	LPCM08U,
	LPCM16S,

	MJPEG,
	H264,
	H265;

	public @NonNull RtpPacketType convertToRtpPacketType(int audioSamplerateHz, byte audioChannelCount) {
		return switch (this) {
				case AACLC -> RtpPacketType.A_AAC;
				case PCMU -> {
						if (audioChannelCount == 1 && audioSamplerateHz == 8000) {
							yield RtpPacketType.A_PCMU_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMU_VAR;
					}
				case LPCM08U -> RtpPacketType.A_LINEAR_PCM_U08_VAR;
				case LPCM16S -> {
						if (audioChannelCount == 1 && audioSamplerateHz == 44100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_MONO;
						}
						if (audioChannelCount == 2 && audioSamplerateHz == 44100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_STEREO;
						}
						yield RtpPacketType.A_LINEAR_PCM_S16_VAR;
					}
				case MJPEG -> RtpPacketType.V_JPEG;
				case H264 -> RtpPacketType.V_H264;
				case H265 -> RtpPacketType.V_H265;
			};
	}

}

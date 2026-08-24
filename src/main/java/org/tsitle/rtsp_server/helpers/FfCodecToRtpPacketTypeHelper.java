package org.tsitle.rtsp_server.helpers;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

import java.util.Optional;

public final class FfCodecToRtpPacketTypeHelper {

	private FfCodecToRtpPacketTypeHelper() { }

	public static Optional<RtpPacketType> convertFfmpegVideoCodecToRtpPacketType(@NonNull FfmpegCodec ffmpegCodec) {
		RtpPacketType resEn = switch (ffmpegCodec) {
				case V_H264 -> RtpPacketType.V_H264;
				case V_H265 -> RtpPacketType.V_H265;
				case V_MJPEG -> RtpPacketType.V_MJPEG;
				case V_VP8 -> RtpPacketType.V_VP8;
				default -> null;
			};
		return Optional.ofNullable(resEn);
	}

	public static Optional<RtpPacketType> convertFfmpegAudioCodecToRtpPacketType(
				@NonNull FfmpegCodec ffmpegCodec,
				@NonNull SampleRateEnum audioSamplerate,
				byte audioChannelCount
			) {
		RtpPacketType resEn = switch (ffmpegCodec) {
				case A_AAC -> RtpPacketType.A_AAC;
				case A_AC3 -> RtpPacketType.A_AC3;
				case A_MP2, A_MP3 -> RtpPacketType.A_MPEG;
				case A_OPUS -> RtpPacketType.A_OPUS;
				case A_PCM_ALAW -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_008000) {
							yield RtpPacketType.A_PCMA_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMA_VAR;
					}
				case A_PCM_MULAW -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_008000) {
							yield RtpPacketType.A_PCMU_8KHZ_MONO;
						}
						yield RtpPacketType.A_PCMU_VAR;
					}
				case A_PCM_U8 -> RtpPacketType.A_LINEAR_PCM_U08_VAR;
				case A_PCM_S16BE, A_PCM_S16LE -> {
						if (audioChannelCount == 1 && audioSamplerate == SampleRateEnum.SR_044100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_MONO;
						}
						if (audioChannelCount == 2 && audioSamplerate == SampleRateEnum.SR_044100) {
							yield RtpPacketType.A_LINEAR_PCM_S16_441K_STEREO;
						}
						yield RtpPacketType.A_LINEAR_PCM_S16_VAR;
					}
				default -> null;
			};
		return Optional.ofNullable(resEn);
	}

}

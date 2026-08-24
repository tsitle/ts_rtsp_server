package org.tsitle.rtsp_server.helpers;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_mq.common.mqdata.MqPacketCodec;

import java.util.Optional;

public final class FfCodecToMqCodecHelper {

	private FfCodecToMqCodecHelper() { }

	public static Optional<MqPacketCodec> convertFfToMqCodec(@NonNull FfmpegCodec ffCodec) {
		MqPacketCodec resEn = switch (ffCodec) {
				case A_AAC -> MqPacketCodec.AACLC;
				case A_AC3 -> MqPacketCodec.AC3;
				case A_MP2 -> MqPacketCodec.MP2;
				case A_MP3 -> MqPacketCodec.MP3;
				case A_OPUS -> MqPacketCodec.OPUS;
				case A_PCM_ALAW -> MqPacketCodec.PCMA;
				case A_PCM_MULAW -> MqPacketCodec.PCMU;
				case A_PCM_U8 -> MqPacketCodec.LPCM08U;
				case A_PCM_S16BE -> MqPacketCodec.LPCM16S;
				//
				case V_H264 -> MqPacketCodec.H264;
				case V_H265 -> MqPacketCodec.H265;
				case V_MJPEG -> MqPacketCodec.MJPEG;
				case V_VP8 -> MqPacketCodec.VP8;
				default -> null;
			};
		return Optional.ofNullable(resEn);
	}

}

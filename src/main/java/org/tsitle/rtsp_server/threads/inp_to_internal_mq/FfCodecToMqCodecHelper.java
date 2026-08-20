package org.tsitle.rtsp_server.threads.inp_to_internal_mq;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_mq.common.mqdata.MqPacketCodec;

import java.util.Optional;

final class FfCodecToMqCodecHelper {

	private FfCodecToMqCodecHelper() { }

	static Optional<MqPacketCodec> convertFfToMqCodec(@NonNull FfmpegCodec ffCodec) {
		return switch (ffCodec) {
				case A_AAC -> Optional.of(MqPacketCodec.AACLC);
				case A_AC3 -> Optional.of(MqPacketCodec.AC3);
				case A_MP2 -> Optional.of(MqPacketCodec.MP2);
				case A_MP3 -> Optional.of(MqPacketCodec.MP3);
				case A_OPUS -> Optional.of(MqPacketCodec.OPUS);
				case A_PCM_ALAW -> Optional.of(MqPacketCodec.PCMA);
				case A_PCM_MULAW -> Optional.of(MqPacketCodec.PCMU);
				case A_PCM_U8 -> Optional.of(MqPacketCodec.LPCM08U);
				case A_PCM_S16BE -> Optional.of(MqPacketCodec.LPCM16S);
				//
				case V_H264 -> Optional.of(MqPacketCodec.H264);
				case V_H265 -> Optional.of(MqPacketCodec.H265);
				case V_MJPEG -> Optional.of(MqPacketCodec.MJPEG);
				case V_VP8 -> Optional.of(MqPacketCodec.VP8);
				default -> Optional.empty();
			};
	}

}

package org.tsitle.rtsp_server.helpers;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.rtsp_server.config.ConfigTcCodec;

public final class CfgTcCodecToFfCodecHelper {

	private CfgTcCodecToFfCodecHelper() { }

	public static @NonNull FfmpegCodec convertCfgTcCodecToFfmpegCodec(@NonNull String cfgTcCodecStr) {
		try {
			return convertCfgTcCodecToFfmpegCodec(
					ConfigTcCodec.valueOf(cfgTcCodecStr)
				);
		} catch (IllegalArgumentException e) {
			return FfmpegCodec.UNKNOWN;
		}
	}

	public static @NonNull FfmpegCodec convertCfgTcCodecToFfmpegCodec(@NonNull ConfigTcCodec cfgTcCodec) {
		return switch (cfgTcCodec) {
				case AACLC -> FfmpegCodec.A_AAC;
				case AC3 -> FfmpegCodec.A_AC3;
				case MP2 -> FfmpegCodec.A_MP2;
				case MP3 -> FfmpegCodec.A_MP3;
				case OPUS -> FfmpegCodec.A_OPUS;
				case PCMA -> FfmpegCodec.A_PCM_ALAW;
				case PCMU -> FfmpegCodec.A_PCM_MULAW;
				case LPCM16S -> FfmpegCodec.A_PCM_S16BE;

				case H264 -> FfmpegCodec.V_H264;
				case H265 -> FfmpegCodec.V_H265;
				case MJPEG -> FfmpegCodec.V_MJPEG;
				case VP8 -> FfmpegCodec.V_VP8;
			};
	}

}

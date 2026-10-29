package org.tsitle.lib_dataprov;

import org.jspecify.annotations.NonNull;
import io.github.tsitle.lib_ffmpeg.FfmpegCodec;
import io.github.tsitle.lib_xrtxp.avdata.codec_a_pcm.AudioPcmInfo;

import java.util.HashSet;
import java.util.Set;

public final class DpConstants {

	private DpConstants() { }

	/** Maximum channel count for audio */
	public static final int DP_PCM_AUDIO_CHANNELS_MAX = AudioPcmInfo.AUDIO_CHANNELS_MAX;
	/** Maximum channel count for Opus audio */
	public static final int DP_OPUS_AUDIO_CHANNELS_MAX = 2;

	/** Allowed FFmpeg video codecs for RTP */
	public static final @NonNull Set<@NonNull FfmpegCodec> DP_FFMPEG_ALLOWED_CODECS_VIDEO = new HashSet<>() {{
			add(FfmpegCodec.V_H264);
			add(FfmpegCodec.V_H265);
			add(FfmpegCodec.V_MJPEG);
			add(FfmpegCodec.V_VP8);
		}};
	/** Allowed FFmpeg audio codecs for RTP */
	public static final @NonNull Set<@NonNull FfmpegCodec> DP_FFMPEG_ALLOWED_CODECS_AUDIO = new HashSet<>() {{
			add(FfmpegCodec.A_AAC);
			add(FfmpegCodec.A_AC3);
			add(FfmpegCodec.A_MP2);
			add(FfmpegCodec.A_MP3);
			add(FfmpegCodec.A_OPUS);
			add(FfmpegCodec.A_PCM_ALAW);
			add(FfmpegCodec.A_PCM_MULAW);
			add(FfmpegCodec.A_PCM_S16BE);
			add(FfmpegCodec.A_PCM_S16LE);
			add(FfmpegCodec.A_PCM_U8);
		}};

}

package org.tsitle.lib_dataprov;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.codec_a_pcm.AudioPcmInfo;

import java.util.HashSet;
import java.util.Set;

public final class DpConstants {

	private DpConstants() { }

	/** Samples per frame for AAC-LC audio -- Default Type 1 (most common) */
	public static final int DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1 = 1024;
	/** Samples per frame for AAC-LC audio -- Default Type 2 */
	public static final int DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2 = 960;
	/**
	 * Samples per frame for AAC-LC audio -- Low Delay<br />
	 * See note in {@link AudioAacInfo}
	 */
	public static final int DP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD = 512;
	/** Samples per frame for AC-3 audio */
	public static final int DP_SAMPLES_PER_FRAME_AC3_AUDIO = 1536;
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

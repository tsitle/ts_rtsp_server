package org.tsitle.rtsp_server.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.codec_a_pcm.AudioPcmInfo;

import java.util.HashSet;
import java.util.Set;

public final class RtpConstants {

	private RtpConstants() { }

	/** Interval for sending PCM audio samples that were read from a file (in milliseconds) */
	public static final int RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS = 20;
	/** Samples per frame for AAC-LC audio -- Default Type 1 (most common) */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1 = 1024;
	/** Samples per frame for AAC-LC audio -- Default Type 2 */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2 = 960;
	/**
	 * Samples per frame for AAC-LC audio -- Low Delay<br />
	 * See note in {@link AudioAacInfo}
	 */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD = 512;
	/** Samples per frame for AC-3 audio */
	public static final int RTP_SAMPLES_PER_FRAME_AC3_AUDIO = 1536;
	/** Maximum channel count for audio */
	public static final int RTP_AUDIO_CHANNELS_MAX = AudioPcmInfo.AUDIO_CHANNELS_MAX;

	/** Interval for re-keying the SRTxP master key and salt (in packets) as integer */
	public static final long SRTXP_REKEYING_INTERVAL_PACKETS_INT = 1048576;  // ^=2^20

	/** Allowed FFmpeg video codecs for RTP */
	public static final @NonNull Set<@NonNull FfmpegCodec> RTP_FFMPEG_ALLOWED_CODECS_VIDEO = new HashSet<>() {{
			add(FfmpegCodec.V_H264);
			add(FfmpegCodec.V_H265);
			add(FfmpegCodec.V_MJPEG);
			add(FfmpegCodec.V_VP8);
		}};
	/** Allowed FFmpeg audio codecs for RTP */
	public static final @NonNull Set<@NonNull FfmpegCodec> RTP_FFMPEG_ALLOWED_CODECS_AUDIO = new HashSet<>() {{
			add(FfmpegCodec.A_AAC);
			add(FfmpegCodec.A_AC3);
			add(FfmpegCodec.A_OPUS);
			add(FfmpegCodec.A_PCM_ALAW);
			add(FfmpegCodec.A_PCM_MULAW);
			add(FfmpegCodec.A_PCM_S16BE);
			add(FfmpegCodec.A_PCM_S16LE);
			add(FfmpegCodec.A_PCM_U8);
		}};

}

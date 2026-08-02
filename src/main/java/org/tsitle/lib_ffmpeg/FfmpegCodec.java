package org.tsitle.lib_ffmpeg;

import org.bytedeco.ffmpeg.global.avcodec;
import org.jspecify.annotations.NonNull;

/**
 * FFmpeg codecs.
 */
public enum FfmpegCodec {

	UNKNOWN(-1),

	A_AAC(avcodec.AV_CODEC_ID_AAC),
	A_AC3(avcodec.AV_CODEC_ID_AC3),
	A_ALAC(avcodec.AV_CODEC_ID_ALAC),
	A_EAC3(avcodec.AV_CODEC_ID_EAC3),
	A_FLAC(avcodec.AV_CODEC_ID_FLAC),
	A_MP3(avcodec.AV_CODEC_ID_MP3),
	A_OPUS(avcodec.AV_CODEC_ID_OPUS),
	A_PCM_ALAW(avcodec.AV_CODEC_ID_PCM_ALAW),
	A_PCM_F32BE(avcodec.AV_CODEC_ID_PCM_F32BE),
	A_PCM_F32LE(avcodec.AV_CODEC_ID_PCM_F32LE),
	A_PCM_MULAW(avcodec.AV_CODEC_ID_PCM_MULAW),
	A_PCM_S16BE(avcodec.AV_CODEC_ID_PCM_S16BE),
	A_PCM_S16LE(avcodec.AV_CODEC_ID_PCM_S16LE),
	A_PCM_S24BE(avcodec.AV_CODEC_ID_PCM_S24BE),
	A_PCM_S24LE(avcodec.AV_CODEC_ID_PCM_S24LE),
	A_PCM_S32BE(avcodec.AV_CODEC_ID_PCM_S32BE),
	A_PCM_S32LE(avcodec.AV_CODEC_ID_PCM_S32LE),
	A_PCM_U8(avcodec.AV_CODEC_ID_PCM_U8),
	A_VORBIS(avcodec.AV_CODEC_ID_VORBIS),

	V_AV1(avcodec.AV_CODEC_ID_AV1),
	V_H264(avcodec.AV_CODEC_ID_H264),
	V_H265(avcodec.AV_CODEC_ID_H265),
	V_MJPEG(avcodec.AV_CODEC_ID_MJPEG),
	V_MPEG2(avcodec.AV_CODEC_ID_MPEG2VIDEO),
	V_MPEG4(avcodec.AV_CODEC_ID_MPEG4),
	V_THEORA(avcodec.AV_CODEC_ID_THEORA),
	V_VP8(avcodec.AV_CODEC_ID_VP8),
	V_VP9(avcodec.AV_CODEC_ID_VP9);

	private final int ffmpegId;

	FfmpegCodec(int ffmpegId) {
		this.ffmpegId = ffmpegId;
	}

	public int getFfmpegId() {
		if (this == UNKNOWN) {
			throw new IllegalStateException(FfmpegCodec.class.getSimpleName() + ".getFfmpegId(): " +
					"Invalid codec UNKNOWN");
		}
		return ffmpegId;
	}

	public static @NonNull FfmpegCodec of(int value) {
		for (FfmpegCodec tmpCod : FfmpegCodec.values()) {
			if (tmpCod != UNKNOWN && tmpCod.getFfmpegId() == value) {
				return tmpCod;
			}
		}
		return UNKNOWN;
	}

	public boolean isVideo() {
		return switch(this) {
				case
						V_AV1,
						V_H264,
						V_H265,
						V_MJPEG,
						V_MPEG2,
						V_MPEG4,
						V_THEORA,
						V_VP8,
						V_VP9
					-> true;
				default -> false;
			};
	}

	public boolean isPcmAudio() {
		return switch(this) {
				case
						A_PCM_ALAW,
						A_PCM_F32BE,
						A_PCM_F32LE,
						A_PCM_MULAW,
						A_PCM_S16BE,
						A_PCM_S16LE,
						A_PCM_S24BE,
						A_PCM_S24LE,
						A_PCM_S32BE,
						A_PCM_S32LE,
						A_PCM_U8
					-> true;
				default -> false;
			};
	}

	public boolean isAudio() {
		return (isPcmAudio() ||
				this == A_AAC || this == A_AC3 || this == A_ALAC ||
				this == A_EAC3 || this == A_FLAC || this == A_MP3 ||
				this == A_OPUS || this == A_VORBIS);
	}

}

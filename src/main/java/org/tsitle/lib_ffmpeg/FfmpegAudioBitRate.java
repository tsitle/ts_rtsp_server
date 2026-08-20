package org.tsitle.lib_ffmpeg;

import org.jspecify.annotations.NonNull;

/**
 * Audio bit rates in kbps.
 */
public enum FfmpegAudioBitRate {

	UNKNOWN(-1),

	ABR_008(8),
	ABR_016(16),
	ABR_024(24),
	ABR_032(32),
	ABR_040(40),
	ABR_048(48),
	ABR_056(56),
	ABR_064(64),
	ABR_080(80),
	ABR_096(96),
	ABR_112(112),
	ABR_128(128),
	ABR_144(144),
	ABR_160(160),
	ABR_192(192),
	ABR_224(224),
	ABR_256(256),
	ABR_288(288),
	ABR_320(320),
	ABR_352(352),
	ABR_384(384),
	ABR_416(416),
	ABR_448(448);

	private final int brInt;

	FfmpegAudioBitRate(int brInt) {
		this.brInt = brInt;
	}

	public int getBrInt() {
		return brInt;
	}

	public static @NonNull FfmpegAudioBitRate of(double value) {
		for (FfmpegAudioBitRate tmpEn : FfmpegAudioBitRate.values()) {
			if (tmpEn != UNKNOWN && tmpEn.getBrInt() == value) {
				return tmpEn;
			}
		}
		return UNKNOWN;
	}

}

package org.tsitle.lib_ffmpeg.tc;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegPixelFmt;
import org.tsitle.lib_xrtxp.common.types.ImageDimensions;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;

/**
 * Parameters for Video Transcoder Input.
 */
public final class FfmpegTcParamsInpVideo extends FfmpegTcParamsInpBase {

	public @NonNull RationalNumber frameRate = RationalNumber.ofEmpty();
	public @NonNull ImageDimensions imgDims = ImageDimensions.ofEmpty();
	public @NonNull FfmpegPixelFmt pixelFmt = FfmpegPixelFmt.UNKNOWN;

	public FfmpegTcParamsInpVideo() { }

	public void copyFrom(@NonNull FfmpegTcParamsInpVideo other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		frameRate.copyFrom(other.frameRate);
		imgDims = ImageDimensions.of(other.imgDims);
		pixelFmt = other.pixelFmt;
	}

}

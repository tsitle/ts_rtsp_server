package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegPixelFmt;
import org.tsitle.lib_xrtxp.common.types.ImageDimensions;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;

public final class FfmpegDmxSubStreamInfoVideo extends FfmpegDmxSubStreamInfoBase {

	public int streamNumberVideo;
	public @NonNull RationalNumber fps;
	public @NonNull ImageDimensions imgDims;
	public @NonNull FfmpegPixelFmt pixelFmt;

	public FfmpegDmxSubStreamInfoVideo() {
		clear();
	}

	public void clear() {
		baseClear();

		streamNumberVideo = -1;
		fps = RationalNumber.ofEmpty();
		imgDims = ImageDimensions.ofEmpty();
		pixelFmt = FfmpegPixelFmt.UNKNOWN;
	}

	public void copyFrom(@NonNull FfmpegDmxSubStreamInfoVideo other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		streamNumberVideo = other.streamNumberVideo;
		fps = RationalNumber.of(other.fps);
		imgDims = ImageDimensions.of(other.imgDims);
		pixelFmt = other.pixelFmt;
	}

}

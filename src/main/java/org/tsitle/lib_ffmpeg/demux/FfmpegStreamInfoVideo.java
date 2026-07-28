package org.tsitle.lib_ffmpeg.demux;

import org.tsitle.lib_xrtxp.common.types.ImageDimensions;
import org.tsitle.lib_xrtxp.common.types.RationalNumber;
import org.jspecify.annotations.NonNull;

public final class FfmpegStreamInfoVideo extends FfmpegStreamInfoBase {

	public int streamNumberVideo;
	public @NonNull RationalNumber fps;
	public @NonNull ImageDimensions imgDims;

	public FfmpegStreamInfoVideo() {
		clear();
	}

	public void clear() {
		baseClear();

		streamNumberVideo = -1;
		fps = RationalNumber.ofEmpty();
		imgDims = ImageDimensions.ofEmpty();
	}

	public void copyFrom(@NonNull FfmpegStreamInfoVideo other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		streamNumberVideo = other.streamNumberVideo;
		fps = RationalNumber.of(other.fps);
		imgDims = ImageDimensions.of(other.imgDims);
	}

}

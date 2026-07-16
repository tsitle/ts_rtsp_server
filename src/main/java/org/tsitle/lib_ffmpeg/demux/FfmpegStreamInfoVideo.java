package org.tsitle.lib_ffmpeg.demux;

import org.tsitle.lib_xrtxp.common.helpers.ImageDimensions;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.jspecify.annotations.NonNull;

public final class FfmpegStreamInfoVideo extends FfmpegStreamInfoBase {

	public @NonNull RationalNumber fps = RationalNumber.ofEmpty();
	public @NonNull ImageDimensions imgDims = ImageDimensions.ofEmpty();

	public void reset() {
		baseReset();

		fps = RationalNumber.ofEmpty();
		imgDims = ImageDimensions.ofEmpty();
	}

	public void copyFrom(@NonNull FfmpegStreamInfoVideo other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		fps = RationalNumber.of(other.fps);
		imgDims = ImageDimensions.of(other.imgDims);
	}

}

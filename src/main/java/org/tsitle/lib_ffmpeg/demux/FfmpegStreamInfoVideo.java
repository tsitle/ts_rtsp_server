package org.tsitle.lib_ffmpeg.demux;

import org.tsitle.lib_xrtxp.common.helpers.ImageDimensions;
import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.jspecify.annotations.NonNull;

public final class FfmpegStreamInfoVideo extends FfmpegStreamInfoBase {

	public int streamNumberVideo;
	public @NonNull RationalNumber fps;
	public @NonNull ImageDimensions imgDims;
	public @NonNull String extradataHex;

	public FfmpegStreamInfoVideo() {
		reset();
	}

	public void reset() {
		baseReset();

		streamNumberVideo = -1;
		fps = RationalNumber.ofEmpty();
		imgDims = ImageDimensions.ofEmpty();
		extradataHex = "";
	}

	public void copyFrom(@NonNull FfmpegStreamInfoVideo other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		streamNumberVideo = other.streamNumberVideo;
		fps = RationalNumber.of(other.fps);
		imgDims = ImageDimensions.of(other.imgDims);
		extradataHex = other.extradataHex;
	}

}

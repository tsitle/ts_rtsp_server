package org.tsitle.lib_ffmpeg.demux;

import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.jspecify.annotations.NonNull;

public class FfmpegStreamInfoBase {

	public int streamIx = -1;
	public @NonNull FfmpegCodec ffmpegCodec = FfmpegCodec.UNKNOWN;
	public @NonNull RationalNumber timeBasePts = RationalNumber.ofEmpty();
	/**
	 * Audio: works for AC3 and PCM, sometimes AAC, doesn't work for Opus<br />
	 * Video: works sometimes for H26x
	 */
	public long bitRate = -1L;

	protected FfmpegStreamInfoBase() { }

	protected void baseReset() {
		streamIx = -1;
		ffmpegCodec = FfmpegCodec.UNKNOWN;
		timeBasePts = RationalNumber.ofEmpty();
		bitRate = -1L;
	}

	protected void baseCopyFrom(@NonNull FfmpegStreamInfoBase other) {
		if (this == other) {
			return;
		}
		streamIx = other.streamIx;
		ffmpegCodec = other.ffmpegCodec;
		timeBasePts = RationalNumber.of(other.timeBasePts);
		bitRate = other.bitRate;
	}

}

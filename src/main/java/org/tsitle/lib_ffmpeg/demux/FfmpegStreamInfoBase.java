package org.tsitle.lib_ffmpeg.demux;

import org.tsitle.lib_xrtxp.common.helpers.RationalNumber;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.jspecify.annotations.NonNull;

public class FfmpegStreamInfoBase {

	public int streamIx;
	public @NonNull FfmpegCodec ffmpegCodec;
	public @NonNull RationalNumber timeBasePts;
	public double durationSecs;
	/**
	 * Audio: works for AC3 and PCM, sometimes AAC, doesn't work for Opus<br />
	 * Video: works sometimes for H26x
	 */
	public long bitRate;

	protected FfmpegStreamInfoBase() {
		baseReset();
	}

	protected void baseReset() {
		streamIx = -1;
		ffmpegCodec = FfmpegCodec.UNKNOWN;
		timeBasePts = RationalNumber.ofEmpty();
		durationSecs = -1.0;
		bitRate = -1L;
	}

	protected void baseCopyFrom(@NonNull FfmpegStreamInfoBase other) {
		if (this == other) {
			return;
		}
		streamIx = other.streamIx;
		ffmpegCodec = other.ffmpegCodec;
		timeBasePts = RationalNumber.of(other.timeBasePts);
		durationSecs = other.durationSecs;
		bitRate = other.bitRate;
	}

}

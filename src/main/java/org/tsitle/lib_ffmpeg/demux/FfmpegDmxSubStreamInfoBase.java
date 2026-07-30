package org.tsitle.lib_ffmpeg.demux;

import org.tsitle.lib_xrtxp.common.types.RationalNumber;
import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.jspecify.annotations.NonNull;

public class FfmpegDmxSubStreamInfoBase {

	public int subStreamIx;
	public @NonNull FfmpegCodec ffmpegCodec;
	public @NonNull RationalNumber timeBasePts;
	public double durationSecs;
	/**
	 * Audio: works for AC3 and PCM, sometimes AAC, doesn't work for Opus<br />
	 * Video: works sometimes for H26x
	 */
	public long bitRate;
	public @NonNull String extradataHex;

	protected FfmpegDmxSubStreamInfoBase() {
		baseClear();
	}

	protected void baseClear() {
		subStreamIx = -1;
		ffmpegCodec = FfmpegCodec.UNKNOWN;
		timeBasePts = RationalNumber.ofEmpty();
		durationSecs = -1.0;
		bitRate = -1L;
		extradataHex = "";
	}

	protected void baseCopyFrom(@NonNull FfmpegDmxSubStreamInfoBase other) {
		if (this == other) {
			return;
		}
		subStreamIx = other.subStreamIx;
		ffmpegCodec = other.ffmpegCodec;
		timeBasePts = RationalNumber.of(other.timeBasePts);
		durationSecs = other.durationSecs;
		bitRate = other.bitRate;
		extradataHex = other.extradataHex;
	}

}

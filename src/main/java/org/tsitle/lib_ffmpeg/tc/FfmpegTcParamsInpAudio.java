package org.tsitle.lib_ffmpeg.tc;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;

/**
 * Parameters for Audio Transcoder Input.
 */
public final class FfmpegTcParamsInpAudio extends FfmpegTcParamsInpBase {

	public @NonNull SampleRateEnum sampleRate = SampleRateEnum.UNKNOWN;
	public int channelCount = -1;

	public FfmpegTcParamsInpAudio() { }

	public void copyFrom(@NonNull FfmpegTcParamsInpAudio other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		sampleRate = other.sampleRate;
		channelCount = other.channelCount;
	}

}

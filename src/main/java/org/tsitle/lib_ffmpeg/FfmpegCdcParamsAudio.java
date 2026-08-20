package org.tsitle.lib_ffmpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;

/**
 * Audio Codec Parameters.
 */
public final class FfmpegCdcParamsAudio extends FfmpegCdcParamsBase {

	public @NonNull SampleRateEnum sampleRate;
	public int channelCount;
	public int samplesPerFrame;

	public FfmpegCdcParamsAudio() {
		super();

		clear();
	}

	public void clear() {
		baseClear();

		sampleRate = SampleRateEnum.UNKNOWN;
		channelCount = -1;
		samplesPerFrame = -1;
	}

	public void copyFrom(@NonNull FfmpegCdcParamsAudio other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		sampleRate = other.sampleRate;
		channelCount = other.channelCount;
		samplesPerFrame = other.samplesPerFrame;
	}

}

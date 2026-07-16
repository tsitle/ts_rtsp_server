package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;

public final class FfmpegStreamInfoAudio extends FfmpegStreamInfoBase {

	public int sampleRate = -1;
	public int channelCount = -1;
	/** works for PCM, doesn't work for Opus, sometimes works for AC3 and AAC */
	public int bitsPerCodedSample = -1;

	public void reset() {
		baseReset();

		sampleRate = -1;
		channelCount = -1;
		bitsPerCodedSample = -1;
	}

	public void copyFrom(@NonNull FfmpegStreamInfoAudio other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		sampleRate = other.sampleRate;
		channelCount = other.channelCount;
		bitsPerCodedSample = other.bitsPerCodedSample;
	}

}

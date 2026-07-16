package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;

public final class FfmpegStreamInfoAudio extends FfmpegStreamInfoBase {

	public @NonNull SampleRateEnum sampleRate;
	public int channelCount;
	/** works for PCM, doesn't work for Opus, sometimes works for AC3 and AAC */
	public int bitsPerCodedSample;
	public int aacSamplesPerFrame;
	public @NonNull String aacAudioSpecificConfigHex;

	public FfmpegStreamInfoAudio() {
		reset();
	}

	public void reset() {
		baseReset();

		sampleRate = SampleRateEnum.UNKNOWN;
		channelCount = -1;
		bitsPerCodedSample = -1;
		aacSamplesPerFrame = -1;
		aacAudioSpecificConfigHex = "";
	}

	public void copyFrom(@NonNull FfmpegStreamInfoAudio other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		sampleRate = other.sampleRate;
		channelCount = other.channelCount;
		bitsPerCodedSample = other.bitsPerCodedSample;
		aacSamplesPerFrame = other.aacSamplesPerFrame;
		aacAudioSpecificConfigHex = other.aacAudioSpecificConfigHex;
	}

}

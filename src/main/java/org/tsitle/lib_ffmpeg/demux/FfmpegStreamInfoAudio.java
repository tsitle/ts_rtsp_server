package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;

public final class FfmpegStreamInfoAudio extends FfmpegStreamInfoBase {

	public int streamNumberAudio;
	public @NonNull SampleRateEnum sampleRate;
	public int channelCount;
	/** works for PCM, doesn't work for Opus, sometimes works for AC3 and AAC */
	public int bitsPerCodedSample;
	/** works for PCM and AC3, doesn't work for E-AC-3 or Opus */
	public int samplesPerFrame;
	public @NonNull String aacAudioSpecificConfigHex;

	public FfmpegStreamInfoAudio() {
		reset();
	}

	public void reset() {
		baseReset();

		streamNumberAudio = -1;
		sampleRate = SampleRateEnum.UNKNOWN;
		channelCount = -1;
		bitsPerCodedSample = -1;
		samplesPerFrame = -1;
		aacAudioSpecificConfigHex = "";
	}

	public void copyFrom(@NonNull FfmpegStreamInfoAudio other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		streamNumberAudio = other.streamNumberAudio;
		sampleRate = other.sampleRate;
		channelCount = other.channelCount;
		bitsPerCodedSample = other.bitsPerCodedSample;
		samplesPerFrame = other.samplesPerFrame;
		aacAudioSpecificConfigHex = other.aacAudioSpecificConfigHex;
	}

}

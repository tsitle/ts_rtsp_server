package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;

public final class PcmInfo extends AvInfoBase<PcmInfo> {

	/** Offset of the audio samples in the audio data (in case there is a header) */
	public int samplesOffset;
	/** Length of the audio samples in the audio data */
	public int samplesLength;
	/** Number of audio channels */
	public int channels;
	/** Bits per sample (8 or 16) */
	public int bitsPerSample;
	/** Number of samples per channel that the audio data contains */
	public int samplesPerChannelInAudioData;

	public PcmInfo() {
		reset();
	}

	@Override
	public void reset() {
		samplesOffset = 0;
		samplesLength = 0;
		channels = 0;
		bitsPerSample = 0;
		samplesPerChannelInAudioData = 0;
	}

	@Override
	public void copyOf(@NonNull PcmInfo other) {
		reset();

		samplesOffset = other.samplesOffset;
		samplesLength = other.samplesLength;
		channels = other.channels;
		bitsPerSample = other.bitsPerSample;
		samplesPerChannelInAudioData = other.samplesPerChannelInAudioData;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"[" +
				"samplesOffset=" + Integer.toUnsignedString(samplesOffset) +
				", samplesLength=" + Integer.toUnsignedString(samplesLength) +
				", channels=" + Integer.toUnsignedString(channels) +
				", bitsPerSample=" + Integer.toUnsignedString(bitsPerSample) +
				", samplesPerChannelInAudioData=" + Integer.toUnsignedString(samplesPerChannelInAudioData) +
				"]";
	}

}

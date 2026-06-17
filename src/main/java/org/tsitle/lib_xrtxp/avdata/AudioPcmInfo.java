package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;

public final class AudioPcmInfo implements CodecInfoInterface<AudioPcmInfo>, Cloneable {

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

	public AudioPcmInfo() {
		reset();
	}

	@Override
	public int getPayloadOffset() {
		return samplesOffset;
	}

	@Override
	public int getPayloadLength() {
		return samplesLength;
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
	public void copyOf(@NonNull CodecInfoInterface<AudioPcmInfo> src) {
		reset();

		AudioPcmInfo tmpSrc = (AudioPcmInfo)src;
		samplesOffset = tmpSrc.samplesOffset;
		samplesLength = tmpSrc.samplesLength;
		channels = tmpSrc.channels;
		bitsPerSample = tmpSrc.bitsPerSample;
		samplesPerChannelInAudioData = tmpSrc.samplesPerChannelInAudioData;
	}

	@Override
	public @NonNull AudioPcmInfo clone() {
		try {
			return (AudioPcmInfo)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"samplesOffset=" + Integer.toUnsignedString(samplesOffset) +
				", samplesLength=" + Integer.toUnsignedString(samplesLength) +
				", channels=" + Integer.toUnsignedString(channels) +
				", bitsPerSample=" + Integer.toUnsignedString(bitsPerSample) +
				", samplesPerChannelInAudioData=" + Integer.toUnsignedString(samplesPerChannelInAudioData) +
				"]";
	}

	@Override
	public @NonNull String toString(boolean shortOutput) {
		return toString();
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(samplesOffset);
		baos.write(samplesLength);
		baos.write(channels);
		baos.write(bitsPerSample);
		baos.write(samplesPerChannelInAudioData);

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}

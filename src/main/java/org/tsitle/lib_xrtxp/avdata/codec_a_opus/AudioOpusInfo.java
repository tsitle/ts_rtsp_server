package org.tsitle.lib_xrtxp.avdata.codec_a_opus;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;

public final class AudioOpusInfo implements CodecInfoInterface<AudioOpusInfo>, Cloneable {

	/** Offset of the audio samples in the audio data (in case there is a header) */
	public int samplesOffset;
	/** Length of the audio samples in the audio data */
	public int samplesLength;
	/** Length of the complete Opus frame (including header) */
	public int frameLength;
	/** Number of samples per channel that the audio data contains */
	public int samplesPerChannelInAudioData;

	public AudioOpusInfo() {
		reset();
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public @NonNull String getValidationErrorMsg() {
		return "";
	}

	@Override
	public int getPayloadOffset() {
		// we skip the ADTS header for the RTP payload
		return samplesOffset;
	}

	@Override
	public int getPayloadLength() {
		// since we skipped the ADTS header for the RTP payload, the payload length is now the length of the audio samples
		return samplesLength;
	}

	@Override
	public void reset() {
		samplesOffset = 0;
		samplesLength = 0;
		frameLength = 0;
		samplesPerChannelInAudioData = 0;
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<AudioOpusInfo> src) {
		reset();

		AudioOpusInfo tmpSrc = (AudioOpusInfo)src;
		samplesOffset = tmpSrc.samplesOffset;
		samplesLength = tmpSrc.samplesLength;
		frameLength = tmpSrc.frameLength;
		samplesPerChannelInAudioData = tmpSrc.samplesPerChannelInAudioData;
	}

	@Override
	public @NonNull AudioOpusInfo clone() {
		try {
			return  (AudioOpusInfo)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"samplesOffset=" + Integer.toUnsignedString(samplesOffset) +
				", samplesLength=" + Integer.toUnsignedString(samplesLength) +
				", frameLength=" + Integer.toUnsignedString(frameLength) +
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
		baos.write(frameLength);
		baos.write(samplesPerChannelInAudioData);

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

}

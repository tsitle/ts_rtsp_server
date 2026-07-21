package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

public final class AudioPcmParser {

	private final int channels;
	private final int bitsPerSample;

	/**
	 * Constructor.
	 * @param channels Number of channels (1 = mono, 2 = stereo)
	 * @param bitsPerSample Bits per sample (8 or 16)
	 */
	public AudioPcmParser(int channels, int bitsPerSample) {
		this.channels = channels;
		this.bitsPerSample = bitsPerSample;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the PCMA/PCMU/LinearPCM data and returns a PcmInfo object with the parsed information.
	 * @param inputBv PCM data
	 * @return Parsed PCM information
	 */
	public @NonNull AudioPcmInfo parsePcmData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parsePcmData()";

		if (inputBv.getLength() < 1) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid PCM data size");
		}
		if (channels < 1 || channels > 2) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid audio channel count: " + channels);
		}
		if (bitsPerSample != 8 && bitsPerSample != 16) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid audio bits per sample: " + bitsPerSample);
		}

		//
		AudioPcmInfo resObj = new AudioPcmInfo();

		resObj.samplesOffset = 0;
		resObj.samplesLength = inputBv.getLength();
		resObj.channels = channels;
		resObj.bitsPerSample = bitsPerSample;
		resObj.samplesPerChannelInAudioData = inputBv.getLength() / (channels * (bitsPerSample / 8));

		return resObj;
	}

}

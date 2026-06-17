package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;

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
	 * Parses the PCMU/LinearPCM data and returns a PcmInfo object with the parsed information.
	 * @param pcmBuf PCM data
	 * @return Parsed PCM information
	 */
	public @NonNull AudioPcmInfo parsePcmData(@NonNull BufferExt pcmBuf) throws AvInvalidCodecDataException {
		final String FNC_NAME = AudioPcmParser.class.getSimpleName() + ".parsePcmData()";

		if (pcmBuf.getUsed() < 1) {
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
		resObj.samplesLength = pcmBuf.getUsed();
		resObj.channels = channels;
		resObj.bitsPerSample = bitsPerSample;
		resObj.samplesPerChannelInAudioData = pcmBuf.getUsed() / (channels * (bitsPerSample / 8));

		return resObj;
	}

}

package org.tsitle.rtsp.avdata;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidPcmDataException;

public final class PcmParser {

	private final int channels;
	private final int bitsPerSample;

	/**
	 * Constructor.
	 * @param channels Number of channels (1 = mono, 2 = stereo)
	 * @param bitsPerSample Bits per sample (8 or 16)
	 */
	public PcmParser(int channels, int bitsPerSample) {
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
	public PcmInfo parsePcmData(BufferExt pcmBuf) throws AvInvalidPcmDataException {
		final String FNC_NAME = PcmParser.class.getSimpleName() + ".parsePcmData()";

		if (pcmBuf.getUsed() < 1) {
			throw new AvInvalidPcmDataException(FNC_NAME + ": Invalid PCM data size");
		}
		if (channels < 1 || channels > 2) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid audio channel count: " + channels);
		}
		if (bitsPerSample != 8 && bitsPerSample != 16) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid audio bits per sample: " + bitsPerSample);
		}

		//
		PcmInfo resObj = new PcmInfo();

		resObj.samplesOffset = 0;
		resObj.samplesLength = pcmBuf.getUsed();
		resObj.channels = channels;
		resObj.bitsPerSample = bitsPerSample;
		resObj.samplesPerChannelInAudioData = pcmBuf.getUsed() / (channels * (bitsPerSample / 8));

		return resObj;
	}

}

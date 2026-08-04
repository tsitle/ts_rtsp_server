package org.tsitle.lib_dataprov.avstreams.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;

final class PcmFrameHandler {

	private final int channels;
	private int samplesPerFrame;
	private final boolean isBigEndian;

	private final int bytesPerSample;
	private final int bytesPerChannelAndSample;
	private int frameSizeBytes;

	/**
	 * Constructor.
	 * @param channels Number of channels (1 = mono, 2 = stereo)
	 * @param bitsPerSample Bits per sample (8 or 16)
	 * @param samplesPerFrame Samples per frame as required for RTP
	 * @param isBigEndian Is the input data big-endian?
	 */
	PcmFrameHandler(
				int channels,
				int bitsPerSample,
				int samplesPerFrame,
				boolean isBigEndian
			) {
		this.channels = channels;
		this.samplesPerFrame = samplesPerFrame;
		this.isBigEndian = isBigEndian;

		this.bytesPerSample = (bitsPerSample > 0 ? bitsPerSample / 8 : -1);
		this.bytesPerChannelAndSample = (channels > 0 && this.bytesPerSample > 0 ? channels * this.bytesPerSample : -1);
		this.frameSizeBytes = (
				channels > 0 && samplesPerFrame > 0 && this.bytesPerSample > 0 ?
						channels * samplesPerFrame * this.bytesPerSample
						: -1
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	void updateParamFrameSize(int frameSize) {
		this.frameSizeBytes = frameSize;
		this.samplesPerFrame = (
				this.channels > 0 && frameSize > 0 && this.bytesPerSample > 0 ?
						(int)((double)frameSize / (double)(this.channels * this.bytesPerSample))
						: -1
			);
	}

	int getFrameSizeBytes() {
		return frameSizeBytes;
	}

	int getSamplesPerFrame() {
		return samplesPerFrame;
	}

	void convertToBigEndian(@NonNull BufferExt inputBuf, @NonNull BufferExt outputBuf) throws InputStreamEosException {
		if (bytesPerChannelAndSample <= 0) {
			throw new IllegalStateException(getClass().getSimpleName() + ".convertToBigEndian(): " +
					"Invalid bytesPerChannelAndSample");
		}
		if (bytesPerSample != 1 && bytesPerSample != 2 && bytesPerSample != 4) {
			throw new IllegalStateException(getClass().getSimpleName() + ".convertToBigEndian(): " +
					"Invalid bytesPerSample");
		}

		int tmpRead = inputBuf.getUsed();
		if (tmpRead > 0 && tmpRead % bytesPerChannelAndSample != 0) {
			// discard any partial samples
			tmpRead -= (tmpRead % bytesPerChannelAndSample);
		}
		if (tmpRead <= 0) {
			throw new InputStreamEosException();
		}
		inputBuf.setUsed(tmpRead);
		//
		if (isBigEndian || bytesPerSample == 1) {
			outputBuf.copyOf(inputBuf);
			return;
		}

		// convert to big-endian
		outputBuf.clear();
		outputBuf.increaseSize(tmpRead);
		outputBuf.setUsed(tmpRead);
		if (bytesPerSample == 2) {
			for (int i = 0; i + 1 < tmpRead; i += bytesPerSample) {
				outputBuf.set(i, inputBuf.get(i + 1));
				outputBuf.set(i + 1, inputBuf.get(i));
			}
		} else {
			for (int i = 0; i + 3 < tmpRead; i += bytesPerSample) {
				outputBuf.set(i, inputBuf.get(i + 3));
				outputBuf.set(i + 1, inputBuf.get(i + 2));
				outputBuf.set(i + 2, inputBuf.get(i + 1));
				outputBuf.set(i + 3, inputBuf.get(i));
			}
		}
	}

}

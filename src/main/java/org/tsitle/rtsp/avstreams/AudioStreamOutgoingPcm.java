package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

import java.io.FileNotFoundException;

public class AudioStreamOutgoingPcm extends AvStreamOutgoingBase {

	private final boolean isBigEndian;

	private final int bytesPerSample;
	private final int bytesPerChannelAndSample;
	private final int rtpFrameSizeBytes;
	private final BufferExt cachedDataBuf2 = new BufferExt();

	/**
	 * Constructor.
	 * @param filename Audio file name
	 * @param channels Number of channels (1 = mono, 2 = stereo)
	 * @param bitsPerSample Bits per sample (8 or 16)
	 * @param rtpSamplesPerFrame Samples per frame as required for RTP
	 * @param isBigEndian Is the input data big-endian?
	 * @throws FileNotFoundException If the audio file cannot be found
	 */
	public AudioStreamOutgoingPcm(
				@NonNull String filename,
				int channels,
				int bitsPerSample,
				int rtpSamplesPerFrame,
				boolean isBigEndian
			) throws FileNotFoundException {
		super(new byte[0], 0, filename);

		//
		if (channels < 1 || channels > 2) {
			throw new IllegalArgumentException("Invalid audio channel count: " + channels);
		}
		if (bitsPerSample != 8 && bitsPerSample != 16) {
			throw new IllegalArgumentException("Invalid audio bits per sample: " + bitsPerSample);
		}
		if (rtpSamplesPerFrame < 1) {
			throw new IllegalArgumentException("Invalid audio samples per frame: " + rtpSamplesPerFrame);
		}

		this.isBigEndian = isBigEndian;

		this.bytesPerSample = bitsPerSample / 8;
		this.bytesPerChannelAndSample = channels * bytesPerSample;
		this.rtpFrameSizeBytes = channels * rtpSamplesPerFrame * this.bytesPerSample;

		this.cachedDataBuf2.increaseSize(this.rtpFrameSizeBytes);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Checks if there could be more samples in the stream
	 * @return True if there could be more samples, false otherwise
	 */
	@Override
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean hasMoreFrames() {
		return (bisAvailableBytes() >= bytesPerChannelAndSample);
	}

	/**
	 * Reads the next audio samples from the stream.
	 * @param frameBuf Output buffer to store the samples in
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException {
		BufferExt readIntoPtr = (isBigEndian || bytesPerSample == 1 ? frameBuf : cachedDataBuf2);
		//
		frameBuf.clear();
		frameBuf.increaseSize(rtpFrameSizeBytes);
		//
		int tmpRead = bisReadBytesNoCache(readIntoPtr.getBufPtr(), rtpFrameSizeBytes);
		if (tmpRead > 0 && tmpRead % bytesPerChannelAndSample != 0) {
			// discard any partial samples
			tmpRead -= (tmpRead % bytesPerChannelAndSample);
		}
		if (tmpRead <= 0) {
			throw new InputStreamEofException();
		}
		readIntoPtr.setUsed(tmpRead);
		//
		if (isBigEndian || bytesPerSample == 1) {
			return;
		}

		// convert to big-endian
		for (int i = 0; i + 1 < tmpRead; i += bytesPerSample) {
			frameBuf.getBufPtr()[i] = readIntoPtr.get(i + 1);
			frameBuf.getBufPtr()[i + 1] = readIntoPtr.get(i);
		}
		frameBuf.setUsed(tmpRead);
	}

}

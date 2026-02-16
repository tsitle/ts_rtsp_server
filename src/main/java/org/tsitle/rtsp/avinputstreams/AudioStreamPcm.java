package org.tsitle.rtsp.avinputstreams;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class AudioStreamPcm implements AvInputStreamInterface {

	private final String filename;
	private final boolean isBigEndian;

	private final int bytesPerSample;
	private final int bytesPerChannelAndSample;
	private final byte[] cachedDataBuf1;
	private final byte[] cachedDataBuf2;
	private FileInputStream fis;
	private BufferedInputStream bis;

	/**
	 * Constructor.
	 * @param filename Audio file name
	 * @param channels Number of channels (1 = mono, 2 = stereo)
	 * @param bitsPerSample Bits per sample (8 or 16)
	 * @param rtpSamplesPerFrame Samples per frame as required for RTP
	 * @param isBigEndian Is the input data big-endian?
	 * @throws FileNotFoundException If the audio file cannot be found
	 */
	public AudioStreamPcm(String filename, int channels, int bitsPerSample, int rtpSamplesPerFrame, boolean isBigEndian)
			throws FileNotFoundException {
		if (channels < 1 || channels > 2) {
			throw new IllegalArgumentException("Invalid audio channel count: " + channels);
		}
		if (bitsPerSample != 8 && bitsPerSample != 16) {
			throw new IllegalArgumentException("Invalid audio bits per sample: " + bitsPerSample);
		}
		if (rtpSamplesPerFrame < 1) {
			throw new IllegalArgumentException("Invalid audio samples per frame: " + rtpSamplesPerFrame);
		}

		this.filename = filename;
		this.isBigEndian = isBigEndian;

		this.bytesPerSample = bitsPerSample / 8;
		this.bytesPerChannelAndSample = channels * bytesPerSample;
		int rtpFrameSizeBytes = channels * rtpSamplesPerFrame * this.bytesPerSample;
		this.cachedDataBuf1 = new byte[rtpFrameSizeBytes];
		this.cachedDataBuf2 = new byte[rtpFrameSizeBytes];
		this.fis = openFile(filename);
		this.bis = new BufferedInputStream(this.fis);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Gets the length of the magic bytes array for frame start detection.
	 * @return Length of the magic bytes array
	 */
	@Override
	public int getMagicBytesLength() {
		return 0;
	}

	/**
	 * Checks if there could be more samples in the stream
	 * @return True if there could be more samples, false otherwise
	 */
	@Override
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean hasMoreFrames() {
		try {
			return (bis.available() >= bytesPerChannelAndSample);
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Reads the next audio samples from the stream.
	 * @param frameBuf Output buffer to store the samples in
	 */
	@Override
	public void getNextFrame(BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException {
		try {
			frameBuf.clear();
			int tmpRead = bis.read(cachedDataBuf1, 0, cachedDataBuf1.length);
			if (tmpRead > 0 && tmpRead % bytesPerChannelAndSample != 0) {
				// discard any partial samples
				tmpRead -= (tmpRead % bytesPerChannelAndSample);
			}
			if (tmpRead <= 0) {
				throw new InputStreamEofException();
			}
			//
			if (isBigEndian || bytesPerSample == 1) {
				frameBuf.copyOf(cachedDataBuf1, tmpRead);
				return;
			}

			// convert to big-endian
			for (int i = 0; i + 1 < tmpRead; i += bytesPerSample) {
				cachedDataBuf2[i] = cachedDataBuf1[i + 1];
				cachedDataBuf2[i + 1] = cachedDataBuf1[i];
			}
			//
			frameBuf.copyOf(cachedDataBuf2, tmpRead);
		} catch (IOException e) {
			throw new InputStreamIoException(e.getMessage());
		}
	}

	/**
	 * Rewinds the stream to the beginning
	 */
	@Override
	public void rewind() {
		try {
			bis.close();
			fis.close();
			fis = openFile(filename);
			bis = new BufferedInputStream(fis);
		} catch (IOException e) {
			// this should never happen
			throw new RuntimeException(e);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static FileInputStream openFile(String filename) throws FileNotFoundException {
		return new FileInputStream(filename);
	}

}

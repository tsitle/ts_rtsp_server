package org.tsitle.rtsp.avinputstreams;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public abstract class VideoStreamBase implements AvInputStreamInterface {

	protected byte[] frameStartMagicbytes;
	private final String filename;

	private FileInputStream fis;
	private BufferedInputStream bis;
	private byte[] cachedDataBuf = new byte[1024 * 1024];
	private int cachedDataLength = 0;

	/**
	 * Constructor.
	 * @param frameStartMagicbytes Magic bytes array for frame start detection
	 * @param filename Video file name
	 * @throws FileNotFoundException If the video file cannot be found
	 */
	protected VideoStreamBase(byte[] frameStartMagicbytes, String filename) throws FileNotFoundException {
		this.frameStartMagicbytes = frameStartMagicbytes;
		this.filename = filename;

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
		return frameStartMagicbytes.length;
	}

	/**
	 * Checks if there could be more frames in the stream
	 * @return True if there could be more frames, false otherwise
	 */
	@Override
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean hasMoreFrames() {
		try {
			return (cachedDataLength > 0 || bis.available() > 0);
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Reads the next video frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 */
	@Override
	public abstract void getNextFrame(BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException;

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

	/**
	 * Reads the next video frame from the stream
	 *
	 * @param frameBuf Output buffer to store the frame in
	 * @param isFirstFrame Is this the first frame in the stream?
	 * @param magicBytesVersionA Version A of the magic bytes - needs to be the longer one
	 * @param magicBytesVersionB Version B of the magic bytes - needs to be the shorter one
	 */
	protected void internalGetNextFrame(
				BufferExt frameBuf,
				boolean isFirstFrame,
				byte[] magicBytesVersionA,
				byte[] magicBytesVersionB
			) throws InputStreamIoException, InputStreamEofException {
		if ((! isFirstFrame || (magicBytesVersionA == null && magicBytesVersionB == null)) && frameStartMagicbytes.length == 0) {
			throw new IllegalStateException("frameStartMagicbytes is not set");
		}

		while (true) {
			int firstStart;

			//
			if (isFirstFrame && magicBytesVersionA != null && magicBytesVersionB != null) {
				readMoreIntoCache();
				frameStartMagicbytes = magicBytesVersionA;
				firstStart = findStartCode(cachedDataBuf, cachedDataLength, 0);
				if (firstStart < 0) {
					frameStartMagicbytes = magicBytesVersionB;
					firstStart = findStartCode(cachedDataBuf, cachedDataLength, 0);
				}
			} else {
				firstStart = findStartCode(cachedDataBuf, cachedDataLength, 0);
			}

			//
			if (firstStart > 0) {
				System.arraycopy(cachedDataBuf, firstStart, cachedDataBuf, 0, cachedDataLength - firstStart);
				cachedDataLength -= firstStart;
				firstStart = 0;
			}

			if (firstStart == 0) {
				int nextStart = findStartCode(cachedDataBuf, cachedDataLength, frameStartMagicbytes.length);
				if (nextStart > 0) {
					frameBuf.clear();
					frameBuf.copyFrom(cachedDataBuf, 0, 0, nextStart);
					System.arraycopy(cachedDataBuf, nextStart, cachedDataBuf, 0, cachedDataLength - nextStart);
					cachedDataLength -= nextStart;
					return;
				}
			}

			if (! readMoreIntoCache()) {
				if (cachedDataLength > 0) {
					frameBuf.clear();
					frameBuf.copyFrom(cachedDataBuf, 0, 0, cachedDataLength);
					cachedDataLength = 0;
					return;
				}
				try {
					bis.close();
					fis.close();
				} catch (IOException e) {
					throw new InputStreamIoException(e.getMessage());
				}
				throw new InputStreamEofException();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static FileInputStream openFile(String filename) throws FileNotFoundException {
		return new FileInputStream(filename);
	}

	private boolean readMoreIntoCache() throws InputStreamIoException {
		if (cachedDataLength == cachedDataBuf.length) {
			cachedDataBuf = Arrays.copyOf(cachedDataBuf, cachedDataBuf.length * 2);
		}
		try {
			int read = bis.read(cachedDataBuf, cachedDataLength, cachedDataBuf.length - cachedDataLength);
			if (read <= 0) {
				return false;
			}
			cachedDataLength += read;
			return true;
		} catch (IOException e) {
			throw new InputStreamIoException(e.getMessage());
		}
	}

	private int findStartCode(byte[] data, int length, int startIdx) {
		boolean isOk;
		for (int i = startIdx; i + frameStartMagicbytes.length - 1 < length; i++) {
			isOk = true;
			for (int j = 0; j < frameStartMagicbytes.length; j++) {
				if (data[i + j] != frameStartMagicbytes[j]) {
					isOk = false;
					break;
				}
			}
			if (isOk) {
				return i;
			}
		}
		return -1;
	}

}

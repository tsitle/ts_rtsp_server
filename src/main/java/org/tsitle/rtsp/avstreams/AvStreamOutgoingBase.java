package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public abstract class AvStreamOutgoingBase {

	private byte[] frameStartMagicbytes;
	private int magicBytesLengthInBits;
	private final String filename;

	private FileInputStream fis;
	private BufferedInputStream bis;
	private byte[] cachedDataBuf = new byte[1024 * 1024];
	private int cachedDataLength = 0;

	/**
	 * Constructor.
	 * @param frameStartMagicbytes Magic bytes array for frame start detection
	 * @param magicBytesLengthInBits Length of the magic bytes array in bits
	 * @param filename Input file name
	 * @throws FileNotFoundException If the input file cannot be found
	 */
	protected AvStreamOutgoingBase(
				byte[] frameStartMagicbytes,
				int magicBytesLengthInBits,
				@NonNull String filename
			) throws FileNotFoundException {
		this.frameStartMagicbytes = frameStartMagicbytes;
		this.magicBytesLengthInBits = magicBytesLengthInBits;
		this.filename = filename;

		this.fis = openFile(filename);
		this.bis = new BufferedInputStream(this.fis);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Gets the length of the magic bytes array for frame start detection.
	 * @return Length of the magic bytes array in bits
	 */
	public int getMagicBytesLengthBits() {
		return magicBytesLengthInBits;
	}

	/**
	 * Checks if there could be more frames in the stream
	 * @return True if there could be more frames, false otherwise
	 */
	public abstract boolean hasMoreFrames();

	/**
	 * Reads the next video frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 */
	public abstract void getNextFrame(@NonNull BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException;

	/**
	 * Rewinds the stream to the beginning
	 */
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

	protected int bisAvailableBytes() {
		try {
			return bis.available();
		} catch (IOException e) {
			return 0;
		}
	}

	protected int getCachedDataLengthForFramesWithStartCode() {
		return cachedDataLength;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads some bytes from the stream.
	 * @param buf Output buffer to store the data in
	 * @param length Number of bytes to read
	 * @return Number of bytes read
	 */
	protected int bisReadBytes(byte[] buf, int length) throws InputStreamIoException {
		return bisReadBytes(buf, 0, length);
	}

	/**
	 * Reads some bytes from the stream.
	 * @param buf Output buffer to store the data in
	 * @param destOffset Offset in the output buffer to start writing at
	 * @param length Number of bytes to read
	 * @return Number of bytes read
	 */
	protected int bisReadBytes(byte[] buf, int destOffset, int length) throws InputStreamIoException {
		try {
			return bis.read(buf, destOffset, length);
		} catch (IOException e) {
			throw new InputStreamIoException(e.getMessage());
		}
	}

	/**
	 * Reads the next video frame from the stream
	 *
	 * @param frameBuf Output buffer to store the frame in
	 * @param isFirstFrame Is this the first frame in the stream?
	 * @param magicBytesVersionA Version A of the magic bytes - needs to be the longer one
	 * @param magicBytesVersionB Version B of the magic bytes - needs to be the shorter one
	 */
	protected void internalGetNextFrameWithStartCode(
				@NonNull BufferExt frameBuf,
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
				magicBytesLengthInBits = frameStartMagicbytes.length * 8;
				firstStart = findStartCode(cachedDataBuf, cachedDataLength, 0);
				if (firstStart < 0) {
					frameStartMagicbytes = magicBytesVersionB;
					magicBytesLengthInBits = frameStartMagicbytes.length * 8;
					firstStart = findStartCode(cachedDataBuf, cachedDataLength, 0);
				}
			} else {
				firstStart = findStartCode(cachedDataBuf, cachedDataLength, 0);
			}

			//
			if (firstStart > 0) {
				// we need to skip over some garbage data before the first frame starts
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
					break;
				}
			}

			// we couldn't find the start code within the current buffer - try reading more data
			if (! readMoreIntoCache()) {
				if (cachedDataLength > 0) {
					// we couldn't read more data, but we still have some data in the buffer
					frameBuf.clear();
					frameBuf.copyFrom(cachedDataBuf, 0, 0, cachedDataLength);
					cachedDataLength = 0;
					break;
				}
				//
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

	private static @NonNull FileInputStream openFile(@NonNull String filename) throws FileNotFoundException {
		return new FileInputStream(filename);
	}

	private boolean readMoreIntoCache() throws InputStreamIoException {
		if (cachedDataLength == cachedDataBuf.length) {
			cachedDataBuf = Arrays.copyOf(cachedDataBuf, cachedDataBuf.length * 2);
		}
		int read = bisReadBytes(cachedDataBuf, cachedDataLength, cachedDataBuf.length - cachedDataLength);
		if (read <= 0) {
			return false;
		}
		cachedDataLength += read;
		return true;
	}

	private int findStartCode(byte[] data, int length, int startIdx) {
		if (magicBytesLengthInBits % 8 == 0) {
			return findStartCodeEvenMB(data, length, startIdx);
		}
		return findStartCodeOddMB(data, length, startIdx);
	}

	private int findStartCodeEvenMB(byte[] data, int length, int startIdx) {
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

	private int findStartCodeOddMB(byte[] data, int length, int startIdx) {
		boolean isOk;
		int magicBitsLeft;
		for (int i = startIdx; i + frameStartMagicbytes.length - 1 < length; i++) {
			isOk = true;
			magicBitsLeft = magicBytesLengthInBits;
			for (int j = 0; j < frameStartMagicbytes.length; j++) {
				if ((magicBitsLeft >= 8 && data[i + j] != frameStartMagicbytes[j]) ||
						(magicBitsLeft == 4 && (byte)(data[i + j] & (byte)0xF0) != (byte)(frameStartMagicbytes[j] & (byte)0xF0))) {
					isOk = false;
					break;
				}
				magicBitsLeft -= 8;
			}
			if (isOk) {
				return i;
			}
		}
		return -1;
	}

}

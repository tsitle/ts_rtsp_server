package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.util.Arrays;

public abstract class AvStreamOutgoingFromFileBase extends AvStreamOutgoingBase<AvStreamIncomingFromFile> {

	private byte[] frameStartMagicbytes;
	private int magicBytesLengthInBits;

	private byte[] cachedDataBuf = new byte[4 * 1024];  // 4 kB is only the initial size - it can dynamically grow
	private int cachedDataLength = 0;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 * @param frameStartMagicbytes Magic bytes array for frame start detection
	 * @param magicBytesLengthInBits Length of the magic bytes array in bits
	 */
	protected AvStreamOutgoingFromFileBase(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromFile avStreamIncoming,
				byte[] frameStartMagicbytes,
				int magicBytesLengthInBits
			) {
		super(logMsgInterface, avStreamIncoming);

		if (magicBytesLengthInBits % 4 != 0) {
			throw new IllegalArgumentException("magicBytesLengthInBits must be a multiple of 4");
		}
		this.frameStartMagicbytes = frameStartMagicbytes;
		this.magicBytesLengthInBits = magicBytesLengthInBits;
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

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected int getCachedDataLengthForFramesWithStartCode() {
		return cachedDataLength;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next video frame from the stream.
	 * @param fncName Name of the calling function for logging
	 * @param frameBuf Output buffer to store the frame in
	 * @param isFirstFrame Is this the first frame in the stream?
	 * @param magicBytesVersionA Version A of the magic bytes - needs to be the longer one
	 * @param magicBytesVersionB Version B of the magic bytes - needs to be the shorter one
	 * @param readMaxBytes Maximum number of bytes to read from the stream (-1 for unlimited)
	 */
	protected void internalGetNextFrameWithStartCode(
				@NonNull String fncName,
				@NonNull BufferExt frameBuf,
				boolean isFirstFrame,
				byte[] magicBytesVersionA,
				byte[] magicBytesVersionB,
				int readMaxBytes
			) throws InputStreamIoException, InputStreamEosException {
		if ((! isFirstFrame || (magicBytesVersionA == null && magicBytesVersionB == null)) && frameStartMagicbytes.length == 0) {
			throw new IllegalStateException(fncName + ": frameStartMagicbytes is not set");
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
				logDebug(fncName, "Skipping " + firstStart + " bytes of garbage data");
				System.arraycopy(cachedDataBuf, firstStart, cachedDataBuf, 0, cachedDataLength - firstStart);
				cachedDataLength -= firstStart;
				firstStart = 0;
			}

			if (firstStart == 0) {
				if (readMaxBytes == -1) {
					int nextStart = findStartCode(cachedDataBuf, cachedDataLength, frameStartMagicbytes.length);
					if (nextStart > 0) {
						frameBuf.clear();
						frameBuf.copyFrom(cachedDataBuf, 0, 0, nextStart);
						System.arraycopy(cachedDataBuf, nextStart, cachedDataBuf, 0, cachedDataLength - nextStart);
						cachedDataLength -= nextStart;
						break;
					}
				} else {
					while (cachedDataLength < readMaxBytes) {
						readMoreIntoCache();
					}
					frameBuf.clear();
					frameBuf.copyFrom(cachedDataBuf, 0, 0, readMaxBytes);
					System.arraycopy(cachedDataBuf, readMaxBytes, cachedDataBuf, 0, cachedDataLength - readMaxBytes);
					cachedDataLength -= readMaxBytes;
					break;
				}
			}

			// we couldn't find the start code within the current buffer - try reading more data
			try {
				readMoreIntoCache();
			} catch (InputStreamEosException e) {
				if (cachedDataLength > 0) {
					// we couldn't read more data, but we still have some data in the buffer
					frameBuf.clear();
					frameBuf.copyFrom(cachedDataBuf, 0, 0, cachedDataLength);
					cachedDataLength = 0;
					break;
				}
				//
				throw new InputStreamEosException();
			}
		}
	}

	/**
	 * Reads only a fixed number of bytes from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 * @param bytesToRead Number of bytes to read
	 * @throws InputStreamIoException If the stream cannot be read
	 * @throws InputStreamEosException If the end of the stream is reached before the requested number of bytes is read
	 */
	protected void internalReadRemainingFrameForFrameWithStartCode(
				@NonNull BufferExt frameBuf,
				int bytesToRead
			) throws InputStreamIoException, InputStreamEosException {
		int dstOffset = frameBuf.getUsed();
		while (bytesToRead > 0) {
			if (cachedDataLength > 0) {
				int toReadFromCache = Math.min(bytesToRead, cachedDataLength);
				frameBuf.copyFrom(cachedDataBuf, 0, dstOffset, toReadFromCache);
				dstOffset += toReadFromCache;
				if (toReadFromCache < cachedDataLength) {
					System.arraycopy(
							cachedDataBuf,
							toReadFromCache,
							cachedDataBuf,
							0,
							cachedDataLength - toReadFromCache
						);
				}
				cachedDataLength -= toReadFromCache;
				bytesToRead -= toReadFromCache;
			} else {
				readMoreIntoCache();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void readMoreIntoCache() throws InputStreamIoException, InputStreamEosException {
		if (cachedDataLength == cachedDataBuf.length) {
			cachedDataBuf = Arrays.copyOf(cachedDataBuf, cachedDataBuf.length * 2);
		}
		int tmpDidRead = avStreamIncoming.readBytes(
				cachedDataBuf,
				cachedDataLength,
				cachedDataBuf.length - cachedDataLength
			);
		cachedDataLength += tmpDidRead;
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

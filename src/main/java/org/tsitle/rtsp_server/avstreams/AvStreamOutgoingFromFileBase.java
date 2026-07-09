package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

import java.util.Arrays;

public abstract class AvStreamOutgoingFromFileBase extends AvStreamOutgoingBase<AvStreamIncomingFromFile> {

	private final byte[] frameStartMagicBytesPtr_fixed;
	private final int magicBytesLengthInBits_fixed;
	private byte[] frameStartMagicBytesPtr_a_long = null;
	private byte[] frameStartMagicBytesPtr_b_short = null;

	private byte[] cachedDataBuf = new byte[4 * 1024];  // 4 kB is only the initial size - it can dynamically grow
	private int cachedDataLength = 0;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 * @param frameStartMagicBytes Magic Bytes for frame start detection
	 * @param magicBytesLengthInBits Length of the Magic Bytes array in bits
	 */
	protected AvStreamOutgoingFromFileBase(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromFile avStreamIncoming,
				byte[] frameStartMagicBytes,
				int magicBytesLengthInBits
			) {
		super(logMsgInterface, avStreamIncoming);

		if (magicBytesLengthInBits % 4 != 0) {
			throw new IllegalArgumentException("magicBytesLengthInBits must be zero or a multiple of 4");
		}
		this.frameStartMagicBytesPtr_fixed = frameStartMagicBytes;
		this.magicBytesLengthInBits_fixed = magicBytesLengthInBits;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Gets the minimum length of the Magic Bytes for frame start detection.
	 * @return Length of the Magic Bytes array in bits
	 */
	public int getMinimumMagicBytesLengthBits() {
		if (magicBytesLengthInBits_fixed > 0) {
			return magicBytesLengthInBits_fixed;
		}
		int resI = (frameStartMagicBytesPtr_b_short == null ? 0 : frameStartMagicBytesPtr_b_short.length);
		if (resI == 0) {
			resI = (frameStartMagicBytesPtr_a_long == null ? 1 : frameStartMagicBytesPtr_a_long.length);
		}
		return resI * 8;
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
	 * @param magicBytesVersionA_long Version A of the magic bytes - needs to be the longer one (can be null if not used)
	 * @param magicBytesVersionB_short Version B of the magic bytes - needs to be the shorter one (can be null if not used)
	 * @param readMaxBytes Maximum number of bytes to read from the stream (-1 for unlimited)
	 */
	protected void internalGetNextFrameWithStartCode(
				@NonNull String fncName,
				@NonNull BufferExt frameBuf,
				boolean isFirstFrame,
				byte[] magicBytesVersionA_long,
				byte[] magicBytesVersionB_short,
				int readMaxBytes
			) throws InputStreamIoException, InputStreamEosException {
		if (magicBytesVersionA_long == null && magicBytesVersionB_short == null &&
				frameStartMagicBytesPtr_fixed.length == 0) {
			throw new IllegalStateException(fncName + ": frameStartMagicBytesPtr_fixed is not set");
		}

		frameStartMagicBytesPtr_a_long = magicBytesVersionA_long;
		frameStartMagicBytesPtr_b_short = magicBytesVersionB_short;
		final int minimumMagicBytesLengthBytes = getMinimumMagicBytesLengthBits() / 8;

		while (true) {
			int firstStart;

			//
			if (isFirstFrame && magicBytesVersionA_long != null && magicBytesVersionB_short != null) {
				readMoreIntoCache();
			}
			firstStart = findStartCode(cachedDataBuf, cachedDataLength, 0);

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
					int nextStart = findStartCode(cachedDataBuf, cachedDataLength, minimumMagicBytesLengthBytes);
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

	/**
	 * Find the index of the start code in the given data array.
	 * @param data Data to search in
	 * @param length Length of the data array
	 * @param startIdx Starting index to search from
	 * @return Index of the start code in the given data array plus [startIdx], or -1 if not found.
	 */
	private int findStartCode(byte[] data, int length, int startIdx) {
		if (magicBytesLengthInBits_fixed > 0 && magicBytesLengthInBits_fixed % 8 == 0) {
			return findStartCodeEvenMB(frameStartMagicBytesPtr_fixed, data, length, startIdx);
		}
		if (magicBytesLengthInBits_fixed == 0 && frameStartMagicBytesPtr_a_long != null) {
			if (frameStartMagicBytesPtr_b_short == null) {
				return findStartCodeEvenMB(frameStartMagicBytesPtr_a_long, data, length, startIdx);
			}

			int tmpIx = findStartCodeEvenMB(frameStartMagicBytesPtr_b_short, data, length, startIdx);

			if (tmpIx > startIdx) {
				// we found the shorter Magic Bytes after [startIdx], so the longer Magic Bytes might start at [startIdx]
				int tmpLonger = findStartCodeEvenMB(
						frameStartMagicBytesPtr_a_long,
						data,
						tmpIx + frameStartMagicBytesPtr_b_short.length,
						tmpIx - 1
					);
				if (tmpLonger >= 0 && tmpLonger == tmpIx - 1) {
					// indeed we have found the longer Magic Bytes at [tmpIx - 1]
					return tmpLonger;
				}
			}
			return tmpIx;
		}
		if (magicBytesLengthInBits_fixed == 0) {
			throw new IllegalStateException("magicBytesLengthInBits_fixed is not set");
		}
		return findStartCodeOddMB_fixed(data, length, startIdx);
	}

	private static int findStartCodeEvenMB(byte[] magicBytes, byte[] data, int length, int startIdx) {
		boolean isOk;
		for (int i = startIdx; i + magicBytes.length - 1 < length; i++) {
			isOk = true;
			for (int j = 0; j < magicBytes.length; j++) {
				if (data[i + j] != magicBytes[j]) {
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

	private int findStartCodeOddMB_fixed(byte[] data, int length, int startIdx) {
		boolean isOk;
		int magicBitsLeft;
		for (int i = startIdx; i + frameStartMagicBytesPtr_fixed.length - 1 < length; i++) {
			isOk = true;
			magicBitsLeft = magicBytesLengthInBits_fixed;
			for (int j = 0; j < frameStartMagicBytesPtr_fixed.length; j++) {
				if ((magicBitsLeft >= 8 && data[i + j] != frameStartMagicBytesPtr_fixed[j]) ||
						(magicBitsLeft == 4 && (byte)(data[i + j] & (byte)0xF0) != (byte)(frameStartMagicBytesPtr_fixed[j] & (byte)0xF0))) {
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

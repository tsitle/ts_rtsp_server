package org.tsitle.rtsp.helpers;

import java.io.ByteArrayOutputStream;

/**
 * Helper class for writing bits to a buffer.
 */
public final class BitWriterHelper {

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();
	/** Accumulates bits */
	private int currentByte = 0;
	/** Number of bits currently in currentByte (0–7) */
	private int bitPosition = 0;
	private int totalBitsWritten = 0;
	private int totalPaddingBitsWritten = 0;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Writes the lowest numBits from value into the buffer (MSB-first).
	 * @param value The integer containing bits to write
	 * @param numBits Number of bits to write (1–32)
	 */
	public void writeBits(int value, int numBits) {
		if (numBits < 0 || numBits > 32) {
			throw new IllegalArgumentException("numBits must be between 0 and 32");
		}
		if (numBits == 0) {
			return;
		}

		for (int i = numBits - 1; i >= 0; i--) {
			int bit = (value >> i) & 1;

			currentByte = (currentByte << 1) | bit;
			bitPosition++;

			if (bitPosition == 8) {
				flushCurrentByte();
			}
		}
		totalBitsWritten += numBits;
	}

	/**
	 * Flush remaining bits (pads with zeros if necessary).
	 */
	public void flush() {
		if (bitPosition > 0) {
			totalPaddingBitsWritten = (8 - bitPosition);
			currentByte <<= (8 - bitPosition); // pad with zeros
			flushCurrentByte();
		}
	}

	/**
	 * Returns the written bytes.
	 */
	public byte[] toByteArray() {
		flush();
		return output.toByteArray();
	}

	/**
	 * Returns the total number of bits written.
	 * @return Total number of bits written
	 */
	@SuppressWarnings("unused")
	public int getTotalBitsWritten() {
		return totalBitsWritten;
	}

	/**
	 * Returns the total number of padding bits written.
	 * @return Total number of padding bits written
	 */
	@SuppressWarnings("unused")
	public int getTotalPaddingBitsWritten() {
		return totalPaddingBitsWritten;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void flushCurrentByte() {
		output.write(currentByte);
		currentByte = 0;
		bitPosition = 0;
	}

}

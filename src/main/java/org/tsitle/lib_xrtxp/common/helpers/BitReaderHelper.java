package org.tsitle.lib_xrtxp.common.helpers;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;

/**
 * Helper class for reading bits from a buffer.
 */
public final class BitReaderHelper {

	private final BufferExt buffer;
	private int currentByte;
	private int bytePos;
	private int bitPos = 8;

	/**
	 * Constructor.
	 * @param buffer Input buffer
	 */
	public BitReaderHelper(@NonNull BufferExt buffer, int offset) {
		this.buffer = buffer;
		this.bytePos = offset;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next single bit from the bitstream.
	 * @return Value (either 0 or 1)
	 * @throws BitReaderEosException If the end of the bitstream is reached
	 */
	public int readBit() throws BitReaderEosException {
		if (bitPos == 8) {
			if (bytePos >= buffer.getUsed()) {
				throw new BitReaderEosException();
			}
			currentByte = buffer.get(bytePos++) & 0xFF;
			bitPos = 0;
		}
		int bit = (currentByte >> (7 - bitPos)) & 1;
		bitPos++;
		return bit;
	}

	/**
	 * Reads n bits from the bitstream.
	 * @param n Number of bits to read
	 * @return Read bits (e.g., read four bits b1001 results in d9)
	 * @throws BitReaderEosException If the end of the bitstream is reached
	 */
	public int readBits(int n) throws BitReaderEosException {
		if (n > 32) {
			throw new IllegalArgumentException("Cannot read more than 32 bits at once");
		}
		int val = 0;
		for (int i = 0; i < n; i++) {
			val = (val << 1) | readBit();
		}
		return val;
	}

	/**
	 * Reads an unsigned Exp-Golomb coded integer (often called ue(v) in video specs like H.264/H.265) from the bitstream.
	 * @return Unsigned integer
	 * @throws BitReaderEosException If the end of the bitstream is reached
	 */
	public int readH26xUE() throws BitReaderEosException {
		// count leading zeros
		int zeros = 0;
		while (readBit() == 0) {
			zeros++;
		}
		if (zeros > 31) {
			throw new IllegalStateException("Invalid Exp-Golomb code");
		}
		// build the base value 2^k
		int value = 1 << zeros;
		// read the k info bits and append them
		value |= readBits(zeros);
		// convert to Exp-Golomb result
		return value - 1;
	}

	/**
	 * Reads a signed Exp-Golomb coded integer (often called ue(v) in video specs like H.264/H.265) from the bitstream.
	 * @return Signed integer
	 * @throws BitReaderEosException If the end of the bitstream is reached
	 */
	public int readH26xSE() throws BitReaderEosException {
		// read an unsigned Exp‑Golomb number
		int codeNum = readH26xUE();
		// convert that unsigned number to a magnitude
		int val = (codeNum + 1) / 2;
		// assign a sign based on parity
		return (codeNum % 2 == 0) ? -val : val;
	}

}

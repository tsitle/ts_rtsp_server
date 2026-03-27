package org.tsitle.rtsp.buffers;

import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Read-only view of a BufferExt.
 */
public final class BufferView {

	private final @NonNull BufferExt bufPtr;
	private int offset;
	private int length;

	public BufferView(@NonNull BufferExt bufPtr) {
		this(bufPtr, 0, bufPtr.getUsed());
	}

	@SuppressWarnings("unused")
	public BufferView(@NonNull BufferExt bufPtr, int offset) {
		this(bufPtr, offset, bufPtr.getUsed() - offset);
	}

	public BufferView(@NonNull BufferExt bufPtr, int offset, int length) {
		if (offset < 0 || offset > bufPtr.getUsed()) {
			throw new IllegalArgumentException("Invalid offset");
		}
		if (length < 0 || offset + length > bufPtr.getUsed()) {
			throw new IllegalArgumentException("Invalid length");
		}
		this.bufPtr = bufPtr;
		setOffset(offset);
		setLength(length);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get pointer to the internal buffer - this does ignore the view's offset and length.
	 * @return Buffer pointer
	 */
	public byte[] getInternalBaPtr() {
		return bufPtr.getBaPtr();
	}

	/**
	 * Get a copy of the buffer in the view.
	 * @return Buffer copy
	 */
	@SuppressWarnings("unused")
	public @NonNull BufferExt getViewAsBe() {
		BufferExt resObj = new BufferExt();
		resObj.copyOf(bufPtr, offset, length);
		return resObj;
	}

	/**
	 * Copy the buffer in the view into another buffer.
	 * @param dst Destination buffer
	 */
	public void copyViewIntoBe(@NonNull BufferExt dst) {
		dst.copyOf(bufPtr, offset, length);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Set the offset of the view.
	 * @param value New offset
	 */
	public void setOffset(int value) {
		offset = value;
	}

	/**
	 * Increase the offset by a given value.
	 * @param value Offset increment (can be negative)
	 */
	public void increaseOffset(int value) {
		setOffset(offset + value);
	}

	/**
	 * Get the offset of the view.
	 * @return Offset
	 */
	public int getOffset() {
		return offset;
	}

	/**
	 * Set the length of the view.
	 * @param value New length
	 */
	public void setLength(int value) {
		length = value;
	}

	/**
	 * Increase the length by a given value.
	 * @param value Length increment (can be negative)
	 */
	public void increaseLength(int value) {
		setLength(length + value);
	}

	/**
	 * Get the length of the view.
	 * @return Length of the view
	 */
	public int getLength() {
		return length;
	}

	/**
	 * Get the length of the internal buffer.
	 * @return Length of the internal buffer
	 */
	public int getInternalBeLength() {
		return bufPtr.getUsed();
	}

	/**
	 * Check if the view is empty.
	 * @return True if the view is empty, false otherwise
	 */
	public boolean isEmpty() {
		return (length == 0);
	}

	/**
	 * Get the byte at the specified index within the view.
	 * @param index Index within the view
	 * @return Byte at the specified index
	 */
	public byte getByte(int index) {
		return bufPtr.get(offset + index);
	}

	/**
	 * Reset the view.
	 */
	public void clear() {
		offset = 0;
		length = bufPtr.getUsed();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get an integer from the buffer view at the current offset in big-endian format.
	 * @param advanceOffset If true, advance the offset by 4 bytes after reading
	 * @return Integer value
	 */
	public int getIntFromBigEndian(boolean advanceOffset) {
		if (offset + 4 > length) {
			throw new IndexOutOfBoundsException("Invalid offset");
		}
		byte[] tmpBa = new byte[4];
		for (int i = 0; i < tmpBa.length; i++) {
			tmpBa[i] = getByte(i);
		}
		if (advanceOffset) {
			increaseOffset(4);
		}
		return ByteBuffer.wrap(tmpBa)
				.order(ByteOrder.BIG_ENDIAN)
				.getInt();
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Convert the buffer in the current view to a hex string.
	 * @return Hex string representation
	 */
	@SuppressWarnings("unused")
	public @NonNull String toHexString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			sb.append(String.format("%02X", getByte(i)));
		}
		return sb.toString();
	}

}

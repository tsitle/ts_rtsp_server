package org.tsitle.rtsp.buffers;

/**
 * BufferExt provides a resizable byte buffer with methods for copying data and accessing buffer contents.
 */
public class BufferExt implements Cloneable {

	private byte[] buf = new byte[1024 * 64];
	private int used;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Get the number of bytes currently used in the buffer.
	 * @return Number of bytes used
	 */
	public int getUsed() {
		return used;
	}

	/**
	 * Checks if the buffer is empty.
	 * @return True if the buffer is empty, false otherwise
	 */
	@SuppressWarnings("unused")
	public boolean isEmpty() {
		return (used == 0);
	}

	/**
	 * Clears the buffer.
	 */
	public void clear() {
		used = 0;
	}

	/**
	 * Get a pointer to the internal buffer.
	 * @return Pointer to the internal buffer
	 */
	public byte[] getBuf() {
		return buf;
	}

	/**
	 * Get a byte from the buffer.
	 * @param index Byte index
	 * @return Byte at the given index
	 */
	public byte get(int index) {
		if (index >= used) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds for used buffer size " + used);
		}
		return buf[index];
	}

	/**
	 * Copy data from a byte array into the buffer.
	 * @param srcData Source byte array
	 * @param srcOffset Source offset
	 * @param dstOffset Destination offset
	 * @param len Length of data to copy
	 */
	public void copyFrom(byte[] srcData, int srcOffset, int dstOffset, int len) {
		validateArgs(srcData == null ? 0 : srcData.length, srcOffset, dstOffset, len);
		if (dstOffset + len > buf.length) {
			increaseSize(dstOffset + len);
		}
		if (len > 0 && srcData != null) {
			System.arraycopy(srcData, srcOffset, buf, dstOffset, len);
		}
		if (dstOffset + len > used) {
			used = dstOffset + len;
		}
	}

	/**
	 * Copy data from another buffer into this buffer.
	 * @param srcBuf Source buffer
	 * @param srcOffset Source offset
	 * @param dstOffset Destination offset
	 * @param len Length of data to copy
	 */
	@SuppressWarnings("unused")
	public void copyFrom(BufferExt srcBuf, int srcOffset, int dstOffset, int len) {
		copyFrom(srcBuf.getBuf(), srcOffset, dstOffset, len);
	}

	/**
	 * Copy data from a byte array into the buffer. Overwrites any existing data.
	 * @param srcData Source byte array
	 */
	public void copyOf(byte[] srcData) {
		clear();
		copyFrom(srcData, 0, 0, srcData.length);
	}

	/**
	 * Copy data from a byte array into the buffer. Overwrites any existing data.
	 * @param srcData Source byte array
	 * @param len Length of data to copy
	 */
	@SuppressWarnings("unused")
	public void copyOf(byte[] srcData, int len) {
		clear();
		copyFrom(srcData, 0, 0, len);
	}

	/**
	 * Copy data from a byte array into the buffer. Overwrites any existing data.
	 * @param srcData Source byte array
	 * @param srcOffset Source offset
	 * @param len Length of data to copy
	 */
	@SuppressWarnings("unused")
	public void copyOf(byte[] srcData, int srcOffset, int len) {
		clear();
		copyFrom(srcData, srcOffset, 0, len);
	}

	/**
	 * Copy data from another buffer into this buffer. Overwrites any existing data.
	 * @param srcBuf Source buffer
	 */
	public void copyOf(BufferExt srcBuf) {
		clear();
		copyFrom(srcBuf.getBuf(), 0, 0, srcBuf.getUsed());
	}

	/**
	 * Copy data from another buffer into this buffer. Overwrites any existing data.
	 * @param srcBuf Source buffer
	 * @param srcOffset Source offset
	 * @param len Length of data to copy
	 */
	public void copyOf(BufferExt srcBuf, int srcOffset, int len) {
		clear();
		copyFrom(srcBuf.getBuf(), srcOffset, 0, len);
	}

	/**
	 * Copy data from the buffer into a byte array.
	 * @param srcOffset Source offset
	 * @param dstData Destination byte array
	 * @param dstOffset Destination offset
	 * @param len Length of data to copy
	 */
	public void copyInto(int srcOffset, byte[] dstData, int dstOffset, int len) {
		validateArgs(used, srcOffset, dstOffset, len);
		if (dstData == null || dstOffset + len > dstData.length) {
			throw new IllegalArgumentException("Invalid destination offset / len");
		}
		System.arraycopy(buf, srcOffset, dstData, dstOffset, len);
	}

	/**
	 * Increases the internal buffer size to at least {@code newSize}.
	 * @param newSize New internal buffer size
	 */
	public void increaseSize(int newSize) {
		if (newSize < 0) {
			throw new IllegalArgumentException("Invalid buffer size");
		}
		if (newSize <= buf.length) {
			return;
		}
		byte[] oldBuf = new byte[used + 1];
		if (used > 0) {
			System.arraycopy(buf, 0, oldBuf, 0, used);
		}
		buf = new byte[newSize];
		if (used > 0) {
			System.arraycopy(oldBuf, 0, buf, 0, used);
		}
	}

	/**
	 * Append data from another buffer to this buffer.
	 * @param srcBuf Source buffer
	 */
	public void append(BufferExt srcBuf) {
		copyFrom(srcBuf.getBuf(), 0, used, srcBuf.getUsed());
	}

	/**
	 * Append data from another buffer to this buffer.
	 * @param srcBuf Source buffer
	 */
	public void append(byte[] srcBuf) {
		copyFrom(srcBuf, 0, used, srcBuf.length);
	}

	/**
	 * Append one byte to this buffer.
	 * @param value The byte to append
	 */
	public void append(byte value) {
		byte[] tmpBuf = new byte[1];
		tmpBuf[0] = value;
		copyFrom(tmpBuf, 0, used, 1);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validateArgs(int srcDataLen, int srcOffset, int dstOffset, int len) {
		if (srcOffset < 0) {
			throw new IllegalArgumentException("Invalid source offset");
		}
		if (dstOffset < 0) {
			throw new IllegalArgumentException("Invalid destination offset");
		}
		if (len < 0) {
			throw new IllegalArgumentException("Invalid length");
		}
		if (srcOffset + len > srcDataLen) {
			throw new IllegalArgumentException("Invalid source offset / len");
		}
	}

	@Override
	public BufferExt clone() {
		try {
			BufferExt clone = (BufferExt)super.clone();
			clone.buf = buf.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}

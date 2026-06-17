package org.tsitle.lib_xrtxp.common.buffers;

import org.jspecify.annotations.NonNull;

import java.util.Base64;
import java.util.HexFormat;

/**
 * BufferExt provides a resizable byte buffer with methods for copying data and accessing buffer contents.
 */
public final class BufferExt implements Cloneable {

	private byte[] buf = new byte[1024 * 64];
	private int used;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public BufferExt() { }

	public BufferExt(byte[] buf) {
		copyOf(buf);
	}

	public BufferExt(byte[] buf, int offset, int length) {
		copyOf(buf, offset, length);
	}

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
	 * Set the number of bytes currently used in the buffer.
	 * @param used Number of bytes used
	 */
	public void setUsed(int used) {
		if (used > buf.length) {
			throw new IllegalArgumentException("Used size cannot exceed buffer length");
		}
		if (used < 0) {
			throw new IllegalArgumentException("Used size cannot be negative");
		}
		this.used = used;
	}

	/**
	 * Checks if the buffer is empty.
	 * @return True if the buffer is empty, false otherwise
	 */
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
	 * Get a pointer to the internal buffer.<br />
	 * <b>Note:</b> Use in combination with {@code getUsed()} - not {@code baPtr.length}!
	 * @return Pointer to the internal buffer
	 */
	public byte[] getBaPtr() {
		return buf;
	}

	/**
	 * Get a byte from the buffer.
	 * @param index Byte index
	 * @return Byte at the given index
	 */
	public byte get(int index) {
		if (index < 0) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds");
		}
		if (index >= used) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds for used buffer size " + used);
		}
		return buf[index];
	}

	/**
	 * Set a byte in the buffer.
	 * @param index Byte index
	 * @param value Byte value to set
	 */
	public void set(int index, byte value) {
		if (index < 0) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds");
		}
		if (index >= used) {
			throw new IndexOutOfBoundsException("Index " + index + " is out of bounds for used buffer size " + used);
		}
		buf[index] = value;
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
	public void copyFrom(@NonNull BufferExt srcBuf, int srcOffset, int dstOffset, int len) {
		copyFrom(srcBuf.getBaPtr(), srcOffset, dstOffset, len);
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
	 * @param srcOffset Source offset
	 * @param len Length of data to copy
	 */
	public void copyOf(byte[] srcData, int srcOffset, int len) {
		clear();
		copyFrom(srcData, srcOffset, 0, len);
	}

	/**
	 * Copy data from another buffer into this buffer. Overwrites any existing data.
	 * @param srcBuf Source buffer
	 */
	public void copyOf(@NonNull BufferExt srcBuf) {
		clear();
		copyFrom(srcBuf.getBaPtr(), 0, 0, srcBuf.getUsed());
	}

	/**
	 * Copy data from another buffer into this buffer. Overwrites any existing data.
	 * @param srcBuf Source buffer
	 * @param srcOffset Source offset
	 * @param len Length of data to copy
	 */
	public void copyOf(@NonNull BufferExt srcBuf, int srcOffset, int len) {
		clear();
		copyFrom(srcBuf.getBaPtr(), srcOffset, 0, len);
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
	 * Increase the internal buffer size to at least {@code newSize}.
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
	public void append(@NonNull BufferExt srcBuf) {
		copyFrom(srcBuf.getBaPtr(), 0, used, srcBuf.getUsed());
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

	/**
	 * Create a new buffer from a hex-encoded string.
	 * @param hexString Hex-encoded string
	 * @return New buffer
	 */
	static public @NonNull BufferExt decodeHexString(@NonNull String hexString) {
		BufferExt resObj = new BufferExt();
		if (hexString.startsWith("0x")) {
			hexString = hexString.substring(2);
		}
		hexString = hexString
				.replace(" ", "")
				.replace(":", "")
				.replace("|", "")
				.replace("\n", "")
				.replace("\r", "")
				.strip();
		if (! hexString.isBlank()) {
			try {
				byte[] tmpBa = HexFormat.of().parseHex(hexString);  // throws IllegalArgumentException or NumberFormatException
				resObj.copyOf(tmpBa);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid hex string");
			}
		}
		return resObj;
	}

	/**
	 * Create a new buffer from a Base64-encoded string.
	 * @param base64String Base64-encoded string
	 * @return New buffer
	 */
	@SuppressWarnings("unused")
	static public @NonNull BufferExt decodeBase64String(@NonNull String base64String) {
		if (base64String.startsWith("b64:")) {
			base64String = base64String.substring(4);
		}
		BufferExt resObj = new BufferExt();
		if (! base64String.isBlank()) {
			byte[] tmpBa;
			try {
				tmpBa = Base64.getDecoder().decode(base64String);  // throws IllegalArgumentException
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid base64 encoding");
			}
			resObj.copyOf(tmpBa);
		}
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [used=" + used + ", buf=" + toHexString(true) + "]";
	}

	/**
	 * Convert the buffer to a hex string.
	 * @return Hex string representation
	 */
	public @NonNull String toHexString() {
		return toHexString(false);
	}

	/**
	 * Convert the buffer to a hex string.
	 * @param withPrefix If true, the string will start with "0x"
	 * @return Hex string representation
	 */
	public @NonNull String toHexString(boolean withPrefix) {
		if (used == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		if (withPrefix) {
			sb.append("0x");
		}
		for (int i = 0; i < used; i++) {
			sb.append(String.format("%02X", buf[i]));
		}
		return sb.toString();
	}

	/**
	 * Convert the buffer to a Base64-encoded string.
	 * @return Base64-encoded string
	 */
	public @NonNull String toBase64String() {
		return toBase64String(false);
	}

	/**
	 * Convert the buffer to a Base64-encoded string.
	 * @param withPrefix If true, the string will start with "b64:"
	 * @return Base64-encoded string
	 */
	public @NonNull String toBase64String(boolean withPrefix) {
		if (used == 0) {
			return "";
		}
		byte[] tmpBa = new byte[used];
		copyInto(0, tmpBa, 0, used);

		return (withPrefix ? "b64:" : "") + Base64.getEncoder().encodeToString(tmpBa);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj instanceof BufferExt other) {
			if (used != other.used) {
				return false;
			}
			for (int i = 0; i < used; i++) {
				if (buf[i] != other.buf[i]) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		int result = Integer.hashCode(used);
		for (int i = 0; i < used; i++) {
			result = 31 * result + Byte.hashCode(buf[i]);
		}
		return result;
	}

	@Override
	public @NonNull BufferExt clone() {
		try {
			BufferExt clone = (BufferExt)super.clone();
			clone.buf = buf.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
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

}

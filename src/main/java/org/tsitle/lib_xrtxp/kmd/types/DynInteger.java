package org.tsitle.lib_xrtxp.kmd.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Container for integer values with dynamic size.
 */
public final class DynInteger implements Cloneable {

	private final long value;
	private final int sizeBytes;

	/**
	 * Constructor.
	 * @param value Value of the integer
	 * @param sizeBytes Size of the integer in bytes (0/1/2/4/8)
	 */
	private DynInteger(long value, int sizeBytes) {
		this.value = switch (sizeBytes) {
				case 0 -> 0L;
				case 1 -> (value % 256L);
				case 2 -> (value % 65536L);
				case 4 -> (value % 4294967296L);
				case 8 -> value;
				default -> throw new IllegalArgumentException("sizeBytes must be 0/1/2/4/8");
			};
		this.sizeBytes = sizeBytes;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull DynInteger ofEmpty() {
		return new DynInteger(0L, 0);
	}

	public static @NonNull DynInteger ofAutoSized(long value) {
		if (value >= 0L && value < 256L) {
			return new DynInteger(value, 1);
		}
		if (value >= 0L && value < 65536L) {
			return new DynInteger(value, 2);
		}
		if (value >= 0L && value < 4294967296L) {
			return new DynInteger(value, 4);
		}
		return new DynInteger(value, 8);
	}

	public static @NonNull DynInteger ofBufferBigEndian(@NonNull BufferExt buffer) throws IllegalArgumentException {
		if (buffer.isEmpty()) {
			return DynInteger.ofEmpty();
		}
		ByteBuffer tmpBb = ByteBuffer
				.wrap(buffer.getBaPtr(), 0, buffer.getUsed())
				.order(ByteOrder.BIG_ENDIAN);
		return ofByteBuffer(tmpBb, buffer.getUsed());
	}

	public static @NonNull DynInteger ofByteBuffer(@NonNull ByteBuffer input, int sizeBytes) throws IllegalArgumentException {
		try {
			return switch (sizeBytes) {
					case 0 -> new DynInteger(0, 0);
					case 1 -> new DynInteger(Byte.toUnsignedLong(input.get()), 1);
					case 2 -> new DynInteger(Short.toUnsignedLong(input.getShort()), 2);
					case 4 -> new DynInteger(Integer.toUnsignedLong(input.getInt()), 4);
					case 8 -> new DynInteger(input.getLong(), 8);
					default -> throw new IllegalArgumentException("sizeBytes must be 0/1/2/4/8");
				};
		} catch (BufferOverflowException ignored) {
			throw new IllegalArgumentException("Buffer too small");
		}
	}

	public static @NonNull DynInteger of(long value, int sizeBytes) {
		return new DynInteger(sizeBytes > 0 ? value : 0L, sizeBytes);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public long getValue() {
		return value;
	}

	public int getSizeBytes() {
		return sizeBytes;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull BufferExt toBufferExtBigEndian() {
		BufferExt resBe = new BufferExt();
		if (! isEmpty()) {
			resBe.setUsed(sizeBytes);
			ByteBuffer tmpBb = ByteBuffer.wrap(resBe.getBaPtr()).order(ByteOrder.BIG_ENDIAN);
			writeToByteBuffer(tmpBb);
		}
		return resBe;
	}

	public void writeToByteBuffer(@NonNull ByteBuffer output) {
		switch (sizeBytes) {
			case 1: output.put((byte)value); break;
			case 2: output.putShort((short)value); break;
			case 4: output.putInt((int)value); break;
			case 8: output.putLong(value); break;
			default: break;
		}
	}

	public boolean isEmpty() {
		return (sizeBytes == 0);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public DynInteger clone() {
		try {
			return (DynInteger)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		DynInteger that = (DynInteger)o;
		return (value == that.value && sizeBytes == that.sizeBytes);
	}

	public boolean equalsBufferBigEndian(@NonNull BufferExt buffer) {
		BufferExt thisBe = toBufferExtBigEndian();
		return thisBe.equals(buffer);
	}

	@Override
	public int hashCode() {
		return Objects.hash(value, sizeBytes);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				(isEmpty() ? "empty" : String.format("value=%s, sizeBytes=%d", Long.toUnsignedString(value), sizeBytes)) +
				"]";
	}

}

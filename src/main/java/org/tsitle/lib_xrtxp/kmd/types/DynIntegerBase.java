package org.tsitle.lib_xrtxp.kmd.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Base class for containers for integer values with dynamic size.
 */
public abstract class DynIntegerBase implements Cloneable {

	protected final long value;
	protected final int sizeBytes;

	/**
	 * Constructor.
	 *
	 * @param value     Value of the integer
	 * @param sizeBytes Size of the integer in bytes (0/1/2/4/8)
	 */
	protected DynIntegerBase(long value, int sizeBytes) {
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

	public Optional<Long> getValue() {
		if (isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(value);
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

	public abstract boolean isEmpty();

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public DynIntegerBase clone() {
		try {
			return (DynIntegerBase)super.clone();
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
		DynIntegerBase that = (DynIntegerBase)o;
		if (isEmpty() && that.isEmpty()) {
			return true;
		}
		return (value == that.value && sizeBytes == that.sizeBytes);
	}

	public abstract boolean equalsBufferBigEndian(@NonNull BufferExt buffer);

	@Override
	public int hashCode() {
		return Objects.hash(value, sizeBytes);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected static <T> T ofValueAndSize(long value, int sizeBytes, @NonNull BiFunction<Long, Integer, T> ctor) {
		return ctor.apply(sizeBytes > 0 ? value : 0L, sizeBytes);
	}

	protected static <T> T createFromBufferExt(@NonNull BufferExt buffer, @NonNull BiFunction<Long, Integer, T> ctor) {
		if (buffer.isEmpty()) {
			return ctor.apply(0L, 0);
		}
		ByteBuffer tmpBb = ByteBuffer
				.wrap(buffer.getBaPtr(), 0, buffer.getUsed())
				.order(ByteOrder.BIG_ENDIAN);
		return createFromByteBuffer(tmpBb, buffer.getUsed(), ctor);
	}

	protected static <T> T createFromByteBuffer(@NonNull ByteBuffer input, int sizeBytes, @NonNull BiFunction<Long, Integer, T> ctor) {
		try {
			return switch (sizeBytes) {
					case 0 -> ctor.apply(0L, 0);
					case 1 -> ctor.apply(Byte.toUnsignedLong(input.get()), 1);
					case 2 -> ctor.apply(Short.toUnsignedLong(input.getShort()), 2);
					case 4 -> ctor.apply(Integer.toUnsignedLong(input.getInt()), 4);
					case 8 -> ctor.apply(input.getLong(), 8);
					default -> throw new IllegalArgumentException("sizeBytes must be 0/1/2/4/8");
				};
		} catch (BufferOverflowException ignored) {
			throw new IllegalArgumentException("Buffer too small");
		}
	}

	protected static int autoSizeBytesUnsigned(long value, boolean zeroIsEmpty) {
		if (zeroIsEmpty && value == 0L) {
			return 0;
		}
		if (value >= 0L && value < 256L) {
			return 1;
		}
		if (value >= 0L && value < 65536L) {
			return 2;
		}
		if (value >= 0L && value < 4294967296L) {
			return 4;
		}
		return 8;
	}

}

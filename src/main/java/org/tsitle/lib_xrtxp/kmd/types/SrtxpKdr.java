package org.tsitle.lib_xrtxp.kmd.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

/**
 * Container for a Key Derivation Rate.
 */
public final class SrtxpKdr extends DynIntegerBase implements Cloneable {

	private SrtxpKdr(long value, int sizeBytes) {
		// we want the KDR to be 'empty' if the value is zero
		super(value, value == 0L ? 0 : sizeBytes);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull SrtxpKdr ofEmpty() {
		return new SrtxpKdr(0L, 0);
	}

	public static @NonNull SrtxpKdr ofAutoSized(long value) {
		return new SrtxpKdr(value, autoSizeBytesUnsigned(value, true));
	}

	public static @NonNull SrtxpKdr ofBufferBigEndian(@NonNull BufferExt buffer) throws IllegalArgumentException {
		return createFromBufferExt(buffer, SrtxpKdr::new);
	}

	public static @NonNull SrtxpKdr of(long value, int sizeBytes) {
		return ofValueAndSize(value, sizeBytes, SrtxpKdr::new);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equalsBufferBigEndian(@NonNull BufferExt buffer) {
		SrtxpKdr other = ofBufferBigEndian(buffer);
		return this.equals(other);
	}

	@Override
	public boolean isEmpty() {
		// the KDR needs to be 'empty' if the value is zero
		return (value == 0L || sizeBytes == 0);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public SrtxpKdr clone() {
		return (SrtxpKdr)super.clone();
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				(isEmpty() ? "empty" : String.format("value=%s, sizeBytes=%d", Long.toUnsignedString(value), sizeBytes)) +
				"]";
	}

}

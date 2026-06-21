package org.tsitle.lib_xrtxp.kmd.types;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

/**
 * Container for a Master Key Identifier.
 */
public final class SrtxpMki extends DynIntegerBase implements Cloneable {

	private SrtxpMki(long value, int sizeBytes) {
		super(value, sizeBytes);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull SrtxpMki ofEmpty() {
		return new SrtxpMki(0L, 0);
	}

	public static @NonNull SrtxpMki ofAutoSized(long value) {
		return new SrtxpMki(value, autoSizeBytesUnsigned(value, false));
	}

	public static @NonNull SrtxpMki ofBufferBigEndian(@NonNull BufferExt buffer) throws IllegalArgumentException {
		return createFromBufferExt(buffer, SrtxpMki::new);
	}

	public static @NonNull SrtxpMki of(long value, int sizeBytes) {
		return ofValueAndSize(value, sizeBytes, SrtxpMki::new);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equalsBufferBigEndian(@NonNull BufferExt buffer) {
		SrtxpMki other = ofBufferBigEndian(buffer);
		return this.equals(other);
	}

	@Override
	public boolean isEmpty() {
		return (sizeBytes == 0);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public SrtxpMki clone() {
		return (SrtxpMki)super.clone();
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				(isEmpty() ? "empty" : String.format("value=%s, sizeBytes=%d", Long.toUnsignedString(value), sizeBytes)) +
				"]";
	}

}

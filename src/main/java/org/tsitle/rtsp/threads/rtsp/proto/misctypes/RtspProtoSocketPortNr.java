package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Objects;
import java.util.Optional;

public final class RtspProtoSocketPortNr implements Cloneable {

	private boolean isWriteProtected = false;

	/** TCP/UDP port */
	private int portNr = -1;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Integer> getPort16bit() {
		return (portNr < 1 ? Optional.empty() : Optional.of(portNr));
	}
	public void setPort16bit(int value16bit) throws RtspProtoNumberRangeException {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		validatePos16bit(value16bit);
		this.portNr = value16bit;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (portNr < 1);
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		portNr = -1;
	}

	public void copyFrom(@NonNull RtspProtoSocketPortNr other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		portNr = other.portNr;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoSocketPortNr that)) {
			return false;
		}
		return (portNr == that.portNr);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(portNr);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"portNr=" + (portNr >= 1 ? Integer.toUnsignedString(portNr) : "unset") +
				"]";
	}

	@Override
	public RtspProtoSocketPortNr clone() {
		try {
			return (RtspProtoSocketPortNr)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validatePos16bit(int value) throws RtspProtoNumberRangeException {
		final String FNC_NAME = RtspProtoSocketPortNr.class.getSimpleName() + ".validatePos16bit()";

		if (value < 1 || value > 65535) {
			throw new RtspProtoNumberRangeException(FNC_NAME + ": value must be between 1 and 65535 (is=" + value + ")");
		}
	}

}

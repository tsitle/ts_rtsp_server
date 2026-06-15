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
	public void setPort16bit(int portNr) throws RtspProtoNumberRangeException {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		validatePortNumber(portNr);
		this.portNr = portNr;
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
				"portNr=" + portToStr() +
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

	private static void validatePortNumber(int portNr) throws RtspProtoNumberRangeException {
		if (portNr < 1 || portNr > 65535) {
			throw new RtspProtoNumberRangeException("Socket Port must be between 1 and 65535, got: " + portNr);
		}
	}

	private @NonNull String portToStr() {
		return (portNr >= 1 ? Integer.toUnsignedString(portNr) : "unset");
	}

}

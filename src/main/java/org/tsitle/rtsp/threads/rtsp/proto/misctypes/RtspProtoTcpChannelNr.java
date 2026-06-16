package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Objects;
import java.util.Optional;

/**
 * Container for a TCP channel number.
 */
public final class RtspProtoTcpChannelNr implements Cloneable {

	private boolean isWriteProtected = false;

	/** TCP channel */
	private int channelNr = -1;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoTcpChannelNr ofEmpty() {
		return new RtspProtoTcpChannelNr();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Integer> getChannel8bit() {
		return (channelNr < 0 ? Optional.empty() : Optional.of(channelNr));
	}
	public void setChannel8bit(int value8bit) throws RtspProtoNumberRangeException {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		validateNonNeg8bit(value8bit);
		this.channelNr = value8bit;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (channelNr < 0);
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		channelNr = -1;
	}

	public void copyFrom(@NonNull RtspProtoTcpChannelNr other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		channelNr = other.channelNr;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoTcpChannelNr that)) {
			return false;
		}
		return (channelNr == that.channelNr);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(channelNr);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"channelNr=" + (channelNr >= 0 ? Integer.toUnsignedString(channelNr) : "unset") +
				"]";
	}

	@Override
	public RtspProtoTcpChannelNr clone() {
		try {
			return (RtspProtoTcpChannelNr)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validateNonNeg8bit(int value) throws RtspProtoNumberRangeException {
		final String FNC_NAME = RtspProtoTcpChannelNr.class.getSimpleName() + ".validateNonNeg8bit()";

		if (value < 0 || value > 255) {
			throw new RtspProtoNumberRangeException(FNC_NAME + ": value must be between 0 and 255 (is=" + value + ")");
		}
	}

}

package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;

import java.util.Objects;
import java.util.Optional;

public final class RtspProtoTcpChannelNr implements Cloneable {

	private boolean isWriteProtected = false;

	/** TCP channel */
	private int channelNr = -1;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Integer> getChannel8bit() {
		return (channelNr < 0 ? Optional.empty() : Optional.of(channelNr));
	}
	public void setChannel8bit(int channelNr) throws RtspNumberRangeException {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		validateChannelNumber(channelNr);
		this.channelNr = channelNr;
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
				"channelNr=" + channelToStr() +
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

	private static void validateChannelNumber(int channelNr) throws RtspNumberRangeException {
		if (channelNr < 0 || channelNr > 255) {
			throw new RtspNumberRangeException("Channel must be between 0 and 255, got: " + channelNr);
		}
	}

	private @NonNull String channelToStr() {
		return (channelNr >= 0 ? Integer.toUnsignedString(channelNr) : "unset");
	}

}

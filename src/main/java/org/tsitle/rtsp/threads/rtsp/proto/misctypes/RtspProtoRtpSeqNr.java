package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Objects;
import java.util.Optional;

public final class RtspProtoRtpSeqNr implements Cloneable {

	private boolean isWriteProtected = false;

	/** RTP Sequence Number */
	private int seqNr = -1;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoRtpSeqNr ofEmpty() {
		return new RtspProtoRtpSeqNr();
	}

	public static RtspProtoRtpSeqNr of(int value16bit) throws RtspProtoNumberRangeException {
		RtspProtoRtpSeqNr resObj = new RtspProtoRtpSeqNr();
		resObj.setSeqNr16bit(value16bit);
		return resObj;
	}

	public static RtspProtoRtpSeqNr withOverflow(int value32bit) {
		RtspProtoRtpSeqNr resObj = new RtspProtoRtpSeqNr();
		short tmpShort = (short)value32bit;
		resObj.seqNr = Short.toUnsignedInt(tmpShort);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<Integer> getSeqNr16bit() {
		return (seqNr < 0 ? Optional.empty() : Optional.of(seqNr));
	}
	public void setSeqNr16bit(int value16bit) throws RtspProtoNumberRangeException {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		validateNonNeg16bit(value16bit);
		this.seqNr = value16bit;
	}
	public void increment() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		try {
			setSeqNr16bit(seqNr + 1);
		} catch (RtspProtoNumberRangeException e) {
			// overflow
			seqNr = 0;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (seqNr < 0);
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		seqNr = -1;
	}

	public void copyFrom(@NonNull RtspProtoRtpSeqNr other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		seqNr = other.seqNr;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (! (o instanceof RtspProtoRtpSeqNr that)) {
			return false;
		}
		return (seqNr == that.seqNr);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(seqNr);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"seqNr=" + (seqNr >= 0 ? Integer.toUnsignedString(seqNr) : "unset") +
				"]";
	}

	@Override
	public RtspProtoRtpSeqNr clone() {
		try {
			return (RtspProtoRtpSeqNr)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void validateNonNeg16bit(int value) throws RtspProtoNumberRangeException {
		final String FNC_NAME = RtspProtoRtpSeqNr.class.getSimpleName() + ".validateNonNeg16bit()";

		if (value < 0 || value > 65535) {
			throw new RtspProtoNumberRangeException(FNC_NAME + ": value must be between 0 and 65535 (is=" + value + ")");
		}
	}

}

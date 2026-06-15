package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Optional;

public class RtspProtoHeaderTypeCseq {

	private long cseqNr32bit = -1L;

	public void setCseqNr32bit(long cseqNr32bit) throws RtspProtoNumberRangeException {
		if (cseqNr32bit < 0L || cseqNr32bit > 0xFFFFFFFFL) {
			throw new RtspProtoNumberRangeException("cseqNr32bit must be non-negative and within 32-bit range");
		}
		this.cseqNr32bit = cseqNr32bit;
	}

	public Optional<Integer> getCseqNr32bit() {
		return (cseqNr32bit < 0L ? Optional.empty() : Optional.of((int)cseqNr32bit));
	}

	@Override
	public @NonNull String toString() {
		return "[" +
				optionalIntToStr("cseqNr", getCseqNr32bit()) +
				"]";
	}

	@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
	private static @NonNull String optionalIntToStr(@NonNull String desc, @NonNull Optional<Integer> value) {
		return desc + "=" + value.map(Integer::toUnsignedString).orElse("unset");
	}

}

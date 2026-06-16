package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoNumberRangeException;

import java.util.Optional;

public final class RtspProtoHeaderTypeContLen {

	private long contentLen32bit = -1L;

	public void setContentLen32bit(long contentLen32bit) throws RtspProtoNumberRangeException {
		if (contentLen32bit < 0L || contentLen32bit > 0xFFFFFFFFL) {
			throw new RtspProtoNumberRangeException("contentLen32bit must be non-negative and within 32-bit range");
		}
		this.contentLen32bit = contentLen32bit;
	}

	public Optional<Integer> getContentLen32bit() {
		return (contentLen32bit < 0L ? Optional.empty() : Optional.of((int)contentLen32bit));
	}

	@Override
	public @NonNull String toString() {
		return "[" +
				optionalIntToStr("contentLen", getContentLen32bit()) +
				"]";
	}

	@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
	private static @NonNull String optionalIntToStr(@NonNull String desc, @NonNull Optional<Integer> value) {
		return desc + "=" + value.map(Integer::toUnsignedString).orElse("unset");
	}

}

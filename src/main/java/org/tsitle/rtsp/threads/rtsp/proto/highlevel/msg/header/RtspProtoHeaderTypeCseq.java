package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoCseqNr;

import java.util.Optional;

public class RtspProtoHeaderTypeCseq {

	public RtspProtoCseqNr cseqNr = new RtspProtoCseqNr();

	@Override
	public @NonNull String toString() {
		return "[" +
				optionalLongToStr("cseqNr", cseqNr.getCseq32bit()) +
				"]";
	}

	@SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "SameParameterValue"})
	private static @NonNull String optionalLongToStr(@NonNull String desc, @NonNull Optional<Long> value) {
		return desc + "=" + value.map(Long::toUnsignedString).orElse("unset");
	}

}

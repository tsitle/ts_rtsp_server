package org.tsitle.lib.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoContentLength;

public final class RtspProtoHeaderTypeContLen {

	public final @NonNull RtspProtoContentLength contentLen = RtspProtoContentLength.ofEmpty();

	@Override
	public @NonNull String toString() {
		return "[" +
				"contentLen=" + (contentLen.isEmpty() ? "unset" : Long.toUnsignedString(contentLen.getLen32bit().orElseThrow())) +
				"]";
	}

}

package org.tsitle.lib.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.lowlevel.RtspMimeType;

public final class RtspProtoHeaderTypeContType {

	public @NonNull RtspMimeType contentType = RtspMimeType.NONE;

	@Override
	public @NonNull String toString() {
		return "[contentType=" + contentType + "]";
	}

}

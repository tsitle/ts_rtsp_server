package org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspMimeType;

public final class RtspProtoHeaderTypeContType {

	public @NonNull RtspMimeType contentType = RtspMimeType.NONE;

	@Override
	public @NonNull String toString() {
		return "[contentType=" + contentType + "]";
	}

}

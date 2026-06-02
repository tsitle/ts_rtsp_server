package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMimeType;

public class RtspProtoLowHeaderTypeContType {

	public @NonNull RtspMimeType contentType = RtspMimeType.NONE;

	@Override
	public @NonNull String toString() {
		return "[contentType=" + contentType + "]";
	}

}

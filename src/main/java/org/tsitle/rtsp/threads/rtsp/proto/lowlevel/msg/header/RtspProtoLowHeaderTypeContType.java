package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeContType {

	public enum ContentType {
		NONE,
		SDP
	}

	public @NonNull ContentType contentType = ContentType.NONE;

	@Override
	public @NonNull String toString() {
		return "[contentType=" + contentType + "]";
	}

}

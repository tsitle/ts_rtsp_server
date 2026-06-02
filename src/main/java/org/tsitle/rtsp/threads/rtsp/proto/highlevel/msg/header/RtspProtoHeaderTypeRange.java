package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoHeaderTypeRange {

	public @NonNull String rangeStr = "";

	@Override
	public @NonNull String toString() {
		return "[rangeStr='" + rangeStr + "']";
	}

}

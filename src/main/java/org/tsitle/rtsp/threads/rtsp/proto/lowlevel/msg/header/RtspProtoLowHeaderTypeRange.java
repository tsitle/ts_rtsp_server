package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeRange {

	public @NonNull String rangeStr = "";

	@Override
	public @NonNull String toString() {
		return "[rangeStr='" + rangeStr + "']";
	}

}

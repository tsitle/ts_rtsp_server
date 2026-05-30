package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeRange {

	public @NonNull String rangeStr = "";

	@Override
	public @NonNull String toString() {
		return "[rangeStr='" + rangeStr + "']";
	}

}

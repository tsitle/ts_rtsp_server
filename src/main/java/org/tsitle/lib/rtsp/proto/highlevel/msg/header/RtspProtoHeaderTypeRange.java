package org.tsitle.lib.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public final class RtspProtoHeaderTypeRange {

	public @NonNull String rangeStr = "";

	@Override
	public @NonNull String toString() {
		return "[rangeStr='" + rangeStr + "']";
	}

}

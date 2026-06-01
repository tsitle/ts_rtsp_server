package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeContBase {

	public @NonNull String contentBaseStr = "";

	@Override
	public @NonNull String toString() {
		return "[contentBaseStr='" + contentBaseStr + "']";
	}

}

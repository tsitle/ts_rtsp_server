package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeServer {

	public @NonNull String serverStr = "";

	@Override
	public @NonNull String toString() {
		return "[serverStr='" + serverStr + "']";
	}

}

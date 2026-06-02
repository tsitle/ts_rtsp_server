package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoHeaderTypeServer {

	public @NonNull String serverStr = "";

	@Override
	public @NonNull String toString() {
		return "[serverStr='" + serverStr + "']";
	}

}

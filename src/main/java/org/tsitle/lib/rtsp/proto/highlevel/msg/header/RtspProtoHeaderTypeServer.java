package org.tsitle.lib.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public final class RtspProtoHeaderTypeServer {

	public @NonNull String serverStr = "";

	@Override
	public @NonNull String toString() {
		return "[serverStr='" + serverStr + "']";
	}

}

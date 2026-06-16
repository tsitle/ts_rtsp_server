package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public final class RtspProtoHeaderTypeUa {

	public @NonNull String userAgentStr = "";

	@Override
	public @NonNull String toString() {
		return "[userAgentStr='" + userAgentStr + "']";
	}

}

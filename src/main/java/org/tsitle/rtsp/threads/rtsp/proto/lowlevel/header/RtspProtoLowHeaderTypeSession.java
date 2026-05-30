package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeSession {

	public @NonNull String sessionIdStr = "";

	@Override
	public @NonNull String toString() {
		return "[sessionIdStr='" + sessionIdStr + "']";
	}

}

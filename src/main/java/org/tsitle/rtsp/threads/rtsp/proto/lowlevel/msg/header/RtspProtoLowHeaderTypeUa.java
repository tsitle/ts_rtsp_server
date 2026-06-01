package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeUa {

	public @NonNull String userAgentStr = "";

	@Override
	public @NonNull String toString() {
		return "[userAgentStr='" + userAgentStr + "']";
	}

}

package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeUa {

	public @NonNull String userAgentStr = "";

	@Override
	public @NonNull String toString() {
		return "[userAgentStr='" + userAgentStr + "']";
	}

}

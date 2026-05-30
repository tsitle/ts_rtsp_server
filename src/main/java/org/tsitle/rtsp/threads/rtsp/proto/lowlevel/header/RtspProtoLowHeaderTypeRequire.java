package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeRequire {

	public @NonNull String requireStr = "";

	@Override
	public @NonNull String toString() {
		return "[requireStr='" + requireStr + "']";
	}

}

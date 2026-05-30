package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeAccept {

	public @NonNull String acceptStr = "";

	@Override
	public @NonNull String toString() {
		return "[acceptStr='" + acceptStr + "']";
	}

}

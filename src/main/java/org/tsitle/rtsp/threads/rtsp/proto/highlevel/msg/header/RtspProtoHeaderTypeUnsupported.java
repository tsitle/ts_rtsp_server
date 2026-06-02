package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoHeaderTypeUnsupported {

	public @NonNull String unsupportedOptionStr = "";

	@Override
	public @NonNull String toString() {
		return "[unsupportedOptionStr='" + unsupportedOptionStr + "']";
	}

}

package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeUnsupported {

	public @NonNull String unsupportedOptionStr = "";

	@Override
	public @NonNull String toString() {
		return "[unsupportedOptionStr='" + unsupportedOptionStr + "']";
	}

}

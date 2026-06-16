package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public final class RtspProtoHeaderTypeUnsupported {

	public @NonNull String unsupportedFeatureStr = "";

	@Override
	public @NonNull String toString() {
		return "[unsupportedFeatureStr='" + unsupportedFeatureStr + "']";
	}

}

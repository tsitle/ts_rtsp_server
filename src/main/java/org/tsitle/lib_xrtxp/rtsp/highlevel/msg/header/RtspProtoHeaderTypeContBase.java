package org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public final class RtspProtoHeaderTypeContBase {

	public @NonNull String contentBaseStr = "";

	@Override
	public @NonNull String toString() {
		return "[contentBaseStr='" + contentBaseStr + "']";
	}

}

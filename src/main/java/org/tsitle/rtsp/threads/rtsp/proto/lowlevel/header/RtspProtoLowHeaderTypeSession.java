package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeSession {

	public @NonNull String sessionIdStr = "";
	/** Session timeout in seconds. -1 means no timeout. */
	public int timeout = -1;

	@Override
	public @NonNull String toString() {
		return "[sessionIdStr='" + sessionIdStr + "', timeout=" + (timeout >= 0 ? timeout : "none") + "]";
	}

}

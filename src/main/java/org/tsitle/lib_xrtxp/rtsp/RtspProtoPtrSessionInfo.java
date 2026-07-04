package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;

/**
 * Container for a pointer to a {@link RtspProtoSessionInfo}.
 */
public final class RtspProtoPtrSessionInfo {

	public @NonNull RtspProtoSessionInfo ptr;

	public RtspProtoPtrSessionInfo(@NonNull RtspProtoSessionInfo ptr) {
		this.ptr = ptr;
	}

}

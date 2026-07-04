package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;

/**
 * Container for a pointer to a {@link RtspProtoSessionInfo}.
 */
public final class RtspProtoPtrSessionInfo {

	public @NonNull RtspProtoSessionInfo ptr;

	private RtspProtoPtrSessionInfo(@NonNull RtspProtoSessionInfo ptr) {
		this.ptr = ptr;
	}

	public static @NonNull RtspProtoPtrSessionInfo ofNewSi() {
		return new RtspProtoPtrSessionInfo(new RtspProtoSessionInfo());
	}

	public static @NonNull RtspProtoPtrSessionInfo ofPointer(@NonNull RtspProtoSessionInfo ptrToObj) {
		return new RtspProtoPtrSessionInfo(ptrToObj);
	}

}

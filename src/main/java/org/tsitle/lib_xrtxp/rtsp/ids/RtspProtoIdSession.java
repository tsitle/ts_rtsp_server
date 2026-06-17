package org.tsitle.lib_xrtxp.rtsp.ids;

import org.jspecify.annotations.NonNull;

/**
 * Session ID
 */
public final class RtspProtoIdSession extends RtspProtoBaseIdString implements Cloneable {

	private RtspProtoIdSession() {
		super();
	}

	private RtspProtoIdSession(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdSession ofEmpty() {
		return new RtspProtoIdSession();
	}

	public static RtspProtoIdSession of(@NonNull String idStr) {
		return new RtspProtoIdSession(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdSession clone() {
		return (RtspProtoIdSession)super.clone();
	}

}

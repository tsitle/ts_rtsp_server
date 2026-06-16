package org.tsitle.rtsp.threads.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;

/**
 * Session ID
 */
public final class RtspProtoIdSession extends RtspProtoBaseIdString implements Cloneable {

	public RtspProtoIdSession() {
		super();
	}

	public RtspProtoIdSession(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdSession clone() {
		return (RtspProtoIdSession)super.clone();
	}

}

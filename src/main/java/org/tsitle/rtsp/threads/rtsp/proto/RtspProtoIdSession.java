package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;

public final class RtspProtoIdSession extends RtspProtoIdBase {

	public RtspProtoIdSession() {
		super();
	}

	public RtspProtoIdSession(@NonNull String sessionId) {
		super(sessionId);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdSession clone() {
		return (RtspProtoIdSession)super.clone();
	}

}

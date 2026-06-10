package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;

public final class RtspProtoIdInputSource extends RtspProtoIdBase implements Cloneable {

	public RtspProtoIdInputSource() {
		super();
	}

	public RtspProtoIdInputSource(@NonNull String sessionId) {
		super(sessionId);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdInputSource clone() {
		return (RtspProtoIdInputSource)super.clone();
	}

}

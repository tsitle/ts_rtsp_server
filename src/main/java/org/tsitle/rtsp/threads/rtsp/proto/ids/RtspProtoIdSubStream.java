package org.tsitle.rtsp.threads.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;

public final class RtspProtoIdSubStream extends RtspProtoIdBase implements Cloneable {

	public RtspProtoIdSubStream() {
		super();
	}

	public RtspProtoIdSubStream(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdSubStream clone() {
		return (RtspProtoIdSubStream)super.clone();
	}

}

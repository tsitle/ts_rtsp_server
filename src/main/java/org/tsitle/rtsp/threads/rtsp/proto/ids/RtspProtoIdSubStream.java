package org.tsitle.rtsp.threads.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;

/**
 * Sub-Stream ID
 */
public final class RtspProtoIdSubStream extends RtspProtoBaseIdString implements Cloneable {

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

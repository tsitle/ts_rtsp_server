package org.tsitle.rtsp.threads.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;

public final class RtspProtoIdStreamSource extends RtspProtoIdBase implements Cloneable {

	public RtspProtoIdStreamSource() {
		super();
	}

	public RtspProtoIdStreamSource(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdStreamSource clone() {
		return (RtspProtoIdStreamSource)super.clone();
	}

}

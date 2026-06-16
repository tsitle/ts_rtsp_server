package org.tsitle.rtsp.threads.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;

/**
 * Input Source ID
 */
public final class RtspProtoIdInputSource extends RtspProtoBaseIdString implements Cloneable {

	public RtspProtoIdInputSource() {
		super();
	}

	public RtspProtoIdInputSource(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdInputSource clone() {
		return (RtspProtoIdInputSource)super.clone();
	}

}

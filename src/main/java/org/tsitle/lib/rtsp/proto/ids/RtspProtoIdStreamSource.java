package org.tsitle.lib.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;

/**
 * Stream Source ID
 */
public final class RtspProtoIdStreamSource extends RtspProtoBaseIdString implements Cloneable {

	private RtspProtoIdStreamSource() {
		super();
	}

	private RtspProtoIdStreamSource(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdStreamSource ofEmpty() {
		return new RtspProtoIdStreamSource();
	}

	public static RtspProtoIdStreamSource of(@NonNull String idStr) {
		return new RtspProtoIdStreamSource(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdStreamSource clone() {
		return (RtspProtoIdStreamSource)super.clone();
	}

}

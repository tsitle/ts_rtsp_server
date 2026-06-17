package org.tsitle.lib.rtsp.proto.ids;

import org.jspecify.annotations.NonNull;

/**
 * Input Source ID
 */
public final class RtspProtoIdInputSource extends RtspProtoBaseIdString implements Cloneable {

	private RtspProtoIdInputSource() {
		super();
	}

	private RtspProtoIdInputSource(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdInputSource ofEmpty() {
		return new RtspProtoIdInputSource();
	}

	public static RtspProtoIdInputSource of(@NonNull String idStr) {
		return new RtspProtoIdInputSource(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdInputSource clone() {
		return (RtspProtoIdInputSource)super.clone();
	}

}

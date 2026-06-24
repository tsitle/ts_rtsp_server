package org.tsitle.lib_xrtxp.rtsp.ids;

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

	public void copyFrom(@NonNull RtspProtoIdStreamSource other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		setIdStr(other.getIdStr().orElse(""));
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoIdStreamSource clone() {
		return (RtspProtoIdStreamSource)super.clone();
	}

}

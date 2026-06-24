package org.tsitle.lib_xrtxp.rtsp.ids;

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

	public void copyFrom(@NonNull RtspProtoIdInputSource other) {
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
	public RtspProtoIdInputSource clone() {
		return (RtspProtoIdInputSource)super.clone();
	}

}

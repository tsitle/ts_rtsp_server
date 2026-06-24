package org.tsitle.lib_xrtxp.rtsp.ids;

import org.jspecify.annotations.NonNull;

/**
 * Sub-Stream ID
 */
public final class RtspProtoIdSubStream extends RtspProtoBaseIdString implements Cloneable {

	private RtspProtoIdSubStream() {
		super();
	}

	private RtspProtoIdSubStream(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdSubStream ofEmpty() {
		return new RtspProtoIdSubStream();
	}

	public static RtspProtoIdSubStream of(@NonNull String idStr) {
		return new RtspProtoIdSubStream(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFrom(@NonNull RtspProtoIdSubStream other) {
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
	public RtspProtoIdSubStream clone() {
		return (RtspProtoIdSubStream)super.clone();
	}

}

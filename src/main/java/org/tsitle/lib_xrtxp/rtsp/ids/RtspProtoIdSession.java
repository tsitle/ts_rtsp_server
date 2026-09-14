package org.tsitle.lib_xrtxp.rtsp.ids;

import org.jspecify.annotations.NonNull;

/**
 * Session ID
 */
public final class RtspProtoIdSession extends RtspProtoBaseIdString implements Cloneable {

	private RtspProtoIdSession() {
		super();
	}

	private RtspProtoIdSession(@NonNull String idStr) {
		super(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static RtspProtoIdSession ofEmpty() {
		return new RtspProtoIdSession();
	}

	public static RtspProtoIdSession of(@NonNull String idStr) {
		return new RtspProtoIdSession(idStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void copyFrom(@NonNull RtspProtoIdSession other) {
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
	public @NonNull RtspProtoIdSession clone() {
		return (RtspProtoIdSession)super.clone();
	}

}

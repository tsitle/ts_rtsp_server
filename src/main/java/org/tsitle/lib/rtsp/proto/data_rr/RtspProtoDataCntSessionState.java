package org.tsitle.lib.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.enums.RtspProtoSessionState;

public final class RtspProtoDataCntSessionState {

	private boolean isWriteProtected = false;

	private @NonNull RtspProtoSessionState sessionState = RtspProtoSessionState.INIT;

	public RtspProtoDataCntSessionState() { }

	public RtspProtoDataCntSessionState(@NonNull RtspProtoSessionState sessionState) {
		this.sessionState = sessionState;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoSessionState getSessionState() {
		return sessionState;
	}
	public void setSessionState(@NonNull RtspProtoSessionState value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.sessionState = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		sessionState = RtspProtoSessionState.INIT;
	}

	public void copyFrom(@NonNull RtspProtoDataCntSessionState other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		sessionState = other.sessionState;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"sessionState=" + sessionState +
				"]";
	}

}

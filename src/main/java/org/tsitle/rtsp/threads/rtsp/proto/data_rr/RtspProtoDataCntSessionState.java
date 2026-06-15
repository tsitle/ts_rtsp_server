package org.tsitle.rtsp.threads.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspSessionState;

public final class RtspProtoDataCntSessionState {

	private boolean isWriteProtected = false;

	private @NonNull RtspSessionState sessionState = RtspSessionState.INIT;

	public RtspProtoDataCntSessionState() { }

	public RtspProtoDataCntSessionState(@NonNull RtspSessionState sessionState) {
		this.sessionState = sessionState;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspSessionState getSessionState() {
		return sessionState;
	}
	public void setSessionState(@NonNull RtspSessionState value) {
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
		sessionState = RtspSessionState.INIT;
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

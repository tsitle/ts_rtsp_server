package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;

public final class RtspProtoIdSession {

	private boolean isWriteProtected = false;

	private @NonNull String sessionId = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public RtspProtoIdSession() { }

	public RtspProtoIdSession(@NonNull String sessionId) {
		this.sessionId = sessionId;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getId() {
		return sessionId;
	}

	public void setId(@NonNull String sessionId) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.sessionId = sessionId;
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		sessionId = "";
	}

	public void copyFrom(@NonNull RtspProtoIdSession inputIdSession) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		sessionId = inputIdSession.sessionId;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

	public boolean isEmpty() {
		return sessionId.isEmpty();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		RtspProtoIdSession that = (RtspProtoIdSession)obj;
		return sessionId.equals(that.sessionId);
	}

	@Override
	public int hashCode() {
		return sessionId.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"sessionId='" + sessionId + "'" +
				"]";
	}

}

package org.tsitle.lib_xrtxp.rtsp.sdp.types;

import org.jspecify.annotations.NonNull;

public record RtspProtoSdpDataCommonOrigin(
			@NonNull String username,
			@NonNull String sessionId,
			@NonNull String version,
			@NonNull RtspProtoSdpDataConnectionOrigin connInfo
		) {

	public RtspProtoSdpDataCommonOrigin(@NonNull RtspProtoSdpDataCommonOrigin other) {
		this(other.username, other.sessionId, other.version, new RtspProtoSdpDataConnectionOrigin(other.connInfo));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (username.isBlank() && sessionId.isBlank() && version.isBlank() && connInfo.isEmpty());
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"username='" + username + "'" +
				", sessionId='" + sessionId + "'" +
				", version='" + version + "'" +
				", connInfo=" + connInfo +
				"]";
	}

}

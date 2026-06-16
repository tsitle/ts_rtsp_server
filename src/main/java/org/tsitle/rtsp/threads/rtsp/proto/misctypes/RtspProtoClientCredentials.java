package org.tsitle.rtsp.threads.rtsp.proto.misctypes;

import org.jspecify.annotations.NonNull;

/**
 * Container for client credentials for authentication.
 */
public final class RtspProtoClientCredentials {

	/** Authentication credentials: username */
	public @NonNull String authUser = "";
	/** Authentication credentials: password */
	public @NonNull String authPlainPassword = "";

}

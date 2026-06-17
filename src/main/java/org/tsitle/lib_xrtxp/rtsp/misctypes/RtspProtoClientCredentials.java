package org.tsitle.lib_xrtxp.rtsp.misctypes;

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

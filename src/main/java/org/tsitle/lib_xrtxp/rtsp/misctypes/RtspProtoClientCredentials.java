package org.tsitle.lib_xrtxp.rtsp.misctypes;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Container for client credentials for authentication.
 */
public final class RtspProtoClientCredentials {

	/** Authentication credentials: username */
	private @NonNull String authUser = "";
	/** Authentication credentials: password */
	private @NonNull String authPlainPassword = "";

	private RtspProtoClientCredentials() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RtspProtoClientCredentials ofEmpty() {
		return new RtspProtoClientCredentials();
	}

	public static @NonNull RtspProtoClientCredentials of(@NonNull String authUser, @NonNull String authPlainPassword) {
		RtspProtoClientCredentials resObj = new RtspProtoClientCredentials();
		resObj.authUser = authUser;
		resObj.authPlainPassword = authPlainPassword;
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public Optional<String> getAuthUser() {
		if (authUser.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(authUser);
	}

	public Optional<String> getAuthPlainPassword() {
		if (authPlainPassword.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(authPlainPassword);
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return authUser.isBlank();  // the password can be empty as long as the user is specified
	}

}

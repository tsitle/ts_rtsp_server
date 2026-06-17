package org.tsitle.lib_xrtxp.rtsp.data_rr;

import org.jspecify.annotations.NonNull;

public final class RtspProtoDataCntAuthClient {

	private boolean isWriteProtected = false;

	/** Authentication credentials: username (from URL or WWW-Authenticate header) */
	private @NonNull String authUser = "";
	/** Authentication credentials: password (from URL - not WWW-Authenticate header) */
	private @NonNull String authPlainPassword = "";
	/** Authentication credentials: realm */
	private @NonNull String authRealm = "";
	/** Authentication credentials: nonce */
	private @NonNull String authNonce = "";
	/** Authentication credentials: URI */
	private @NonNull String authUri = "";
	/** Authentication credentials: response */
	private @NonNull String authResp = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull String getAuthUser() {
		return authUser;
	}
	public void setAuthUser(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.authUser = value;
	}

	public @NonNull String getAuthPlainPassword() {
		return authPlainPassword;
	}
	public void setAuthPlainPassword(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.authPlainPassword = value;
	}

	public @NonNull String getAuthRealm() {
		return authRealm;
	}
	public void setAuthRealm(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.authRealm = value;
	}

	public @NonNull String getAuthNonce() {
		return authNonce;
	}
	public void setAuthNonce(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.authNonce = value;
	}

	public @NonNull String getAuthUri() {
		return authUri;
	}
	public void setAuthUri(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.authUri = value;
	}

	public @NonNull String getAuthResp() {
		return authResp;
	}
	public void setAuthResp(@NonNull String value) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		this.authResp = value;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		authUser = "";
		authPlainPassword = "";
		authRealm = "";
		authNonce = "";
		authUri = "";
		authResp = "";
	}

	public void copyFrom(@NonNull RtspProtoDataCntAuthClient other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		if (other == this) {
			return;
		}
		authUser = other.authUser;
		authPlainPassword = other.authPlainPassword;
		authRealm = other.authRealm;
		authNonce = other.authNonce;
		authUri = other.authUri;
		authResp = other.authResp;
	}

	public void writeProtect() {
		isWriteProtected = true;
	}

}

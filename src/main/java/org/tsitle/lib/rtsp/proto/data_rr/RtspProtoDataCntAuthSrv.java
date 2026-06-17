package org.tsitle.lib.rtsp.proto.data_rr;

import org.jspecify.annotations.NonNull;

public final class RtspProtoDataCntAuthSrv implements Cloneable {

	private boolean isWriteProtected = false;

	/** Authentication credentials: realm */
	private @NonNull String authRealm = "";
	/** Authentication credentials: nonce */
	private @NonNull String authNonce = "";

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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

	// -----------------------------------------------------------------------------------------------------------------

	public boolean isEmpty() {
		return (authRealm.isBlank() && authNonce.isBlank());
	}

	public void copyFrom(@NonNull RtspProtoDataCntAuthSrv other) {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		authRealm = other.authRealm;
		authNonce = other.authNonce;
	}

	public void clear() {
		if (isWriteProtected) {
			throw new IllegalStateException(getClass().getSimpleName() + ": Object is write protected");
		}
		authRealm = "";
		authNonce = "";
	}

	public boolean isReadOnly() {
		return isWriteProtected;
	}
	public void writeProtect() {
		isWriteProtected = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public RtspProtoDataCntAuthSrv clone() {
		try {
			return (RtspProtoDataCntAuthSrv)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}

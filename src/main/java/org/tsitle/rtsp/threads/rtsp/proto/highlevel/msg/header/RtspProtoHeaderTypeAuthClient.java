package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoHeaderTypeAuthClient {

	/** Authentication credentials: username */
	public @NonNull String authUser = "";
	/** Authentication credentials: realm */
	public @NonNull String authRealm = "";
	/** Authentication credentials: nonce */
	public @NonNull String authNonce = "";
	/** Authentication credentials: URI */
	public @NonNull String authUri = "";
	/** Authentication credentials: response */
	public @NonNull String authResp = "";

	@Override
	public @NonNull String toString() {
		return "[" +
				"authUser='" + authUser + "'" +
				", authRealm='" + authRealm + "'" +
				", authNonce='" + authNonce + "'" +
				", authUri='" + authUri + "'" +
				", authResp='" + authResp + "'" +
				"]";
	}

}

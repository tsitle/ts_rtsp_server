package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeAuthClient {

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
				"authRealm='" + authRealm + "'" +
				", authNonce='" + authNonce + "'" +
				", authUri='" + authUri + "'" +
				", authResp='" + authResp + "'" +
				"]";
	}

}

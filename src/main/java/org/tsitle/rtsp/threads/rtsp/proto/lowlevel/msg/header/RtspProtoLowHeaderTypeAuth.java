package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeAuth {

	public enum AuthAlgo {
		NONE,
		MD5
	}

	/** Authentication credentials: realm */
	public @NonNull String authRealm = "";
	/** Authentication credentials: nonce */
	public @NonNull String authNonce = "";
	/** Authentication credentials: URI */
	public @NonNull String authUri = "";
	/** Authentication credentials: response */
	public @NonNull String authResp = "";
	/** Authentication credentials: algorithm (e.g. MD5) */
	public @NonNull AuthAlgo authAlgo = AuthAlgo.NONE;

	@Override
	public @NonNull String toString() {
		return "[" +
				"authRealm='" + authRealm + "'" +
				", authNonce='" + authNonce + "'" +
				", authUri='" + authUri + "'" +
				", authResp='" + authResp + "'" +
				", authAlgo=" + authAlgo +
				"]";
	}

}

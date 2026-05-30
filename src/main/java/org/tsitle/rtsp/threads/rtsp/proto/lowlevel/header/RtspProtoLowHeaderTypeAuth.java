package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeAuth {

	public enum AuthAlgo {
		NONE,
		MD5
	}

	/** Authentication credentials: realm from the client */
	public @NonNull String authRealmClient = "";
	/** Authentication credentials: nonce from the server */
	public @NonNull String authNonceServer = "";
	/** Authentication credentials: nonce from the client */
	public @NonNull String authNonceClient = "";
	/** Authentication credentials: URI */
	public @NonNull String authUri = "";
	/** Authentication credentials: response */
	public @NonNull String authResp = "";
	/** Authentication credentials: algorithm (e.g. MD5) */
	public @NonNull AuthAlgo authAlgo = AuthAlgo.NONE;

	@Override
	public @NonNull String toString() {
		return "[" +
				"authRealmClient='" + authRealmClient + "'" +
				", authNonceServer='" + authNonceServer + "'" +
				", authNonceClient='" + authNonceClient + "'" +
				", authUri='" + authUri + "'" +
				", authResp='" + authResp + "'" +
				", authAlgo=" + authAlgo +
				"]";
	}

}

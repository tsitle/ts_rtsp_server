package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspAuthAlgo;

public class RtspProtoHeaderTypeAuthServer {

	/** Authentication credentials: realm */
	public @NonNull String authRealm = "";
	/** Authentication credentials: nonce */
	public @NonNull String authNonce = "";
	/** Authentication credentials: algorithm (e.g. MD5) */
	public @NonNull RtspAuthAlgo authAlgo = RtspAuthAlgo.NONE;

	@Override
	public @NonNull String toString() {
		return "[" +
				"authRealm='" + authRealm + "'" +
				", authNonce='" + authNonce + "'" +
				", authAlgo=" + authAlgo +
				"]";
	}

}

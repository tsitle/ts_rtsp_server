package org.tsitle.lib.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;

public enum RtspHeaderKey {

	NONE("-none-"),
	//
	/** (only for requests) */
	ACCEPT("Accept"),
	/** (only for requests) */
	AUTH_CLIENT("Authorization"),
	/** (only for responses) */
	AUTH_SERVER("WWW-Authenticate"),
	CONNECTION("Connection"),
	CONTENT_BASE("Content-Base"),
	CONTENT_ENC("Content-Encoding"),
	CONTENT_LANG("Content-Language"),
	CONTENT_LEN("Content-Length"),
	CONTENT_TYPE("Content-Type"),
	CSEQ("CSeq"),
	DATE("Date"),
	KEYMGMT("KeyMgmt"),
	/** (only for requests) */
	PROXY_REQU("Proxy-Require"),
	/** (only for responses) */
	PUBLIC("Public"),
	RANGE("Range"),
	/** (only for requests) */
	REQUIRE("Require"),
	/** (only for responses) */
	RTPINFO("RTP-Info"),
	/** (only for responses) */
	SERVER("Server"),
	SESSION("Session"),
	TRANSPORT("Transport"),
	/** (only for responses) */
	UNSUPPORTED("Unsupported"),
	/** (only for requests) */
	USERAGENT("User-Agent");

	private final @NonNull String value;

	RtspHeaderKey(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspHeaderKey of(@NonNull String value) {
		for (RtspHeaderKey entry : values()) {
			if (entry == NONE) {
				continue;
			}
			if (entry.value.equalsIgnoreCase(value)) {
				return entry;
			}
		}
		return NONE;
	}

}

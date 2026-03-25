package org.tsitle.rtsp.threads.rtsp;

public enum ServerResponseStatusCode {

	OK(200),
	BAD_REQUEST(400),
	UNAUTHORIZED(401),
	FORBIDDEN(403),
	NOT_FOUND(404),
	METHOD_NOT_ALLOWED(405),
	SESSION_NOT_FOUND(454),
	UNSUPPORTED_TRANSPORT(461);

	private final int value;

	ServerResponseStatusCode(int value) {
		this.value = value;
	}
	public int getValue() {
		return value;
	}

}

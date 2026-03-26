package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;

public enum ServerResponseStatusCode {

	OK(200),
	BAD_REQUEST(400),
	UNAUTHORIZED(401),
	FORBIDDEN(403),
	NOT_FOUND(404),
	METHOD_NOT_ALLOWED(405),
	URI_TOO_LONG(414),
	SESSION_NOT_FOUND(454),
	UNSUPPORTED_TRANSPORT(461);

	private final int value;

	ServerResponseStatusCode(int value) {
		this.value = value;
	}
	public int getValue() {
		return value;
	}

	public @NonNull String getReasonPhrase() {
		return switch (this) {
				case OK -> "OK";
				case BAD_REQUEST -> "Bad Request";
				case UNAUTHORIZED -> "Unauthorized";
				case FORBIDDEN -> "Forbidden";
				case NOT_FOUND -> "Not Found";
				case METHOD_NOT_ALLOWED -> "Method Not Allowed";
				case URI_TOO_LONG -> "URI Too Long";
				case SESSION_NOT_FOUND -> "Session Not Found";
				case UNSUPPORTED_TRANSPORT -> "Unsupported Transport";
			};
	}

}

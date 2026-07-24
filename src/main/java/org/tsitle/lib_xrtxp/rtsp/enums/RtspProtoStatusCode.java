package org.tsitle.lib_xrtxp.rtsp.enums;

import org.jspecify.annotations.NonNull;

public enum RtspProtoStatusCode {

	OK(200),
	BAD_REQUEST(400),
	UNAUTHORIZED(401),
	FORBIDDEN(403),
	NOT_FOUND(404),
	METHOD_NOT_ALLOWED(405),
	NOT_ACCEPTABLE(406),
	URI_TOO_LONG(414),
	INVALID_PARAMETER(451),
	SESSION_NOT_FOUND(454),
	METHOD_NOT_VALID_IN_THIS_STATE(455),
	INVALID_RANGE(457),
	UNSUPPORTED_TRANSPORT(461),
	INTERNAL_SERVER_ERROR(500),
	NOT_IMPLEMENTED(501),
	OPTION_NOT_SUPPORTED(551);

	private final int value;

	RtspProtoStatusCode(int value) {
		this.value = value;
	}

	public int getIntValue() {
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
				case NOT_ACCEPTABLE -> "Not Acceptable";
				case URI_TOO_LONG -> "URI Too Long";
				case INVALID_PARAMETER -> "Invalid Parameter";
				case SESSION_NOT_FOUND -> "Session Not Found";
				case METHOD_NOT_VALID_IN_THIS_STATE -> "Method Not Valid In This State";
				case INVALID_RANGE -> "Invalid Range";
				case UNSUPPORTED_TRANSPORT -> "Unsupported Transport";
				case INTERNAL_SERVER_ERROR -> "Internal Server Error";
				case NOT_IMPLEMENTED -> "Not Implemented";
				case OPTION_NOT_SUPPORTED -> "Option Not Supported";
			};
	}

	public static @NonNull RtspProtoStatusCode of(int value) {
		for (RtspProtoStatusCode entry : values()) {
			if (entry.value == value) {
				return entry;
			}
		}
		return INTERNAL_SERVER_ERROR;
	}

}

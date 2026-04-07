package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class RequestBasicInfo {

	public static class RequestUrlInputOrStreamSource {
		@Nullable String subStreamId = null;
		@Nullable String inputSourceId = null;
		int streamSourceId = -1;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull ServerMessageType serverMessageType = ServerMessageType.UNKNOWN;
	public @NonNull ServerResponseStatusCode statusCode = ServerResponseStatusCode.OK;
	public @Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource = null;

	private RequestBasicInfo() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isValid() { return (serverMessageType != ServerMessageType.UNKNOWN && statusCode == ServerResponseStatusCode.OK); }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RequestBasicInfo createUnknown() {
		RequestBasicInfo res = new RequestBasicInfo();
		res.serverMessageType = ServerMessageType.UNKNOWN;
		return res;
	}

	public static @NonNull RequestBasicInfo createUnsupportedMethod() {
		RequestBasicInfo res = new RequestBasicInfo();
		res.serverMessageType = ServerMessageType.UNKNOWN;
		res.statusCode = ServerResponseStatusCode.METHOD_NOT_ALLOWED;
		return res;
	}

	public static @NonNull RequestBasicInfo createKnownWithError(
				@NonNull ServerMessageType serverMessageType,
				@NonNull ServerResponseStatusCode statusCode
			) {
		RequestBasicInfo res = new RequestBasicInfo();
		res.serverMessageType = serverMessageType;
		res.statusCode = statusCode;
		return res;
	}

	public static @NonNull RequestBasicInfo createOk(
				@NonNull ServerMessageType serverMessageType,
				@Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource
			) {
		RequestBasicInfo res = new RequestBasicInfo();
		res.serverMessageType = serverMessageType;
		res.requestUrlInputOrStreamSource = requestUrlInputOrStreamSource;
		return res;
	}

}

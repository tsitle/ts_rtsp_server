package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class RequestBasicInfo {

	public static class RequestUrlInputOrStreamSource {
		public @Nullable String subStreamId = null;
		public @Nullable String inputSourceId = null;
		public int streamSourceId = -1;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoMessageType messageType = RtspProtoMessageType.UNKNOWN;
	public @NonNull RtspProtoStatusCode statusCode = RtspProtoStatusCode.OK;
	public @Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource = null;

	private RequestBasicInfo() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isValid() { return (messageType != RtspProtoMessageType.UNKNOWN && statusCode == RtspProtoStatusCode.OK); }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull RequestBasicInfo createUnknown() {
		RequestBasicInfo res = new RequestBasicInfo();
		res.messageType = RtspProtoMessageType.UNKNOWN;
		return res;
	}

	public static @NonNull RequestBasicInfo createUnsupportedMethod() {
		RequestBasicInfo res = new RequestBasicInfo();
		res.messageType = RtspProtoMessageType.UNKNOWN;
		res.statusCode = RtspProtoStatusCode.METHOD_NOT_ALLOWED;
		return res;
	}

	public static @NonNull RequestBasicInfo createKnownWithError(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoStatusCode statusCode
			) {
		RequestBasicInfo res = new RequestBasicInfo();
		res.messageType = messageType;
		res.statusCode = statusCode;
		return res;
	}

	public static @NonNull RequestBasicInfo createOk(
				@NonNull RtspProtoMessageType messageType,
				@Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource
			) {
		RequestBasicInfo res = new RequestBasicInfo();
		res.messageType = messageType;
		res.requestUrlInputOrStreamSource = requestUrlInputOrStreamSource;
		return res;
	}

}

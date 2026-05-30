package org.tsitle.rtsp.threads.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderEntry;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RtspProtoLowMsgStructured {

	public @NonNull RtspProtoMessageType messageType = RtspProtoMessageType.UNKNOWN;
	public @NonNull RtspProtoStatusCode statusCode = RtspProtoStatusCode.BAD_REQUEST;

	/** Resource URL */
	public @NonNull String resourceUrl = "";

	/** RTSP protocol version (e.g. 'RTSP/1.0') */
	public @NonNull RtspProtocolVersion rtspProtoVersion = RtspProtocolVersion.NONE;

	/** Authentication credentials: username (from URL or WWW-Authenticate header) */
	public @NonNull String authUser = "";
	/** Authentication credentials: password (from URL - not WWW-Authenticate header) */
	public @NonNull String authPlainPassword = "";

	/** URL Query Parameters */
	public @NonNull Map<@NonNull String, @NonNull String> queryParams = new HashMap<>();

	/** Headers */
	public @NonNull Map<@NonNull RtspProtoLowHeaderKey, @NonNull RtspProtoLowHeaderEntry> headers = new HashMap<>();

	@Override
	public @NonNull String toString() {
		String resS = getClass().getSimpleName() + " [";
		resS += "messageType=" + messageType + ", ";
		resS += "statusCode=" + statusCode + ", ";
		resS += "resourceUrl='" + resourceUrl + "', ";
		resS += "rtspProtoVersion='" + rtspProtoVersion + "', ";
		resS += "authUser='" + authUser + "', ";
		resS += "authPlainPassword='" + authPlainPassword + "', ";
		resS += "queryParams=" + queryParams + ", ";
		resS += "headers=" + headers;
		return resS + "]";
	}

	public Optional<Integer> getHeaderCseq() {
		if (! headers.containsKey(RtspProtoLowHeaderKey.CSEQ)) {
			return Optional.empty();
		}
		return Optional.of(headers.get(RtspProtoLowHeaderKey.CSEQ).hdValCseq.cseqNr);
	}

	public Optional<String> getHeaderSessionId() {
		if (! headers.containsKey(RtspProtoLowHeaderKey.SESSION)) {
			return Optional.empty();
		}
		return Optional.of(headers.get(RtspProtoLowHeaderKey.SESSION).hdValSession.sessionIdStr);
	}

}

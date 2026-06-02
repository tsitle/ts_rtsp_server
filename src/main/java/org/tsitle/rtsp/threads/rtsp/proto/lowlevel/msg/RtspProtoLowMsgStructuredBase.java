package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;

import java.util.HashMap;
import java.util.Map;

public class RtspProtoLowMsgStructuredBase {

	public @NonNull RtspMessageType messageType = RtspMessageType.UNKNOWN;
	public @NonNull RtspStatusCode statusCode = RtspStatusCode.BAD_REQUEST;

	/** Resource URL without Query Parameters */
	public @NonNull String resourceUrl = "";
	/** URL Query Parameters */
	public @NonNull Map<@NonNull String, @NonNull String> queryParams = new HashMap<>();

	/** RTSP protocol version (e.g. 'RTSP/1.0') */
	public @NonNull RtspProtocolVersion rtspProtoVersion = RtspProtocolVersion.NONE;

	/** Message body (requires the header 'CONTENT_TYPE') */
	public @NonNull String body = "";

	protected RtspProtoLowMsgStructuredBase() { }

	protected @NonNull String internalToString(boolean firstPart) {
		String resS;
		if (firstPart) {
			resS = "messageType=" + messageType + ", ";
			resS += "statusCode=" + statusCode + ", ";
			resS += "resourceUrl='" + resourceUrl + "', ";
			resS += "queryParams=" + queryParams + ", ";
			resS += "rtspProtoVersion='" + rtspProtoVersion + "', ";
		} else {
			resS = "body='" + body.replaceAll("\\r\\n", "<CRLF>") + "'";
		}
		return resS;
	}

}

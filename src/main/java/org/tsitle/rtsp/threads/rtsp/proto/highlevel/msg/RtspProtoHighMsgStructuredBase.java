package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;

public class RtspProtoHighMsgStructuredBase {

	public @NonNull RtspMessageType messageType = RtspMessageType.UNKNOWN;
	public @NonNull RtspStatusCode statusCode = RtspStatusCode.BAD_REQUEST;

	/** RTSP protocol version (e.g. 'RTSP/1.0') */
	public @NonNull RtspProtocolVersion rtspProtoVersion = RtspProtocolVersion.NONE;

	/** Message body (requires the header 'CONTENT_TYPE') */
	public @NonNull String body = "";

	protected RtspProtoHighMsgStructuredBase() { }

	protected @NonNull String internalToString(boolean firstPart) {
		String resS;
		if (firstPart) {
			resS = "messageType=" + messageType + ", ";
			resS += "statusCode=" + statusCode + ", ";
			resS += "rtspProtoVersion='" + rtspProtoVersion + "', ";
		} else {
			resS = "body='" + body.replaceAll("\\r\\n", "<CRLF>") + "'";
		}
		return resS;
	}

}

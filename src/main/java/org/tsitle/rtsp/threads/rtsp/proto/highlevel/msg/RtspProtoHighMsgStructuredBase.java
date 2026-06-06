package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RtspProtoHighMsgStructuredBase {

	public @NonNull RtspMessageType messageType = RtspMessageType.UNKNOWN;
	public @NonNull RtspStatusCode statusCode = RtspStatusCode.BAD_REQUEST;

	/** RTSP protocol version (e.g. 'RTSP/1.0') */
	public @NonNull RtspProtocolVersion rtspProtoVersion = RtspProtocolVersion.NONE;

	protected RtspProtoHighMsgStructuredBase() { }

	protected @NonNull String internalToStringFirstPart() {
		return
				"messageType=" + messageType + ", " +
				"statusCode=" + statusCode + ", " +
				"rtspProtoVersion=" + rtspProtoVersion;
	}

	protected static @NonNull String cleanUpBodyString(@NonNull String input) {
		return input.replaceAll("\\r\\n", "<CRLF>")
					.replaceAll("\\r", "<CR>")
					.replaceAll("\\n", "<LF>")
					.replaceAll("\\t", "<TAB>")
					.replace("'", "\\'");
	}

	protected static @NonNull String mapToString(@NonNull Map<@NonNull String, @NonNull String> input) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean isFirst = true;
		for (Map.Entry<@NonNull String, @NonNull String> tmpEntry : input.entrySet()) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append("'").append(tmpEntry.getKey()).append("'='").append(tmpEntry.getValue()).append("'");
			isFirst = false;
		}
		sb.append("}");
		return sb.toString();
	}

}

package org.tsitle.lib_xrtxp.rtsp.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspProtocolVersion;

public class RtspProtoHighMsgStructuredBase {

	public @NonNull RtspProtoMessageType messageType = RtspProtoMessageType.UNKNOWN;
	public @NonNull RtspProtoStatusCode statusCode = RtspProtoStatusCode.BAD_REQUEST;

	/** RTSP protocol version (e.g. 'RTSP/1.0') */
	public @NonNull RtspProtocolVersion rtspProtoVersion = RtspProtocolVersion.NONE;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected RtspProtoHighMsgStructuredBase() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected @NonNull String internalToStringFirstPart() {
		return
				"messageType=" + messageType + ", " +
				"statusCode=" + statusCode + ", " +
				"rtspProtoVersion=" + rtspProtoVersion;
	}

}

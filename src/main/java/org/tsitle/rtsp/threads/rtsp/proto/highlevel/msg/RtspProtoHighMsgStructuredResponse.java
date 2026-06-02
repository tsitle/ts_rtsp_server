package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header.RtspProtoLowHeaderEntryResponse;

import java.util.HashMap;
import java.util.Map;

public final class RtspProtoHighMsgStructuredResponse extends RtspProtoHighMsgStructuredBase {

	/** Headers */
	public @NonNull Map<@NonNull RtspHeaderKey, @NonNull RtspProtoLowHeaderEntryResponse> headers = new HashMap<>();

	public RtspProtoHighMsgStructuredResponse() {
		super();
	}

	@Override
	public @NonNull String toString() {
		String resS = getClass().getSimpleName() + " [";
		resS += internalToString(true);
		resS += "headers=" + headers + ", ";
		resS += internalToString(false);
		return resS + "]";
	}

}

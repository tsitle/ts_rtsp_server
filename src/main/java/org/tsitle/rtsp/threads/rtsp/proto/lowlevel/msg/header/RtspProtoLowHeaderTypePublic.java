package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoMessageType;

import java.util.ArrayList;
import java.util.List;

public class RtspProtoLowHeaderTypePublic {

	public @NonNull List<@NonNull RtspProtoMessageType> messageTypes = new ArrayList<>();

	@Override
	public @NonNull String toString() {
		return "[messageTypes=" + messageTypes + "]";
	}

}

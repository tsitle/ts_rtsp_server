package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoMessageType;

import java.util.HashSet;
import java.util.Set;

public class RtspProtoLowHeaderTypePublic {

	public @NonNull Set<@NonNull RtspProtoMessageType> messageTypes = new HashSet<>();

	@Override
	public @NonNull String toString() {
		return "[messageTypes=" + messageTypes + "]";
	}

}

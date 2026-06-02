package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.util.HashSet;
import java.util.Set;

public class RtspProtoHeaderTypePublic {

	public @NonNull Set<@NonNull RtspMessageType> messageTypes = new HashSet<>();

	@Override
	public @NonNull String toString() {
		return "[messageTypes=" + messageTypes + "]";
	}

}

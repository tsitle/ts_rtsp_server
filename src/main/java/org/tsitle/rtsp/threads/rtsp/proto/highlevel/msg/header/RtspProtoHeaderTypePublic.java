package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntMessageTypes;

public final class RtspProtoHeaderTypePublic {

	public @NonNull RtspProtoDataCntMessageTypes messageTypes = new RtspProtoDataCntMessageTypes();

	@Override
	public @NonNull String toString() {
		return "[messageTypes=" + messageTypes + "]";
	}

}

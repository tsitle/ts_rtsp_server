package org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntMessageTypes;

public final class RtspProtoHeaderTypePublic {

	public @NonNull RtspProtoDataCntMessageTypes messageTypes = new RtspProtoDataCntMessageTypes();

	@Override
	public @NonNull String toString() {
		return "[messageTypes=" + messageTypes + "]";
	}

}

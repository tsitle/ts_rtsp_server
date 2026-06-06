package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspConnectionPolicy;

public class RtspProtoHeaderTypeConnection {

	public @NonNull RtspConnectionPolicy connectionPol = RtspConnectionPolicy.NONE;

	@Override
	public @NonNull String toString() {
		return "[connectionPol=" + connectionPol + "]";
	}

}

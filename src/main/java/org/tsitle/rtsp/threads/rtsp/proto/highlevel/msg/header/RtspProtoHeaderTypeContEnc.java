package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspContentEncoding;

public final class RtspProtoHeaderTypeContEnc {

	public @NonNull RtspContentEncoding contentEnc = RtspContentEncoding.NONE;

	@Override
	public @NonNull String toString() {
		return "[contentEnc=" + contentEnc + "]";
	}

}

package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoCseqNr;

public final class RtspProtoHeaderTypeCseq {

	public RtspProtoCseqNr cseqNr = new RtspProtoCseqNr();

	@Override
	public @NonNull String toString() {
		return "[" +
				"cseqNr=" + (cseqNr.isEmpty() ? "unset" : Long.toUnsignedString(cseqNr.getCseq32bit().orElse(-1L))) +
				"]";
	}

}

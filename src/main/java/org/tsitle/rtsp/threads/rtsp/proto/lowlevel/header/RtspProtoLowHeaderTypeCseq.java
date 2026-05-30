package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeCseq {

	public int cseqNr = -1;

	@Override
	public @NonNull String toString() {
		return "[cseqNr=" + cseqNr + "]";
	}

}

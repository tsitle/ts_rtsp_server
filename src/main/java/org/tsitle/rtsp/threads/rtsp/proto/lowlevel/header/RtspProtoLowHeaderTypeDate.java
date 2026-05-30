package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header;

import org.jspecify.annotations.NonNull;

public class RtspProtoLowHeaderTypeDate {

	public @NonNull String dateStr = "";

	@Override
	public @NonNull String toString() {
		return "[dateStr='" + dateStr + "']";
	}

}

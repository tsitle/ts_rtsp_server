package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

public class RtspProtoLowHeaderTypeDate {

	public @NonNull Instant dateObj = Instant.now();

	@Override
	public @NonNull String toString() {
		return "[dateObj='" + dateObj + "']";
	}

}

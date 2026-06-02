package org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

public class RtspProtoHeaderTypeDate {

	public @NonNull Instant dateObj = Instant.now();

	@Override
	public @NonNull String toString() {
		return "[dateObj='" + dateObj + "']";
	}

}

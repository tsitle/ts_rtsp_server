package org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header;

import org.jspecify.annotations.NonNull;

import java.time.Instant;

public final class RtspProtoHeaderTypeDate {

	public @NonNull Instant dateObj = Instant.now();

	@Override
	public @NonNull String toString() {
		return "[dateObj='" + dateObj + "']";
	}

}

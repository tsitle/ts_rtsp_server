package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper;

import org.jspecify.annotations.NonNull;

public class RtspLowBuilderHelper {

	public static @NonNull String buildHexString(int value) {
		return String.format("%08X", value);
	}

}

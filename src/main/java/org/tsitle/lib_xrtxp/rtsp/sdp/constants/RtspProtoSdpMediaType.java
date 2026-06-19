package org.tsitle.lib_xrtxp.rtsp.sdp.constants;

import org.jspecify.annotations.NonNull;

public enum RtspProtoSdpMediaType {

	UNKNOWN,
	//
	AUDIO,
	VIDEO,
	APPLICATION,
	DATA,
	CONTROL;

	public static @NonNull RtspProtoSdpMediaType of(@NonNull String value) {
		for (RtspProtoSdpMediaType entry : values()) {
			if (entry == UNKNOWN) {
				continue;
			}
			if (entry.name().equalsIgnoreCase(value)) {
				return entry;
			}
		}
		return UNKNOWN;
	}

}

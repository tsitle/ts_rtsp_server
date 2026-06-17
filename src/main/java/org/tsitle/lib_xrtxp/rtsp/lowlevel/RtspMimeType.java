package org.tsitle.lib_xrtxp.rtsp.lowlevel;

import org.jspecify.annotations.NonNull;

public enum RtspMimeType {

	NONE("-none-"),
	MIKEY("application/x-rtsp-mikey"),
	PARAMETERS("text/parameters"),
	SDP("application/sdp");

	private final @NonNull String value;

	RtspMimeType(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspMimeType of(@NonNull String value) {
		for (RtspMimeType entry : values()) {
			if (entry == NONE) {
				continue;
			}
			if (entry.value.equalsIgnoreCase(value)) {
				return entry;
			}
		}
		return NONE;
	}

}

package org.tsitle.lib_xrtxp.rtsp.lowlevel;

import org.jspecify.annotations.NonNull;

public enum RtspTransportMode {

	NONE("-none-"),
	PLAY("PLAY"),
	RECORD("RECORD");

	private final @NonNull String value;

	RtspTransportMode(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspTransportMode of(@NonNull String value) {
		for (RtspTransportMode entry : values()) {
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

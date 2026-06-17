package org.tsitle.lib.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;

public enum RtspConnectionPolicy {

	NONE("-none-"),
	CLOSE("close"),
	KEEPALIVE("keep-alive");

	private final @NonNull String value;

	RtspConnectionPolicy(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspConnectionPolicy of(@NonNull String value) {
		for (RtspConnectionPolicy entry : values()) {
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

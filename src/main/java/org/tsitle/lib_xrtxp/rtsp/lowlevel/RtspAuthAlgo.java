package org.tsitle.lib_xrtxp.rtsp.lowlevel;

import org.jspecify.annotations.NonNull;

public enum RtspAuthAlgo {

	NONE("-none-"),
	MD5("MD5");

	private final @NonNull String value;

	RtspAuthAlgo(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspAuthAlgo of(@NonNull String value) {
		for (RtspAuthAlgo entry : values()) {
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

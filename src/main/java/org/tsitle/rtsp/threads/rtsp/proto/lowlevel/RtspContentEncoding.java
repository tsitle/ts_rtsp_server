package org.tsitle.rtsp.threads.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;

public enum RtspContentEncoding {

	NONE("-none-"),
	GZIP("gzip"),
	COMPRESS("compress"),
	DEFLATE("deflate");

	private final @NonNull String value;

	RtspContentEncoding(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspContentEncoding of(@NonNull String value) {
		for (RtspContentEncoding entry : values()) {
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

package org.tsitle.lib.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;

public enum RtspProtocolVersion {

	NONE("-none-"),
	RTSP_V1("RTSP/1.0"),
	RTSP_V2("RTSP/2.0");

	private final @NonNull String value;

	RtspProtocolVersion(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspProtocolVersion of(@NonNull String value) {
		for (RtspProtocolVersion entry : values()) {
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

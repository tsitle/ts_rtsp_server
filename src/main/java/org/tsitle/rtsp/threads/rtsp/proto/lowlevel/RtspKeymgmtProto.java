package org.tsitle.rtsp.threads.rtsp.proto.lowlevel;

import org.jspecify.annotations.NonNull;

public enum RtspKeymgmtProto {

	NONE("-none-"),
	MIKEY("mikey");

	private final @NonNull String value;

	RtspKeymgmtProto(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspKeymgmtProto of(@NonNull String value) {
		for (RtspKeymgmtProto entry : values()) {
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

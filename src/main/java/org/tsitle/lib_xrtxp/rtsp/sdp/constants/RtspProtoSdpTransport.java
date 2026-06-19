package org.tsitle.lib_xrtxp.rtsp.sdp.constants;

import org.jspecify.annotations.NonNull;

public enum RtspProtoSdpTransport {

	UNKNOWN("-none-"),
	/** Audio/Video Profile for RTP */
	RTP_AVP("RTP/AVP"),
	/** Audio/Video Profile for secure RTP */
	RTP_SAVP("RTP/SAVP"),
	/** UDP */
	UDP("udp");

	private final @NonNull String value;

	RtspProtoSdpTransport(@NonNull String value) {
		this.value = value;
	}

	public @NonNull String getStrValue() {
		return value;
	}

	public static @NonNull RtspProtoSdpTransport of(@NonNull String value) {
		for (RtspProtoSdpTransport entry : values()) {
			if (entry == UNKNOWN) {
				continue;
			}
			if (entry.value.equalsIgnoreCase(value)) {
				return entry;
			}
		}
		return UNKNOWN;
	}

}

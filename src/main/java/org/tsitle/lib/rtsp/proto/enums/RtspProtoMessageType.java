package org.tsitle.lib.rtsp.proto.enums;

import org.jspecify.annotations.NonNull;

public enum RtspProtoMessageType {

	UNKNOWN,
	/** Announce a new SDP. (RFC-2326 Section 10.3) */
	ANNOUNCE,
	/** Send initial SDP. (RFC-2326 Section 10.2) */
	DESCRIBE,
	/** Send one or more parameter values for the session. Also used for keep-alive. (RFC-2326 Section 10.8) */
	GET_PARAMETER,
	/** Send allowed methods. Also used for keep-alive. (RFC-2326 Section 10.1) */
	OPTIONS,
	/** Pause playback of stream. (RFC-2326 Section 10.6) */
	PAUSE,
	/** Start playback of stream. (RFC-2326 Section 10.5) */
	PLAY,
	/** Inform the client that it must connect to another server location. (RFC-2326 Section 10.10) */
	REDIRECT,
	/** Receive one or more parameter values for the session. (RFC-2326 Section 10.9) */
	SET_PARAMETER,
	/** Set a sub-stream up. (RFC-2326 Section 10.4) */
	SETUP,
	/** Stop playback of stream and tear the session down. (RFC-2326 Section 10.7) */
	TEARDOWN;

	public static @NonNull RtspProtoMessageType of(@NonNull String value) {
		for (RtspProtoMessageType entry : values()) {
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

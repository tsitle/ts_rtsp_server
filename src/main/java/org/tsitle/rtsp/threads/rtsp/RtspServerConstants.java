package org.tsitle.rtsp.threads.rtsp;

import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;

import java.util.Set;

public final class RtspServerConstants {

	private RtspServerConstants() { }

	/** Default TCP port for a RTSP server (without SSL/TLS) */
	public static final int SERVER_RTSP_TCP_PORT = 554;
	/** Default TCP port for a RTSPS server (with SSL/TLS) */
	public static final int SERVER_RTSPS_TCP_PORT = 332;

	public static final String SERVER_NAME = "TS RTSP Server";

	/** Supported RTSP message types of the local host */
	public static final Set<RtspMessageType> SERVER_SUPPORTED_MESSAGE_TYPES = Set.of(
			RtspMessageType.DESCRIBE,
			RtspMessageType.GET_PARAMETER,
			RtspMessageType.OPTIONS,
			RtspMessageType.PAUSE,
			RtspMessageType.PLAY,
			RtspMessageType.SET_PARAMETER,
			RtspMessageType.SETUP,
			RtspMessageType.TEARDOWN
		);

}

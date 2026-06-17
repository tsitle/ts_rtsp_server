package org.tsitle.rtsp.threads.rtsp;

import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;

import java.util.Set;

public final class RtspServerConstants {

	private RtspServerConstants() { }

	/** Default TCP port for a RTSP server (without SSL/TLS) */
	public static final int SERVER_RTSP_TCP_PORT = 554;
	/** Default TCP port for a RTSPS server (with SSL/TLS) */
	public static final int SERVER_RTSPS_TCP_PORT = 332;

	public static final String SERVER_NAME = "TS RTSP Server";

	/** Supported RTSP message types of the local host */
	public static final Set<RtspProtoMessageType> SERVER_SUPPORTED_MESSAGE_TYPES = Set.of(
			RtspProtoMessageType.DESCRIBE,
			RtspProtoMessageType.GET_PARAMETER,
			RtspProtoMessageType.OPTIONS,
			RtspProtoMessageType.PAUSE,
			RtspProtoMessageType.PLAY,
			RtspProtoMessageType.SET_PARAMETER,
			RtspProtoMessageType.SETUP,
			RtspProtoMessageType.TEARDOWN
		);

}

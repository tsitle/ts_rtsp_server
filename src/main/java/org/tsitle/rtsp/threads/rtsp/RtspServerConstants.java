package org.tsitle.rtsp.threads.rtsp;

public final class RtspServerConstants {

	private RtspServerConstants() { }

	/** Default TCP port for a RTSP server (without SSL/TLS) */
	public static final int SERVER_RTSP_TCP_PORT = 554;
	/** Default TCP port for a RTSPS server (with SSL/TLS) */
	public static final int SERVER_RTSPS_TCP_PORT = 332;

	public static final String SERVER_NAME = "TS RTSP Server";

}

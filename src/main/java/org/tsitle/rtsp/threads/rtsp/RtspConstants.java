package org.tsitle.rtsp.threads.rtsp;

public class RtspConstants {

	/** Default TCP port for a RTSP server */
	@SuppressWarnings("unused")
	public static final int SERVER_RTSP_TCP_PORT = 554;

	/** RTSP URL Protocol */
	public static final String RTSP_URL_PROTOCOL = "rtsp";

	/** RTSP Session Timeout */
	public static final int RTSP_SESSION_TIMEOUT = 20;

	/** Interval for sending PCM audio samples that were read from a file (in milliseconds) */
	public static final int RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS = 20;
	/** Samples per frame for AAC-LC audio */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO = 1024;

	/** RTSP Authorization Realm */
	public static final String RTSP_AUTH_REALM = "RTSP Server A1B2C3D4";

}

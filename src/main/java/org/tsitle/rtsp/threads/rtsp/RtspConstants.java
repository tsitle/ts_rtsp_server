package org.tsitle.rtsp.threads.rtsp;

public class RtspConstants {

	/** Name of the system property that holds the application version */
	public static final String SYSPROP_CSTM_APP_VERSION = "appVersion";

	/** Default TCP port for a RTSP server (without SSL/TLS) */
	@SuppressWarnings("unused")
	public static final int SERVER_RTSP_TCP_PORT = 554;
	/** Default TCP port for a RTSPS server (with SSL/TLS) */
	@SuppressWarnings("unused")
	public static final int SERVER_RTSPS_TCP_PORT = 332;

	/** RTSP URL Protocol */
	public static final String RTSP_URL_PROTOCOL = "rtsp";
	/** RTSPS URL Protocol */
	public static final String RTSPS_URL_PROTOCOL = "rtsps";

	/**
	 * RTSP Session Timeout in seconds -- this value will be announced to the client.<br />
	 * If TCP transport is being used, the timeout will be ignored. Instead, the {@link org.tsitle.rtsp.threads.RtxpTcpReadWrite}
	 * instance will check for a timeout.
	 */
	public static final int RTSP_SESSION_TIMEOUT = 20;

	/** Interval for sending PCM audio samples that were read from a file (in milliseconds) */
	public static final int RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS = 20;
	/** Samples per frame for AAC-LC audio -- Default Type 1 (most common) */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1 = 1024;
	/** Samples per frame for AAC-LC audio -- Default Type 2 */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2 = 960;
	/**
	 * Samples per frame for AAC-LC audio -- Low Delay<br />
	 * See note in {@link org.tsitle.rtsp.avdata.AudioAacInfo}
	 */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD = 512;

	/** RTSP Authorization Realm */
	public static final String RTSP_AUTH_REALM = "Realm_A1B2C3D4E5F6_G7H8I9_J10K11";

	/**
	 * Interval for re-keying the SRTxP master key and salt (in packets)
	 */
	public static final int SRTXP_REKEYING_INTERVAL_PACKETS = 1048576;  // ^=2^20

}

package org.tsitle.rtsp_server.threads.rtp;

public final class RtpConstants {

	private RtpConstants() { }

	/** Maximum frames per second (or virtual FPS in case of audio) */
	public static final double RTP_MAX_FRAMES_PER_SECOND = 100.0;

	/** Interval for sending PCM audio samples that were read from a file (in milliseconds) */
	public static final int RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS = 20;

	/** Interval for re-keying the SRTxP master key and salt (in packets) as integer */
	public static final long SRTXP_REKEYING_INTERVAL_PACKETS_INT = 1048576;  // ^=2^20

}

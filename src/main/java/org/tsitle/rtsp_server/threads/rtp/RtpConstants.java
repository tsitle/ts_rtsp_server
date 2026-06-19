package org.tsitle.rtsp_server.threads.rtp;

import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpConstants;

public final class RtpConstants {

	private RtpConstants() { }

	/** Interval for sending PCM audio samples that were read from a file (in milliseconds) */
	public static final int RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS = RtspProtoSdpConstants.RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS;
	/** Samples per frame for AAC-LC audio -- Default Type 1 (most common) */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF1 = 1024;
	/** Samples per frame for AAC-LC audio -- Default Type 2 */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_DEF2 = 960;
	/**
	 * Samples per frame for AAC-LC audio -- Low Delay<br />
	 * See note in {@link AudioAacInfo}
	 */
	public static final int RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO_LD = 512;

	/** Interval for re-keying the SRTxP master key and salt (in packets) as integer */
	public static final long SRTXP_REKEYING_INTERVAL_PACKETS_INT = 1048576;  // ^=2^20

}

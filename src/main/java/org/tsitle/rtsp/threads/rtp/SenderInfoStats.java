package org.tsitle.rtsp.threads.rtp;

import java.time.Instant;

public class SenderInfoStats {

	/** Wallclock timestamp at the beginning of the session */
	public Instant timeSessionStartWc = null;
	/** Wallclock timestamp in NTP format at the beginning of the session */
	public Long timeSessionStartNtpWc = null;
	/** Monotonic clock's timestamp at the beginning of the session */
	public long timeSessionStartMono = 0;

	public Instant lastSenderInfoSent = null;
	public Long timestampNtpWallclock = null;
	public int rtpTimestamp;
	public int rtpPacketsSent;
	public int rtpPayloadBytesSent;

}

package org.tsitle.rtsp.threads.rtp;

import java.time.Instant;

final class SenderInfoStats {

	Instant lastSenderInfoSent = null;
	Long timestampNtpWallclock = null;
	int rtpTimestamp;
	int rtpPacketsSent;
	int rtpPayloadBytesSent;

}

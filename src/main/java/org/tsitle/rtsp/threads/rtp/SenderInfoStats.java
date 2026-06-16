package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRtpTimestamp;

import java.time.Instant;

final class SenderInfoStats {

	Instant lastSenderInfoSent = null;
	Long timestampNtpWallclock = null;
	final @NonNull RtspProtoRtpTimestamp rtpTimestamp = RtspProtoRtpTimestamp.ofZero();
	int rtpPacketsSent;
	int rtpPayloadBytesSent;

}

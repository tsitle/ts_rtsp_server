package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.helpers.NtpTimestamp;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRtpTimestamp;

import java.time.Instant;

final class SenderInfoStats {

	@Nullable Instant lastSenderInfoSent = null;
	final @NonNull NtpTimestamp timestampNtpWallclock = NtpTimestamp.ofEmpty();
	final @NonNull RtspProtoRtpTimestamp rtpTimestamp = RtspProtoRtpTimestamp.ofZero();
	int rtpPacketsSent;
	int rtpPayloadBytesSent;

}

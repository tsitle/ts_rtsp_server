package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.helpers.NtpTimestamp;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;

import java.time.Instant;

final class SenderInfoStats {

	@Nullable Instant lastSenderInfoSent = null;
	final @NonNull NtpTimestamp timestampNtpWallclock = NtpTimestamp.ofEmpty();
	final @NonNull RtspProtoRtpTimestamp rtpTimestamp = RtspProtoRtpTimestamp.ofZero();
	int rtpPacketsSent;
	int rtpPayloadBytesSent;

}

package org.tsitle.rtsp_server.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.github.tsitle.lib_xrtxp.common.types.NtpTimestamp;
import io.github.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;

import java.time.Instant;

final class SenderInfoStats {

	@Nullable Instant lastSenderInfoSent = null;
	final @NonNull NtpTimestamp timestampNtpWallclock = NtpTimestamp.ofEmpty();
	final @NonNull RtspProtoRtpTimestamp rtpTimestamp = RtspProtoRtpTimestamp.ofZero();
	long rtpPacketsSent;
	long rtpPayloadBytesSent;

}

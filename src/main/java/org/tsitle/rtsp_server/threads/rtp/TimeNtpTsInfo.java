package org.tsitle.rtsp_server.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.helpers.NtpTimestamp;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;

final class TimeNtpTsInfo {

	/** Wallclock timestamp in NTP format at the beginning of the session */
	final @NonNull NtpTimestamp timeSessionStartNtpWc = NtpTimestamp.ofEmpty();
	/** Monotonic clock's timestamp at the beginning of the session (in nanoseconds) */
	final @NonNull TimestampEpochNs timeSessionStartMonoNs = TimestampEpochNs.ofEmpty();

}

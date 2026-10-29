package org.tsitle.rtsp_server.threads.rtp;

import org.jspecify.annotations.NonNull;
import io.github.tsitle.lib_xrtxp.common.types.NtpTimestamp;

final class TimeNtpTsInfo {

	/** Wallclock timestamp in NTP format at the beginning of the session */
	final @NonNull NtpTimestamp timeSessionStartNtpWc = NtpTimestamp.ofEmpty();

}

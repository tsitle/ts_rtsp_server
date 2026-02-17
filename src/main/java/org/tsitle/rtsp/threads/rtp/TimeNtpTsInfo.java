package org.tsitle.rtsp.threads.rtp;

final class TimeNtpTsInfo {

	/** Wallclock timestamp in NTP format at the beginning of the session */
	Long timeSessionStartNtpWc = null;
	/** Monotonic clock's timestamp at the beginning of the session (in nanoseconds) */
	long timeSessionStartMonoNs = 0;

}

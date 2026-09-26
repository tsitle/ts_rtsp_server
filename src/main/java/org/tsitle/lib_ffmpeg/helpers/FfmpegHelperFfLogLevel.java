package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.global.avutil;
import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;

/**
 * Helper class for setting FFmpeg's log level.
 */
public final class FfmpegHelperFfLogLevel {

	private FfmpegHelperFfLogLevel() { }

	public static void setLogLevel(@NonNull RtxpLogLevel logLevel) {
		int ffmpegLev = switch (logLevel) {
				case DEBUG -> avutil.AV_LOG_DEBUG;
				case INFO -> avutil.AV_LOG_INFO;
				case WARN -> avutil.AV_LOG_WARNING;
				default -> avutil.AV_LOG_ERROR;
			};
		avutil.av_log_set_level(ffmpegLev);
	}

	@SuppressWarnings("unused")
	public static void muteLogMsgs() throws UnsatisfiedLinkError {
		avutil.av_log_set_level(avutil.AV_LOG_QUIET);
	}

}

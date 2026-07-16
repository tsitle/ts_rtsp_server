package org.tsitle.lib_ffmpeg;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.jspecify.annotations.NonNull;

/**
 * Helper class for handling FFmpeg errors.
 */
public final class FfmpegErrorHelper {

	private FfmpegErrorHelper() { }

	public static @NonNull String ffmpegErrorText(int errCode) {
		try (BytePointer errBuf = new BytePointer(256)) {
			avutil.av_strerror(errCode, errBuf, 256);
			return errBuf.getString() + " (" + errCode + ")";
		}
	}

	public static void checkFfmpegResult(@NonNull String fncName, @NonNull String desc, int r)
			throws FfmpegGenericException {
		if (r < 0) {
			throw new FfmpegGenericException(fncName + ": " + desc + " failed: " + ffmpegErrorText(r));
		}
	}

}

package org.tsitle.lib_ffmpeg.helpers;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;
import org.tsitle.lib_ffmpeg.exceptions.FfmpegGenericException;
import org.jspecify.annotations.NonNull;

/**
 * Helper class for handling FFmpeg errors.
 */
public final class FfmpegHelperFfError {

	private FfmpegHelperFfError() { }

	public static @NonNull String ffmpegErrorText(int errCode) {
		try (BytePointer errBuf = new BytePointer(256)) {
			avutil.av_strerror(errCode, errBuf, 256);
			long slen = avutil.av_strnlen(errBuf, 256);
			return filterNonPrintableCharacters(
					errBuf.getString().substring(0, (int)slen) + " (" + errCode + ")"
				);
		}
	}

	public static void checkFfmpegResult(@NonNull String fncName, @NonNull String desc, int r)
			throws FfmpegGenericException {
		if (r < 0) {
			throw new FfmpegGenericException(fncName + ": " + desc + " failed: " + ffmpegErrorText(r));
		}
	}

	private static @NonNull String filterNonPrintableCharacters(@NonNull String str) {
		return str.replaceAll("[^\\x20-\\x7E]", "");
	}

}

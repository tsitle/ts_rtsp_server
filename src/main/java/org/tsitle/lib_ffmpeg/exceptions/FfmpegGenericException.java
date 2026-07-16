package org.tsitle.lib_ffmpeg.exceptions;

import org.jspecify.annotations.NonNull;

public class FfmpegGenericException extends Exception {
	public FfmpegGenericException(@NonNull String msg) { super(msg); }
}

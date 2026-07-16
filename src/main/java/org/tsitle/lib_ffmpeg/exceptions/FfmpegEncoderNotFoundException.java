package org.tsitle.lib_ffmpeg.exceptions;

import org.jspecify.annotations.NonNull;

public class FfmpegEncoderNotFoundException extends Exception {
	public FfmpegEncoderNotFoundException(@NonNull String msg) { super(msg); }
}

package org.tsitle.lib_ffmpeg.exceptions;

import org.jspecify.annotations.NonNull;

public class FfmpegDecoderNotFoundException extends Exception {
	public FfmpegDecoderNotFoundException(@NonNull String msg) { super(msg); }
}

package org.tsitle.lib_ffmpeg.tc;

import org.tsitle.lib_ffmpeg.FfmpegCodec;
import org.jspecify.annotations.NonNull;

public class FfmpegTcSettingsBase {

	public @NonNull FfmpegCodec ffmpegCodec = FfmpegCodec.UNKNOWN;
	public long bitRate = -1L;

	protected FfmpegTcSettingsBase() { }

}

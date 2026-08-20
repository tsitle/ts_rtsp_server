package org.tsitle.lib_ffmpeg.tc;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegCodec;

/**
 * Base class for Transcoder Output Settings.
 */
public class FfmpegTcSettingsOutBase {

	public @NonNull FfmpegCodec cfgFfmpegCodec;
	public boolean cfgCopySubStream;

	protected FfmpegTcSettingsOutBase() {
		baseClear();
	}

	protected void baseClear() {
		cfgFfmpegCodec = FfmpegCodec.UNKNOWN;
		cfgCopySubStream = false;
	}

	protected void baseCopyFrom(@NonNull FfmpegTcSettingsOutBase other) {
		cfgFfmpegCodec = other.cfgFfmpegCodec;
		cfgCopySubStream = other.cfgCopySubStream;
	}

}

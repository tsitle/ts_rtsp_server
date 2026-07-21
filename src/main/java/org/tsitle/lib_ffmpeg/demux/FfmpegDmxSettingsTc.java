package org.tsitle.lib_ffmpeg.demux;

/**
 * Settings for demuxing (with transcoding).
 */
public final class FfmpegDmxSettingsTc extends FfmpegDmxSettingsBase {

	/** Maximum seconds to demux (<= 0 means no limit) */
	public long cfgMaxSecs = -1L;

}

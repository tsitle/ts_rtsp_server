package org.tsitle.lib_ffmpeg.demux;

/**
 * Settings for demuxing (with transcoding).
 */
public final class FfmpegDmxSettingsTc {

	/** Maximum seconds to demux (<= 0 means no limit) */
	public long cfgMaxSecs = -1L;
	/** Select a specific video stream by number (<= 0 means no selection) */
	public int cfgSelectStreamNumberVideo = -1;
	/** Select a specific audio stream by number (<= 0 means no selection) */
	public int cfgSelectStreamNumberAudio = -1;

}

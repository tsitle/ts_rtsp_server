package org.tsitle.lib_ffmpeg.demux;

/**
 * Settings for demuxing (only for reading the sub-stream infos).
 */
public final class FfmpegDmxSettingsRsi {

	/** Select a specific video stream by number (<= 0 means no selection) */
	public int cfgSelectStreamNumberVideo = -1;
	/** Select a specific audio stream by number (<= 0 means no selection) */
	public int cfgSelectStreamNumberAudio = -1;

}

package org.tsitle.lib_ffmpeg.demux;

/**
 * Settings for demuxing (without transcoding).
 */
public final class FfmpegDmxSettingsDemux {

	/** Maximum seconds to demux (<= 0 means no limit) */
	public long cfgMaxSecs = -1L;
	/** Output H.26x as Annex B (true) or as-is? */
	public boolean cfgOutputH26xAsAnnexB = false;
	/** Output AAC with ADTS header (true) or as-is? */
	public boolean cfgOutputAacWithAdts = false;
	/** Select a specific video stream by number (<= 0 means no selection) */
	public int cfgSelectStreamNumberVideo = -1;
	/** Select a specific audio stream by number (<= 0 means no selection) */
	public int cfgSelectStreamNumberAudio = -1;

}

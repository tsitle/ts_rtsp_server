package org.tsitle.lib_ffmpeg.demux;

/**
 * Settings for demuxing (without transcoding).
 */
public final class FfmpegDmxSettingsDemux extends FfmpegDmxSettingsBase {

	/** Maximum seconds to demux (<= 0 means no limit) */
	public long cfgMaxSecs = -1L;
	/** Output H.26x as Annex B (true) or as-is? */
	public boolean cfgOutputH26xAsAnnexB = false;
	/** Output AAC with ADTS header (true) or as-is? */
	public boolean cfgOutputAacWithAdts = false;

}

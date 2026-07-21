package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;

/**
 * For internal use: Settings for demuxing.
 */
final class FfmpegDmxSettingsInternal {

	/** Maximum seconds to demux (<= 0 means no limit) */
	long cfgMaxSecs = -1L;
	/** Output H.26x as Annex B (true) or as-is? */
	boolean cfgOutputH26xAsAnnexB = false;
	/** Output AAC with ADTS header (true) or as-is? */
	boolean cfgOutputAacWithAdts = false;
	/** Select a specific video stream by number (<= 0 means no selection) */
	int cfgSelectStreamNumberVideo = -1;
	/** Select a specific audio stream by number (<= 0 means no selection) */
	int cfgSelectStreamNumberAudio = -1;

	static @NonNull FfmpegDmxSettingsInternal of(@NonNull FfmpegDmxSettingsRsi other) {
		FfmpegDmxSettingsInternal resObj = new FfmpegDmxSettingsInternal();
		resObj.cfgSelectStreamNumberVideo = other.cfgSelectStreamNumberVideo;
		resObj.cfgSelectStreamNumberAudio = other.cfgSelectStreamNumberAudio;
		return resObj;
	}

	static @NonNull FfmpegDmxSettingsInternal of(@NonNull FfmpegDmxSettingsDemux other) {
		FfmpegDmxSettingsInternal resObj = new FfmpegDmxSettingsInternal();
		resObj.cfgMaxSecs = other.cfgMaxSecs;
		resObj.cfgOutputH26xAsAnnexB = other.cfgOutputH26xAsAnnexB;
		resObj.cfgOutputAacWithAdts = other.cfgOutputAacWithAdts;
		resObj.cfgSelectStreamNumberVideo = other.cfgSelectStreamNumberVideo;
		resObj.cfgSelectStreamNumberAudio = other.cfgSelectStreamNumberAudio;
		return resObj;
	}

	static @NonNull FfmpegDmxSettingsInternal of(@NonNull FfmpegDmxSettingsTc other) {
		FfmpegDmxSettingsInternal resObj = new FfmpegDmxSettingsInternal();
		resObj.cfgMaxSecs = other.cfgMaxSecs;
		resObj.cfgSelectStreamNumberVideo = other.cfgSelectStreamNumberVideo;
		resObj.cfgSelectStreamNumberAudio = other.cfgSelectStreamNumberAudio;
		return resObj;
	}

}

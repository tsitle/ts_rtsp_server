package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;

/**
 * For internal use: Settings for demuxing.
 */
final class FfmpegDmxSettingsInternal extends FfmpegDmxSettingsBase {

	/** Maximum seconds to demux (<= 0 means no limit) */
	long cfgMaxSecs = -1L;
	/** Output H.26x as Annex B (true) or as-is? */
	boolean cfgOutputH26xAsAnnexB = false;
	/** Output AAC with ADTS header (true) or as-is? */
	boolean cfgOutputAacWithAdts = false;

	static @NonNull FfmpegDmxSettingsInternal of(@NonNull FfmpegDmxSettingsRsi other) {
		FfmpegDmxSettingsInternal resObj = new FfmpegDmxSettingsInternal();
		resObj.copyFrom(other);
		return resObj;
	}

	static @NonNull FfmpegDmxSettingsInternal of(@NonNull FfmpegDmxSettingsDemux other) {
		FfmpegDmxSettingsInternal resObj = new FfmpegDmxSettingsInternal();
		resObj.cfgMaxSecs = other.cfgMaxSecs;
		resObj.cfgOutputH26xAsAnnexB = other.cfgOutputH26xAsAnnexB;
		resObj.cfgOutputAacWithAdts = other.cfgOutputAacWithAdts;
		resObj.copyFrom(other);
		return resObj;
	}

	static @NonNull FfmpegDmxSettingsInternal of(@NonNull FfmpegDmxSettingsTc other) {
		FfmpegDmxSettingsInternal resObj = new FfmpegDmxSettingsInternal();
		resObj.cfgMaxSecs = other.cfgMaxSecs;
		resObj.copyFrom(other);
		return resObj;
	}

}

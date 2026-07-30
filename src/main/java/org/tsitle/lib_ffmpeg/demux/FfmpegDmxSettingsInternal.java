package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeAac;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeH26x;

/**
 * For internal use: Settings for demuxing.
 */
final class FfmpegDmxSettingsInternal extends FfmpegDmxSettingsBase {

	/** Maximum seconds to demux (<= 0 means no limit) */
	long cfgMaxSecs = -1L;
	/** Output H.26x as AnnexB, length-prefixed or as-is? */
	public @NonNull FfmpegPktConvModeH26x cfgOutputModeH26x = FfmpegPktConvModeH26x.PASSTHROUGH;
	/** Output AAC with/without ADTS header or as-is? */
	public @NonNull FfmpegPktConvModeAac cfgOutputModeAac = FfmpegPktConvModeAac.PASSTHROUGH;

	static @NonNull FfmpegDmxSettingsInternal of(@NonNull FfmpegDmxSettingsRsi other) {
		FfmpegDmxSettingsInternal resObj = new FfmpegDmxSettingsInternal();
		resObj.copyFrom(other);
		return resObj;
	}

	static @NonNull FfmpegDmxSettingsInternal of(@NonNull FfmpegDmxSettingsDemux other) {
		FfmpegDmxSettingsInternal resObj = new FfmpegDmxSettingsInternal();
		resObj.cfgMaxSecs = other.cfgMaxSecs;
		resObj.cfgOutputModeH26x = other.cfgOutputModeH26x;
		resObj.cfgOutputModeAac = other.cfgOutputModeAac;
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

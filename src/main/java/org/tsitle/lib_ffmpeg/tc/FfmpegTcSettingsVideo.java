package org.tsitle.lib_ffmpeg.tc;

import org.tsitle.lib_xrtxp.common.helpers.FrameRateEnum;
import org.jspecify.annotations.NonNull;

/**
 * Settings for video transcoding.
 */
public final class FfmpegTcSettingsVideo extends FfmpegTcSettingsBase {

	public @NonNull FrameRateEnum frameRateMax = FrameRateEnum.UNKNOWN;
	public @NonNull FrameRateEnum frameRateFixed = FrameRateEnum.UNKNOWN;
	public int imgDimsMax = -1;

	public FfmpegTcSettingsVideo() { }

}

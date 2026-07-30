package org.tsitle.lib_ffmpeg.demux;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeAac;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeH26x;

/**
 * Settings for demuxing (without transcoding).
 */
public final class FfmpegDmxSettingsDemux extends FfmpegDmxSettingsBase {

	/** Maximum seconds to demux (<= 0 means no limit) */
	public long cfgMaxSecs = -1L;
	/** Output H.26x as AnnexB, length-prefixed or as-is? */
	public @NonNull FfmpegPktConvModeH26x cfgOutputModeH26x = FfmpegPktConvModeH26x.PASSTHROUGH;
	/** Output AAC with/without ADTS header or as-is? */
	public @NonNull FfmpegPktConvModeAac cfgOutputModeAac = FfmpegPktConvModeAac.PASSTHROUGH;

}

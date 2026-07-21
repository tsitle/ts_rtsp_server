package org.tsitle.lib_ffmpeg.tc;

import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.jspecify.annotations.NonNull;

/**
 * Settings for audio transcoding.
 */
public final class FfmpegTcSettingsAudio extends FfmpegTcSettingsBase {

	public @NonNull SampleRateEnum sampleRate = SampleRateEnum.SR_048000;  // should be 48k for Opus (8/12/16/24/48 supported)
	public int channelCount = 2;

	public FfmpegTcSettingsAudio() { }

}

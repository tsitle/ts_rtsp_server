package org.tsitle.lib_ffmpeg.tc;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_ffmpeg.FfmpegAudioBitRate;
import org.tsitle.lib_ffmpeg.FfmpegPktConvModeAac;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;

/**
 * Settings for Audio Transcoder Output.
 */
public final class FfmpegTcSettingsOutAudio extends FfmpegTcSettingsOutBase {

	public enum ChannelConversionMode {
		PASSTHROUGH,
		DOWNMIX_STEREO,
		DOWNMIX_MONO,
		FIXED_STEREO
	}

	public enum SampleRateConversionMode {
		PASSTHROUGH,
		AUTO,
		FIXED
	}

	public enum OutputModeOpus {
		/** leave as-is */
		DEFAULT,
		/** output only as mono or stereo and with a compatible sample rate */
		RTP
	}

	public @NonNull ChannelConversionMode cfgChannelCm;
	public @NonNull SampleRateConversionMode cfgSrCm;
	public @NonNull SampleRateEnum cfgSampleRateFixed;
	public @NonNull SampleRateEnum cfgSampleRateMax;
	public @NonNull FfmpegAudioBitRate cfgBitRateKbps;
	/** Output AAC with/without ADTS header or as-is? */
	public @NonNull FfmpegPktConvModeAac cfgOutputModeAac;
	public @NonNull OutputModeOpus cfgOutputModeOpus;

	public FfmpegTcSettingsOutAudio() {
		clear();
	}

	public void clear() {
		baseClear();

		cfgChannelCm = ChannelConversionMode.PASSTHROUGH;
		cfgSrCm = SampleRateConversionMode.PASSTHROUGH;
		cfgSampleRateFixed = SampleRateEnum.UNKNOWN;
		cfgSampleRateMax = SampleRateEnum.SR_048000;
		cfgBitRateKbps = FfmpegAudioBitRate.UNKNOWN;
		cfgOutputModeAac = FfmpegPktConvModeAac.PASSTHROUGH;
		cfgOutputModeOpus = OutputModeOpus.DEFAULT;
	}

	public void copyFrom(@NonNull FfmpegTcSettingsOutAudio other) {
		if (this == other) {
			return;
		}
		baseCopyFrom(other);

		cfgChannelCm = other.cfgChannelCm;
		cfgSrCm = other.cfgSrCm;
		cfgSampleRateFixed = other.cfgSampleRateFixed;
		cfgSampleRateMax = other.cfgSampleRateMax;
		cfgBitRateKbps = other.cfgBitRateKbps;
		cfgOutputModeAac = other.cfgOutputModeAac;
		cfgOutputModeOpus = other.cfgOutputModeOpus;
	}

}

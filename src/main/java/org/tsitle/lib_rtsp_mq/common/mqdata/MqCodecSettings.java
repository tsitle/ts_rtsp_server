package org.tsitle.lib_rtsp_mq.common.mqdata;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.helpers.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * Codec settings.
 */
public final class MqCodecSettings {

	public @Nullable MqPacketCodec codec = null;
	public @Nullable FrameRateEnum videoFps = null;
	public @Nullable SampleRateEnum audioSamplerate = null;
	public @Nullable Byte audioChannels = null;
	public @Nullable Integer audioSamplesPerFrame = null;

	public @NonNull RtpPacketType getAsRtpPacketType() {
		if (codec == null) {
			return RtpPacketType.UNKNOWN;
		}
		if (codec.isVideo()) {
			if (videoFps == null || videoFps == FrameRateEnum.UNKNOWN) {
				return RtpPacketType.UNKNOWN;
			}
			return codec.convertToRtpPacketType(SampleRateEnum.UNKNOWN, (byte)0);
		} else {
			if (audioSamplerate == null || audioSamplerate == SampleRateEnum.UNKNOWN || audioChannels == null) {
				return RtpPacketType.UNKNOWN;
			}
			return codec.convertToRtpPacketType(audioSamplerate, audioChannels);
		}
	}

	@Override
	public String toString() {
		String tmpFpsStr = (videoFps == null ?
				"NULL" : String.format("%.3f", videoFps.getFrDbl()).replace(",", "."));
		return getClass().getSimpleName() + " [" +
				"codec=" + (codec == null ? "NULL" : codec) +
				", videoFps=" + tmpFpsStr +
				", audioSamplerate=" + (audioSamplerate == null ? "NULL" : audioSamplerate) +
				", audioChannels=" + (audioChannels == null ? "NULL" : audioChannels) +
				", audioSamplesPerFrame=" + (audioSamplesPerFrame == null ? "NULL" : audioSamplesPerFrame) +
				"]";
	}

}

package org.tsitle.lib_xrtxp.mq.common.mqdata;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.ConfigSsCodec;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * Codec settings.
 */
public class MqCodecSettings {

	public @Nullable MqPacketCodec codec = null;
	public @Nullable Double videoFps = null;
	public @Nullable Integer audioSamplerate = null;
	public @Nullable Byte audioChannels = null;

	public @NonNull RtpPacketType getAsRtpPacketType() {
		if (codec == null) {
			return RtpPacketType.UNKNOWN;
		}
		ConfigSsCodec configSsCodec = switch (codec) {
				case AACLC -> ConfigSsCodec.AACLC;
				case PCMU -> ConfigSsCodec.PCMU;
				case LPCM08U -> ConfigSsCodec.LPCM08U;
				case LPCM16S -> ConfigSsCodec.LPCM16S;
				case MJPEG -> ConfigSsCodec.MJPEG;
				case H264 -> ConfigSsCodec.H264;
				case H265 -> ConfigSsCodec.H265;
			};
		if (codec.isVideo()) {
			if (videoFps == null) {
				return RtpPacketType.UNKNOWN;
			}
			return configSsCodec.convertToRtpPacketType(0, (byte)0);
		} else {
			if (audioSamplerate == null || audioChannels == null) {
				return RtpPacketType.UNKNOWN;
			}
			return configSsCodec.convertToRtpPacketType(audioSamplerate, audioChannels);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"codec=" + (codec == null ? "NULL" : codec) +
				", videoFps=" + (videoFps == null ? "NULL" : String.format("%.2f", videoFps).replace(",", ".")) +
				", audioSamplerate=" + (audioSamplerate == null ? "NULL" : audioSamplerate) +
				", audioChannels=" + (audioChannels == null ? "NULL" : audioChannels) +
				"]";
	}

}

package org.tsitle.lib_rtsp_mq.common.mqdata;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * Codec settings.
 */
public final class MqCodecSettings {

	public @Nullable MqPacketCodec codec = null;
	public @Nullable Double videoFps = null;
	public @Nullable Integer audioSamplerate = null;
	public @Nullable Byte audioChannels = null;

	public @NonNull RtpPacketType getAsRtpPacketType() {
		if (codec == null) {
			return RtpPacketType.UNKNOWN;
		}
		if (codec.isVideo()) {
			if (videoFps == null) {
				return RtpPacketType.UNKNOWN;
			}
			return codec.convertToRtpPacketType(0, (byte)0);
		} else {
			if (audioSamplerate == null || audioChannels == null) {
				return RtpPacketType.UNKNOWN;
			}
			return codec.convertToRtpPacketType(audioSamplerate, audioChannels);
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

package org.tsitle.rtsp.mq.mqdata;

import org.jspecify.annotations.Nullable;

/**
 * Codec settings.
 */
public class MqCodecSettings {

	public @Nullable MqPacketCodec codec = null;
	public @Nullable Integer videoFps = null;
	public @Nullable Integer audioSamplerate = null;
	public @Nullable Integer audioChannels = null;

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" +
				"codec=" + (codec == null ? "NULL" : codec.getRtpPacketType().name()) +
				", videoFps=" + (videoFps == null ? "NULL" : videoFps) +
				", audioSamplerate=" + (audioSamplerate == null ? "NULL" : audioSamplerate) +
				", audioChannels=" + (audioChannels == null ? "NULL" : audioChannels) +
				"]";
	}

}

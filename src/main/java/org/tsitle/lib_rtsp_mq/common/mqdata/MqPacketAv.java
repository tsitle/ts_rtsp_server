package org.tsitle.lib_rtsp_mq.common.mqdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

/**
 * Audio/video packet.
 * @param msgNr Message number
 * @param codec The codec used for the payload
 * @param isCodecGuessed Whether the codec was guessed or explicitly set
 * @param mdTimestampMs Metadata: sample time timestamp of the payload in milliseconds
 * @param mdCounter Metadata: packet counter
 * @param mdVideoIsKeyframe Metadata: is this a keyframe? (video only)
 * @param mdVideoResoWidth Metadata: resolution width (video only)
 * @param mdVideoResoHeight Metadata: resolution height (video only)
 * @param mdVideoFps Metadata: frames per second (video only)
 * @param mdVideoBitrate Metadata: bitrate (video only)
 * @param mdAudioSamplerate Metadata: audio samplerate (audio only)
 * @param mdAudioChannelCount Metadata: audio channel count (audio only)
 * @param mdPayloadCRC8 Metadata: CRC8 checksum of the payload
 * @param payloadDataPtr Pointer to the payload data buffer
 */
public record MqPacketAv(
			long msgNr,
			@NonNull MqPacketCodec codec,
			boolean isCodecGuessed,
			long mdTimestampMs,
			int mdCounter,
			boolean mdVideoIsKeyframe,
			int mdVideoResoWidth,
			int mdVideoResoHeight,
			double mdVideoFps,
			int mdVideoBitrate,
			int mdAudioSamplerate,
			byte mdAudioChannelCount,
			byte mdPayloadCRC8,
			@NonNull BufferExt payloadDataPtr
		) {
	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"msgNr=" + Long.toUnsignedString(msgNr) +
				", codec=" + codec +
				(codec.isVideo() ? ", isCodecGuessed=" + (isCodecGuessed ? "T" : "F") : "") +
				", mdTimestampMs=" + Long.toUnsignedString(mdTimestampMs) +
				", mdCounter=" + Integer.toUnsignedString(mdCounter) +
				(codec.isVideo() ? ", mdVideoIsKeyframe=" + (mdVideoIsKeyframe ? "T" : "F") : "") +
				(codec.isVideo() ? ", mdVideoResoWidth=" + Integer.toUnsignedString(mdVideoResoWidth) : "") +
				(codec.isVideo() ? ", mdVideoResoHeight=" + Integer.toUnsignedString(mdVideoResoHeight) : "") +
				(codec.isVideo() ? ", mdVideoFps=" + String.format("%.3f", mdVideoFps).replace(",", ".") : "") +
				(codec.isVideo() ? ", mdVideoBitrate=" + Integer.toUnsignedString(mdVideoBitrate) : "") +
				(! codec.isVideo() ? ", mdAudioSamplerate=" + Integer.toUnsignedString(mdAudioSamplerate) : "") +
				(! codec.isVideo() ? ", mdAudioChannelCount=" + Integer.toUnsignedString(mdAudioChannelCount) : "") +
				", mdPayloadCRC8=" + String.format("0x%02X", mdPayloadCRC8) +
				", payload.sz=" + payloadDataPtr.getUsed() +
				"]";
	}
}

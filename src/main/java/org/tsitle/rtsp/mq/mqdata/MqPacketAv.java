package org.tsitle.rtsp.mq.mqdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * Audio/video packet.
 * @param codec The codec used for the payload
 * @param isCodecGuessed Whether the codec was guessed or explicitly set
 * @param mdTimestamp Metadata: timestamp of the payload (sample time)
 * @param mdCounter Metadata: packet counter
 * @param mdVideoIsKeyframe Metadata: is this a keyframe? (video only)
 * @param mdVideoResoWidth Metadata: resolution width (video only)
 * @param mdVideoResoHeight Metadata: resolution height (video only)
 * @param mdVideoFps Metadata: frames per second (video only)
 * @param mdVideoBitrate Metadata: bitrate (video only)
 * @param mdPayloadCRC8 Metadata: CRC8 checksum of the payload
 * @param payloadDataPtr Pointer to the payload data buffer
 */
public record MqPacketAv(
			@NonNull MqPacketCodec codec,
			boolean isCodecGuessed,
			long mdTimestamp,
			int mdCounter,
			boolean mdVideoIsKeyframe,
			int mdVideoResoWidth,
			int mdVideoResoHeight,
			int mdVideoFps,
			int mdVideoBitrate,
			byte mdPayloadCRC8,
			@NonNull BufferExt payloadDataPtr
		) {
	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"codec=" + codec +
				(codec.isVideo() ? ", isCodecGuessed=" + (isCodecGuessed ? "T" : "F") : "") +
				", mdTimestamp=" + Long.toUnsignedString(mdTimestamp) +
				", mdCounter=" + Integer.toUnsignedString(mdCounter) +
				(codec.isVideo() ? ", mdVideoIsKeyframe=" + (mdVideoIsKeyframe ? "T" : "F") : "") +
				(codec.isVideo() ? ", mdVideoResoWidth=" + Integer.toUnsignedString(mdVideoResoWidth) : "") +
				(codec.isVideo() ? ", mdVideoResoHeight=" + Integer.toUnsignedString(mdVideoResoHeight) : "") +
				(codec.isVideo() ? ", mdVideoFps=" + Integer.toUnsignedString(mdVideoFps) : "") +
				(codec.isVideo() ? ", mdVideoBitrate=" + Integer.toUnsignedString(mdVideoBitrate) : "") +
				", mdPayloadCRC8=" + String.format("0x%02X", mdPayloadCRC8) +
				", payload.sz=" + payloadDataPtr.getUsed() +
				"]";
	}
}

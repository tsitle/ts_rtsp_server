package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;

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

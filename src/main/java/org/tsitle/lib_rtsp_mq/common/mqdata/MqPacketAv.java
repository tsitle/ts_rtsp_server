package org.tsitle.lib_rtsp_mq.common.mqdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.helpers.FrameRateEnum;
import org.tsitle.lib_xrtxp.common.helpers.ImageDimensions;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;

/**
 * Audio/video packet.
 * @param msgNr Message number
 * @param codec The codec used for the payload
 * @param isCodecGuessed Whether the codec was guessed or explicitly set
 * @param mdTimestampMs Metadata: sample time timestamp of the payload in milliseconds
 * @param mdCounter Metadata: packet counter
 * @param mdVideoIsKeyframe Metadata: is this a keyframe? (video only)
 * @param mdVideoReso Metadata: resolution width and height (video only)
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
			@NonNull ImageDimensions mdVideoReso,
			@NonNull FrameRateEnum mdVideoFps,
			int mdVideoBitrate,
			@NonNull SampleRateEnum mdAudioSamplerate,
			byte mdAudioChannelCount,
			byte mdPayloadCRC8,
			@NonNull BufferExt payloadDataPtr
		) {

	@Override
	public @NonNull String toString() {
		String tmpFpsStr = String.format("%.3f", mdVideoFps.getFrDbl()).replace(",", ".");
		String tmpResoStr = Integer.toUnsignedString(mdVideoReso.imgWidth()) + "x" +
				Integer.toUnsignedString(mdVideoReso.imgHeight());
		return getClass().getSimpleName() + " [" +
				"msgNr=" + Long.toUnsignedString(msgNr) +
				", codec=" + codec +
				(codec.isVideo() ? ", isCodecGuessed=" + (isCodecGuessed ? "T" : "F") : "") +
				", mdTimestampMs=" + Long.toUnsignedString(mdTimestampMs) +
				", mdCounter=" + Integer.toUnsignedString(mdCounter) +
				(codec.isVideo() ? ", mdVideoIsKeyframe=" + (mdVideoIsKeyframe ? "T" : "F") : "") +
				(codec.isVideo() ? ", mdVideoReso=" + tmpResoStr : "") +
				(codec.isVideo() ? ", mdVideoFps=" + tmpFpsStr : "") +
				(codec.isVideo() ? ", mdVideoBitrate=" + Integer.toUnsignedString(mdVideoBitrate) : "") +
				(! codec.isVideo() ? ", mdAudioSamplerate=" + mdAudioSamplerate.getSrHz() : "") +
				(! codec.isVideo() ? ", mdAudioChannelCount=" + Integer.toUnsignedString(mdAudioChannelCount) : "") +
				", mdPayloadCRC8=" + String.format("0x%02X", mdPayloadCRC8) +
				", payload.sz=" + payloadDataPtr.getUsed() +
				"]";
	}

}

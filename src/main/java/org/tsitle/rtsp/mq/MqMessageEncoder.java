package org.tsitle.rtsp.mq;

import org.jspecify.annotations.NonNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Encoder for MQ messages containing video and audio packets.
 */
public class MqMessageEncoder {

	/**
	 * Write the given packet to the given output buffer - but without the payload data.
	 * @param packetAv Packet to encode
	 * @param outputBuffer Output buffer
	 */
	public static void encodePacketAvForInternalMq(final @NonNull MqPacketAv packetAv, @NonNull ByteBuffer outputBuffer) {
		outputBuffer.clear();

		byte[] tmpStrBytes = packetAv.codec().name().getBytes(StandardCharsets.UTF_8);
		outputBuffer.putInt(tmpStrBytes.length);
		outputBuffer.put(tmpStrBytes);
		if (packetAv.codec().isVideo()) {
			outputBuffer.put(packetAv.isCodecGuessed() ? (byte)1 : (byte)0);
		}
		outputBuffer.putLong(packetAv.mdTimestamp());
		outputBuffer.putInt(packetAv.mdCounter());
		if (packetAv.codec().isVideo()) {
			outputBuffer.put(packetAv.mdVideoIsKeyframe() ? (byte)1 : (byte)0);
			outputBuffer.putInt(packetAv.mdVideoResoWidth());
			outputBuffer.putInt(packetAv.mdVideoResoHeight());
			outputBuffer.putInt(packetAv.mdVideoFps());
			outputBuffer.putInt(packetAv.mdVideoBitrate());
		}
		outputBuffer.put(packetAv.mdPayloadCRC8());
		outputBuffer.putInt(packetAv.payloadDataPtr().getUsed());

		outputBuffer.flip();
	}

}

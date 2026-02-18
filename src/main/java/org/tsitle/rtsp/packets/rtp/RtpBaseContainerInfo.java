package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;

/**
 * RTP Base Container info.
 * @param payloadType RTP payload type
 * @param orgPayloadTypeByte Original payload type byte
 * @param ssrcId SSRC identifier
 * @param sequenceNumber RTP sequence number
 * @param isMarkerSet Marker bit flag
 * @param rtpTimestamp RTP timestamp
 */
public record RtpBaseContainerInfo(
		@NonNull RtpPacketType payloadType,
		byte orgPayloadTypeByte,
		int ssrcId,
		short sequenceNumber,
		boolean isMarkerSet,
		int rtpTimestamp
	) { }

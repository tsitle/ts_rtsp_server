package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdXsrc;

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
		@NonNull RtspProtoIdXsrc ssrcId,
		short sequenceNumber,
		boolean isMarkerSet,
		int rtpTimestamp
	) { }

package org.tsitle.rtsp.packets.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdXsrc;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRtpTimestamp;

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
		@NonNull RtspProtoRtpSeqNr sequenceNumber,
		boolean isMarkerSet,
		@NonNull RtspProtoRtpTimestamp rtpTimestamp
	) { }

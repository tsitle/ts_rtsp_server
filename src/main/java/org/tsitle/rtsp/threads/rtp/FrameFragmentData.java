package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoRtpTimestamp;

/**
 * A Frame Fragment is the current part of a frame that is being sent over the wire.
 * @param frameData Complete frame data
 * @param frameRtpTimestamp RTP timestamp
 * @param fragmentOffset Offset of the current fragment in the frame data
 * @param fragmentSize Size of the current fragment in the frame data
 * @param isLastFragment Is this the last fragment of the frame?
 */
public record FrameFragmentData(
		@NonNull FrameData frameData,
		@NonNull RtspProtoRtpTimestamp frameRtpTimestamp,
		int fragmentOffset,
		int fragmentSize,
		int fragmentIndex,
		boolean isLastFragment
	) { }

package org.tsitle.rtsp_server.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;

/**
 * A Frame Fragment is the current part of a frame that is being sent over the wire.
 * @param frameData Complete frame data
 * @param frameRtpTimestamp RTP timestamp
 * @param fragmentOffset Offset of the current fragment in the frame data
 * @param fragmentSize Size of the current fragment in the frame data
 * @param fragmentIndex Index of the current fragment
 * @param fragmentCount Number of fragments
 * @param isLastFragment Is this the last fragment of the frame?
 */
public record FrameFragmentData(
		@NonNull FrameData frameData,
		@NonNull RtspProtoRtpTimestamp frameRtpTimestamp,
		int fragmentOffset,
		int fragmentSize,
		int fragmentIndex,
		int fragmentCount,
		boolean isLastFragment
	) { }

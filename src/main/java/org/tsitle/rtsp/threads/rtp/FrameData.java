package org.tsitle.rtsp.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;

/**
 * Stores the current frame data (e.g., one entire JPEG frame or one entire H264/5 NAL Unit)
 */
public final class FrameData {

	/** The total frame size includes data that might not be part of the RTP payload */
	public int totalFrameSize;
	/** Includes only the RTP payload data */
	public @Nullable BufferExt rtpPayloadDataPtr;
	/** Number of the RTP frame */
	public long rtpFrameNr;
	/** Total size of the Access Unit's RTP frames payloads */
	public long totalAuRtpPayloadSz;
	/** Free-form description of the frame */
	public @NonNull String frameDesc;
	public boolean haveErrorEof;
	public boolean haveErrorOther;
	public @NonNull String errorMsg;

	public FrameData() {
		reset();
	}

	public void reset() {
		totalFrameSize = 0;
		rtpPayloadDataPtr = null;
		rtpFrameNr = -1L;
		totalAuRtpPayloadSz = -1L;
		frameDesc = "";
		haveErrorEof = false;
		haveErrorOther = false;
		errorMsg = "";
	}

}

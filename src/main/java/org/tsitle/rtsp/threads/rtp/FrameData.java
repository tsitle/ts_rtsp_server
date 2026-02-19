package org.tsitle.rtsp.threads.rtp;

import org.tsitle.rtsp.buffers.BufferExt;

/**
 * Stores the current frame data (e.g., one entire JPEG frame or one entire H264/5 NAL Unit)
 */
public final class FrameData {

	/** The total frame size includes data that might not be part of the RTP payload */
	public int totalFrameSize = 0;
	/** Includes only the RTP payload data */
	public BufferExt rtpPayloadDataPtr;
	/** Timestamp of the RTP frame */
	public int rtpFrameTimestamp = 0;
	public boolean haveErrorEof = false;
	public boolean haveErrorOther = false;
	public String errorMsg = "";

	public void reset() {
		totalFrameSize = 0;
		rtpPayloadDataPtr = null;
		rtpFrameTimestamp = 0;
		haveErrorEof = false;
		haveErrorOther = false;
		errorMsg = "";
	}

}

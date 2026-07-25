package org.tsitle.rtsp_server.threads.rtp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.types.TimestampEpochNs;

/**
 * Stores the current frame data (e.g., one entire JPEG frame or one entire H264/5 NAL Unit)
 */
public final class FrameData {

	/** The total frame size includes data that might not be part of the RTP payload */
	public int totalFrameSize;
	/** Includes only the RTP payload data */
	public @Nullable BufferView rtpPayloadDataViewPtr;
	public @NonNull BufferExt rtpPayloadDataForDefFdSupplier = new BufferExt();
	/** Sample-time timestamp of the payload */
	public @NonNull TimestampEpochNs stTimestamp = TimestampEpochNs.ofEmpty();
	/** Number of the RTP frame */
	public long rtpFrameNr;
	/** Total size of the Access Unit's RTP frames payloads */
	public long totalAuRtpPayloadSz;
	/** Free-form description of the frame */
	public @NonNull String frameDesc;
	public boolean haveErrorEos;
	public boolean haveErrorOther;
	public @NonNull String errorMsg;

	public FrameData() {
		reset();
	}

	public void reset() {
		totalFrameSize = 0;
		rtpPayloadDataViewPtr = null;
		rtpPayloadDataForDefFdSupplier.clear();
		stTimestamp.clear();
		rtpFrameNr = -1L;
		totalAuRtpPayloadSz = -1L;
		frameDesc = "";
		haveErrorEos = false;
		haveErrorOther = false;
		errorMsg = "";
	}

}

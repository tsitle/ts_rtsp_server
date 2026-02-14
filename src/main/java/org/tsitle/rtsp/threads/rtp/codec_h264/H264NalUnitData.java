package org.tsitle.rtsp.threads.rtp.codec_h264;

import org.tsitle.rtsp.avdata.H264Info;
import org.tsitle.rtsp.buffers.BufferExt;

public final class H264NalUnitData {

	public int internalId = 0;
	public H264Info h264Info = null;
	public final BufferExt rtpPayloadData = new BufferExt();
	public int fullDataSize = 0;

	public void reset() {
		internalId = 0;
		h264Info = null;
		rtpPayloadData.clear();
		fullDataSize = 0;
	}

	public void moveDataFrom(H264NalUnitData src) {
		if (src == null) {
			throw new IllegalArgumentException("src == null");
		}
		internalId = src.internalId;
		h264Info = src.h264Info;
		rtpPayloadData.copyOf(src.rtpPayloadData);
		fullDataSize = src.fullDataSize;
		//
		src.reset();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" +
				"internalId=" + internalId +
				", h264Info=" + (h264Info == null ? "NULL" : h264Info.toString(true)) +
				", rtpPayloadData.sz=" + rtpPayloadData.getUsed() +
				", fullDataSize=" + fullDataSize +
				"]";
	}

}

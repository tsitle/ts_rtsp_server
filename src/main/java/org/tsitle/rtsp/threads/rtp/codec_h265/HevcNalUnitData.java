package org.tsitle.rtsp.threads.rtp.codec_h265;

import org.tsitle.rtsp.avdata.H265Info;
import org.tsitle.rtsp.buffers.BufferExt;

public final class HevcNalUnitData {

	public int internalId = 0;
	public H265Info h265Info = null;
	public final BufferExt rtpPayloadData = new BufferExt();
	public int fullDataSize = 0;

	public void reset() {
		internalId = 0;
		h265Info = null;
		rtpPayloadData.clear();
		fullDataSize = 0;
	}

	public void moveDataFrom(HevcNalUnitData src) {
		if (src == null) {
			throw new IllegalArgumentException("src == null");
		}
		internalId = src.internalId;
		h265Info = src.h265Info;
		rtpPayloadData.copyOf(src.rtpPayloadData);
		fullDataSize = src.fullDataSize;
		//
		src.reset();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" +
				"internalId=" + internalId +
				", h265Info=" + (h265Info == null ? "NULL" : h265Info.toString(true)) +
				", rtpPayloadData.sz=" + rtpPayloadData.getUsed() +
				", fullDataSize=" + fullDataSize +
				"]";
	}

}

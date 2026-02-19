package org.tsitle.rtsp.threads.rtp.codec_h26x;

import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.buffers.BufferExt;

public final class H26xNalUnitData<I extends CodecInfoInterface<I>> {

	public int internalId = 0;
	public I h26xInfo = null;
	public final BufferExt rtpPayloadData = new BufferExt();
	public int fullDataSize = 0;

	public void reset() {
		internalId = 0;
		h26xInfo = null;
		rtpPayloadData.clear();
		fullDataSize = 0;
	}

	public void moveDataFrom(H26xNalUnitData<I> src) {
		if (src == null) {
			throw new IllegalArgumentException("src == null");
		}
		internalId = src.internalId;
		h26xInfo = src.h26xInfo;
		rtpPayloadData.copyOf(src.rtpPayloadData);  // @TODO
		fullDataSize = src.fullDataSize;
		//
		src.reset();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" +
				"internalId=" + internalId +
				", h26xInfo=" + (h26xInfo == null ? "NULL" : h26xInfo.toString(true)) +
				", rtpPayloadData.sz=" + rtpPayloadData.getUsed() +
				", fullDataSize=" + fullDataSize +
				"]";
	}

}

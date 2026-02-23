package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.buffers.BufferExt;

public final class H26xNalUnitData<I extends CodecInfoInterface<I>> {

	public int internalId = 0;
	public @Nullable I h26xInfo = null;
	public @Nullable BufferExt rtpPayloadDataPtr;
	public int fullDataSize = 0;
	public long rtpFrameNr = -1L;

	public void reset() {
		internalId = 0;
		h26xInfo = null;
		rtpPayloadDataPtr = null;
		fullDataSize = 0;
		rtpFrameNr = -1L;
	}

	public void moveDataFrom(@NonNull H26xNalUnitData<I> src) {
		internalId = src.internalId;
		h26xInfo = src.h26xInfo;
		rtpPayloadDataPtr = src.rtpPayloadDataPtr;
		fullDataSize = src.fullDataSize;
		rtpFrameNr = src.rtpFrameNr;
		//
		src.reset();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" +
				"internalId=" + internalId +
				", h26xInfo=" + (h26xInfo == null ? "NULL" : h26xInfo.toString(true)) +
				", rtpPayloadData.sz=" + (rtpPayloadDataPtr == null ? "NULL" : "" + rtpPayloadDataPtr.getUsed()) +
				", fullDataSize=" + fullDataSize +
				", rtpFrameNr=" + rtpFrameNr +
				"]";
	}

}

package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.buffers.BufferView;

public final class H26xNalUnitData<I extends CodecInfoInterface<I>> {

	public long internalId = 0L;
	public @Nullable I h26xInfo = null;
	public @NonNull BufferExt rawPayloadData = new BufferExt();
	public @NonNull BufferView rtpPayloadDataView = new BufferView(rawPayloadData);
	public long rtpFrameNr = -1L;
	public boolean isEndOfAu = false;

	public void reset() {
		internalId = 0L;
		h26xInfo = null;
		rawPayloadData.clear();
		rtpPayloadDataView.clear();
		rtpFrameNr = -1L;
		isEndOfAu = false;
	}

	@Override
	public @NonNull String toString() {
		return toStringWithNUT("-UNKNOWN-");
	}

	public @NonNull String toStringWithNUT(@NonNull String nudTypeStr) {
		return getClass().getSimpleName() + " [" +
				"internalId=" + Long.toUnsignedString(internalId) +
				", nudType=" + nudTypeStr +
				", h26xInfo=" + (h26xInfo == null ? "NULL" : h26xInfo.toString(true)) +
				", rawPayloadData.sz=" + rawPayloadData.getUsed() +
				", rtpPayloadDataView.sz=" + rtpPayloadDataView.getLength() +
				", rtpFrameNr=" + rtpFrameNr +
				", isEndOfAu=" + (isEndOfAu ? "T" : "F") +
				"]";
	}

}

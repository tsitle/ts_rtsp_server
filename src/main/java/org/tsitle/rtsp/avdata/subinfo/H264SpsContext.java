package org.tsitle.rtsp.avdata.subinfo;

import org.jspecify.annotations.NonNull;

public class H264SpsContext implements Cloneable {

	/** seq_parameter_set_id */
	public int id;
	public int log2MaxFrameNumMinus4;
	public int picOrderCntType;
	public int log2MaxPicOrderCntLsbMinus4;
	public boolean frameMbsOnlyFlag;
	public boolean deltaPicOrderAlwaysZeroFlag;

	public H264SpsContext() {
		reset();
	}

	public void reset() {
		id = -1;
		log2MaxFrameNumMinus4 = 0;
		picOrderCntType = 0;
		log2MaxPicOrderCntLsbMinus4 = 0;
		frameMbsOnlyFlag = false;
		deltaPicOrderAlwaysZeroFlag = false;
	}

	@Override
	public @NonNull H264SpsContext clone() {
		try {
			return (H264SpsContext)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}
}

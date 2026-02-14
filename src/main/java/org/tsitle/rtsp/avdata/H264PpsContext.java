package org.tsitle.rtsp.avdata;

public class H264PpsContext implements Cloneable {

	/** pic_parameter_set_id */
	public int id;
	/** seq_parameter_set_id */
	public int spsId;
	public boolean bottomFieldPicOrderInFramePresentFlag;
	public boolean redundantPicCntPresentFlag;
	public int numSliceGroupsMinus1;

	public H264PpsContext() {
		reset();
	}

	public void reset() {
		id = -1;
		spsId = -1;
		bottomFieldPicOrderInFramePresentFlag = false;
		redundantPicCntPresentFlag = false;
		numSliceGroupsMinus1 = 0;
	}

	@Override
	public H264PpsContext clone() {
		try {
			return (H264PpsContext)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}
}

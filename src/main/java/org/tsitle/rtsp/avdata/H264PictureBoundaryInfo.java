package org.tsitle.rtsp.avdata;

public class H264PictureBoundaryInfo implements Cloneable {

	public int nalRefIdc;
	public int nalUnitType;

	public int frameNum;

	public int picParameterSetId;

	public boolean fieldPicFlag;
	public boolean bottomFieldFlag;

	public boolean idrPicFlag;
	public int idrPicId;

	public int picOrderCntLsb;
	public int deltaPicOrderCntBottom;
	public int deltaPicOrderCnt0;
	public int deltaPicOrderCnt1;

	public H264PictureBoundaryInfo() {
		reset();
	}

	public void reset() {
		nalRefIdc = 0;
		nalUnitType = 0;
		frameNum = 0;
		picParameterSetId = 0;
		fieldPicFlag = false;
		bottomFieldFlag = false;
		idrPicFlag = false;
		idrPicId = 0;
		picOrderCntLsb = 0;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" +
				"nalRefIdc=" + nalRefIdc +
				", nalUnitType=" + nalUnitType +
				", frameNum=" + frameNum +
				", picParameterSetId=" + picParameterSetId +
				", fieldPicFlag=" + fieldPicFlag +
				", bottomFieldFlag=" + bottomFieldFlag +
				", idrPicFlag=" + idrPicFlag +
				", idrPicId=" + idrPicId +
				", picOrderCntLsb=" + picOrderCntLsb +
				", deltaPicOrderCntBottom=" + deltaPicOrderCntBottom +
				", deltaPicOrderCnt0=" + deltaPicOrderCnt0 +
				", deltaPicOrderCnt1=" + deltaPicOrderCnt1 +
				"]";
	}

	@Override
	public H264PictureBoundaryInfo clone() {
		try {
			return (H264PictureBoundaryInfo)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}
}

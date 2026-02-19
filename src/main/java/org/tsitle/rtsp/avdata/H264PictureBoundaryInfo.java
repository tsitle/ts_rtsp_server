package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;

public final class H264PictureBoundaryInfo implements Cloneable {

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
		deltaPicOrderCntBottom = 0;
		deltaPicOrderCnt0 = 0;
		deltaPicOrderCnt1 = 0;
	}

	public void copyOf(@NonNull H264PictureBoundaryInfo other) {
		reset();

		nalRefIdc = other.nalRefIdc;
		nalUnitType = other.nalUnitType;
		frameNum = other.frameNum;
		picParameterSetId = other.picParameterSetId;
		fieldPicFlag = other.fieldPicFlag;
		bottomFieldFlag = other.bottomFieldFlag;
		idrPicFlag = other.idrPicFlag;
		idrPicId = other.idrPicId;
		picOrderCntLsb = other.picOrderCntLsb;
		deltaPicOrderCntBottom = other.deltaPicOrderCntBottom;
		deltaPicOrderCnt0 = other.deltaPicOrderCnt0;
		deltaPicOrderCnt1 = other.deltaPicOrderCnt1;
	}

	@Override
	public H264PictureBoundaryInfo clone() {
		try {
			return (H264PictureBoundaryInfo)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
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

	public String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(nalRefIdc);
		baos.write(nalUnitType);

		baos.write(frameNum);

		baos.write(picParameterSetId);

		baos.write(fieldPicFlag ? 1 : 0);
		baos.write(bottomFieldFlag ? 1 : 0);

		baos.write(idrPicFlag ? 1 : 0);
		baos.write(idrPicId);

		baos.write(picOrderCntLsb);
		baos.write(deltaPicOrderCntBottom);
		baos.write(deltaPicOrderCnt0);
		baos.write(deltaPicOrderCnt1);

		return HashMd5Helper.hashOfBytes(baos.toByteArray());
	}

}

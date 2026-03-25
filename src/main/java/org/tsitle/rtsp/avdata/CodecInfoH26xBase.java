package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;

public abstract class CodecInfoH26xBase<I extends CodecInfoH26xBase<I>> implements CodecInfoInterface<I> {

	/** Offset of the NAL Unit data */
	public int nalUnitOffset;
	/** Length of the NAL Unit data */
	public int nalUnitLength;
	/** NAL Unit Type as byte (5 bits) */
	public byte nalUnitTypeBy;
	/** Is this a VCL NAL Unit? */
	public boolean isVclNalUnit;
	/** For VCL NAL Units: is this the first slice segment in a picture? */
	public boolean isVclFirstSliceSegmentInPic;

	protected CodecInfoH26xBase() {
		internalReset();
	}

	@Override
	public int getPayloadOffset() {
		return nalUnitOffset;
	}

	@Override
	public int getPayloadLength() {
		return nalUnitLength;
	}

	@Override
	public void reset() {
		internalReset();
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<I> src) {
		reset();

		CodecInfoH26xBase<I> tmpSrc = (CodecInfoH26xBase<I>)src;
		nalUnitOffset = tmpSrc.nalUnitOffset;
		nalUnitLength = tmpSrc.nalUnitLength;
		nalUnitTypeBy = tmpSrc.nalUnitTypeBy;
		isVclNalUnit = tmpSrc.isVclNalUnit;
		isVclFirstSliceSegmentInPic = tmpSrc.isVclFirstSliceSegmentInPic;
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(nalUnitOffset);
		baos.write(nalUnitLength);
		baos.write(nalUnitTypeBy);
		baos.write(isVclNalUnit ? 1 : 0);
		baos.write(isVclFirstSliceSegmentInPic ? 1 : 0);

		return HashMd5Helper.hashOfBytes(baos.toByteArray(), true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected @NonNull String getToStringFields() {
		return "offset=" + Integer.toUnsignedString(nalUnitOffset) +
				", length=" + Integer.toUnsignedString(nalUnitLength) +
				String.format(", TypeBy=0x%02X", nalUnitTypeBy) +
				", isVclNalUnit=" + (isVclNalUnit ? "T" : "F") +
				", isVcl1stSSIP=" + (isVclFirstSliceSegmentInPic ? "T" : "F");
	}

	protected @NonNull String getToStringShortFields() {
		return String.format("T=0x%02X", nalUnitTypeBy);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalReset() {
		nalUnitOffset = 0;
		nalUnitLength = 0;
		nalUnitTypeBy = 0;
		isVclNalUnit = false;
		isVclFirstSliceSegmentInPic = false;
	}

}

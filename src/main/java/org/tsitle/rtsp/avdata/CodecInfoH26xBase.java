package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;

public abstract class CodecInfoH26xBase<I extends CodecInfoH26xBase<I, NUT>, NUT> implements CodecInfoInterface<I>, Cloneable {

	/** Offset of the NAL Unit data */
	public int nalUnitOffset;
	/** Length of the NAL Unit data */
	public int nalUnitLength;
	/** NAL Unit Type as byte (5 bits) */
	public byte nalUnitTypeBy;
	/** NAL Unit Type as enum */
	public NUT nalUnitTypeEn;
	/** Is this a VCL NAL Unit? */
	public boolean isVclNalUnit;
	/** For VCL NAL Units: is this the first slice segment in a picture? */
	public boolean isVclFirstSliceSegmentInPic;

	private final NUT nalUnitTypeEnDefault;

	protected CodecInfoH26xBase(NUT nalUnitTypeEnDefault) {
		this.nalUnitTypeEnDefault = nalUnitTypeEnDefault;

		internalReset();
	}

	@Override
	public void reset() {
		internalReset();
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<I> src) {
		reset();

		CodecInfoH26xBase<I, NUT> tmpSrc = (CodecInfoH26xBase<I, NUT>)src;
		nalUnitOffset = tmpSrc.nalUnitOffset;
		nalUnitLength = tmpSrc.nalUnitLength;
		nalUnitTypeBy = tmpSrc.nalUnitTypeBy;
		nalUnitTypeEn = tmpSrc.nalUnitTypeEn;
		isVclNalUnit = tmpSrc.isVclNalUnit;
		isVclFirstSliceSegmentInPic = tmpSrc.isVclFirstSliceSegmentInPic;
	}

	@Override
	public CodecInfoH26xBase<I, NUT> clone() {
		try {
			//noinspection unchecked
			return (CodecInfoH26xBase<I, NUT>)super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

	@Override
	public String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(nalUnitOffset);
		baos.write(nalUnitLength);
		baos.write(nalUnitTypeBy);
		baos.write(nalUnitTypeEn.hashCode());
		baos.write(isVclNalUnit ? 1 : 0);
		baos.write(isVclFirstSliceSegmentInPic ? 1 : 0);

		return HashMd5Helper.hashOfBytes(baos.toByteArray());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected String getToStringFields() {
		return "offset=" + Integer.toUnsignedString(nalUnitOffset) +
				", length=" + Integer.toUnsignedString(nalUnitLength) +
				String.format(", TypeBy=0x%02X", nalUnitTypeBy) +
				", TypeEn=" + nalUnitTypeEn +
				", isVclNalUnit=" + (isVclNalUnit ? "T" : "F") +
				", isVcl1stSSIP=" + (isVclFirstSliceSegmentInPic ? "T" : "F");
	}

	protected String getToStringShortFields() {
		return String.format("T=0x%02X / %s", nalUnitTypeBy, nalUnitTypeEn);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void internalReset() {
		nalUnitOffset = 0;
		nalUnitLength = 0;
		nalUnitTypeBy = 0;
		nalUnitTypeEn = nalUnitTypeEnDefault;
		isVclNalUnit = false;
		isVclFirstSliceSegmentInPic = false;
	}

}

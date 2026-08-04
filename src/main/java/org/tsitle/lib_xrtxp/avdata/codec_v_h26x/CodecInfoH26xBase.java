package org.tsitle.lib_xrtxp.avdata.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class CodecInfoH26xBase<I extends CodecInfoH26xBase<I>> implements CodecInfoInterface<I> {

	/** Is this a valid NAL Unit? */
	public boolean isValid;
	/** Validation error message */
	public @NonNull String validationErrorMsg;
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
	public boolean isValid() {
		return isValid;
	}

	@Override
	public @NonNull String getValidationErrorMsg() {
		return validationErrorMsg;
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
		isValid = tmpSrc.isValid;
		validationErrorMsg = tmpSrc.validationErrorMsg;
		nalUnitOffset = tmpSrc.nalUnitOffset;
		nalUnitLength = tmpSrc.nalUnitLength;
		nalUnitTypeBy = tmpSrc.nalUnitTypeBy;
		isVclNalUnit = tmpSrc.isVclNalUnit;
		isVclFirstSliceSegmentInPic = tmpSrc.isVclFirstSliceSegmentInPic;
	}

	@Override
	public @NonNull String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(isValid ? 1 : 0);
		try {
			baos.write(validationErrorMsg.getBytes());
		} catch (IOException e) {
			// ignore
		}
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
		return "isValid=" + (isValid ? "T" : "F") +
				", validationErrorMsg='" + validationErrorMsg + "'" +
				", offset=" + Integer.toUnsignedString(nalUnitOffset) +
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
		isValid = false;
		validationErrorMsg = "";
		nalUnitOffset = 0;
		nalUnitLength = 0;
		nalUnitTypeBy = 0;
		isVclNalUnit = false;
		isVclFirstSliceSegmentInPic = false;
	}

}

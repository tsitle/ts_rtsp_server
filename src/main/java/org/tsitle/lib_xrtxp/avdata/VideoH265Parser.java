package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;

public final class VideoH265Parser {

	public static final int NAL_UNIT_HEADER_SIZE = 2;

	public VideoH265Parser() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the H265 data and returns an H265Info object with the parsed information.
	 * @param debugStreamOffset Offset of the H265 data in the H265 stream (used for error messages)
	 * @param startCodeLen Length of the start code (3 or 4 bytes for H.265)
	 * @param inputBv H265 data
	 * @return Parsed H265 information
	 */
	public @NonNull VideoH265Info parseH265Data(
				@SuppressWarnings("unused") long debugStreamOffset,
				int startCodeLen,
				@NonNull BufferView inputBv
			) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseH265Data()";

		VideoH265Info resObj = new VideoH265Info();
		resObj.isValid = true;

		resObj.nalUnitOffset = startCodeLen;
		if (inputBv.getLength() < resObj.nalUnitOffset + NAL_UNIT_HEADER_SIZE) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid H265 data size");
		}
		resObj.nalUnitLength = inputBv.getLength() - resObj.nalUnitOffset;
		while (resObj.nalUnitLength > 0 && inputBv.getByte(resObj.nalUnitOffset + resObj.nalUnitLength - 1) == 0) {
			--resObj.nalUnitLength;  // remove trailing zero bytes
		}

		/*
		 * HEVC maintains the NAL unit concept of H.264 with modifications.
		 * HEVC uses a two-byte NAL unit header.
		 *
		 *  +---------------+---------------+
		 *  |0|1|2|3|4|5|6|7|0|1|2|3|4|5|6|7|
		 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
		 *  |F|   Type    |  LayerId  | TID |
		 *  +-------------+-----------------+
		 */

		/*System.out.format("scl=%d: 0x%02X%02X%02X%02X%n",
				startCodeLen,
				inputBv.getByte(0), inputBv.getByte(1),
				inputBv.getByte(2), inputBv.getByte(3));*/
		int offs = resObj.nalUnitOffset;
		BufferView tmpHeaderBv = inputBv.clone();
		tmpHeaderBv.increaseOffset(resObj.nalUnitOffset);
		tmpHeaderBv.setLength(2);
		BitReaderHelper tmpBr = new BitReaderHelper(tmpHeaderBv);
		try {
			int tmpFbit = tmpBr.readBit();
			resObj.nalUnitTypeBy = (byte)tmpBr.readBits(6);
			resObj.nalUnitTypeEn = VideoH265Info.NalUnitType.of(resObj.nalUnitTypeBy);
			resObj.nuhLayerId = (byte)tmpBr.readBits(6);
			resObj.nuhTemporalIdPlus1 = (byte)tmpBr.readBits(3);

			if (tmpFbit != 0) {
				/*System.out.format("scl=%d: 0x%02X%02X%02X%02X %02X%02X%n",
						startCodeLen,
						inputBv.getByte(0), inputBv.getByte(1),
						inputBv.getByte(2), inputBv.getByte(3),
						inputBv.getByte(4), inputBv.getByte(5));*/
				/*throw new AvInvalidCodecDataException(
						String.format("NAL unit F bit must be zero (is=0x%02X) - Type=%s(%d//0x%02x)",
								tmpFbit, resObj.nalUnitTypeEn, resObj.nalUnitTypeBy, resObj.nalUnitTypeBy)
					);*/
				resObj.isValid = false;
			}
		} catch (BitReaderEosException e) {
			throw new AvInvalidCodecDataException("could not read NAL unit header");
		}

		if (resObj.isValid && resObj.nuhLayerId != 0) {
			resObj.isValid = false;
			resObj.validationErrorMsg = String.format("NAL unit layer ID must be zero (is=0x%02X) - Type=%s",
					resObj.nuhLayerId, resObj.nalUnitTypeEn);
		}
		if (resObj.isValid && resObj.nuhTemporalIdPlus1 == 0) {
			resObj.isValid = false;
			resObj.validationErrorMsg = String.format("NAL unit temporal ID must be non-zero (is=0x%02X) - Type=%s",
					resObj.nuhTemporalIdPlus1, resObj.nalUnitTypeEn);
		}

		if (resObj.isValid && VideoH265Info.NalUnitType.isVclNalUnitType(resObj.nalUnitTypeBy)) {
			/*
			 * We don't do 'EBSP' to 'RBSP' (Emulation prevention three bytes) conversion here
			 * since the TemporalIdPlus1 must be non-zero and therefore the first two bytes
			 * of the NAL Unit cannot be 0x0000.
			 * The EBSP to RBSP conversion works like this:
			 *   ... 00 00 03 01 ... --> ... 00 00 01 ...
			 * This is done to prevent having the start code (0x000001) in a NAL Unit.
			 */
			if (inputBv.getLength() < resObj.nalUnitOffset + NAL_UNIT_HEADER_SIZE + 1) {
				throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid H265 data size");
			}
			resObj.isVclFirstSliceSegmentInPic = ((byte)(inputBv.getByte(offs) & 0x80) == (byte)0x80);
			/*debugLog(FNC_NAME, debugStreamOffset, offs,
					String.format("isVclFirstSliceSegmentInPic=%b", resObj.isVclFirstSliceSegmentInPic));*/

			resObj.isVclNalUnit = true;
		} else {
			resObj.isVclNalUnit = false;
		}

		return resObj;
	}

}

package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidH26xDataException;

public class VideoH265Parser {

	public static final int NAL_UNIT_HEADER_SIZE = 2;

	public VideoH265Parser() { }

	/**
	 * Parses the H265 data and returns an H265Info object with the parsed information.
	 * @param debugStreamOffset Offset of the H265 data in the H265 stream (used for error messages)
	 * @param startCodeLen Length of the start code (3 or 4 bytes for H.265)
	 * @param h265Buf H265 data
	 * @return Parsed H265 information
	 */
	public @NonNull VideoH265Info parseH265Data(
				@SuppressWarnings("unused") long debugStreamOffset,
				int startCodeLen,
				@NonNull BufferExt h265Buf
			) throws AvInvalidH26xDataException {
		final String FNC_NAME = VideoH265Parser.class.getSimpleName() + ".parseH265Data()";

		VideoH265Info resObj = new VideoH265Info();

		resObj.nalUnitOffset = startCodeLen;
		if (h265Buf.getUsed() < resObj.nalUnitOffset + NAL_UNIT_HEADER_SIZE) {
			throw new AvInvalidH26xDataException(FNC_NAME + ": Invalid H265 data size");
		}
		resObj.nalUnitLength = h265Buf.getUsed() - resObj.nalUnitOffset;
		while (resObj.nalUnitLength > 0 && h265Buf.get(resObj.nalUnitOffset + resObj.nalUnitLength - 1) == 0) {
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

		/*debugLog(FNC_NAME, debugStreamOffset, 0, String.format("0x%02X%02X",
				h265Buf.getByteAt(0), h265Buf.getByteAt(1)));*/
		int offs = resObj.nalUnitOffset;
		if ((byte)(h265Buf.get(offs) & 0x80) != 0) {
			throw new AvInvalidH26xDataException(
					String.format("NAL unit F bit must be zero (is=0x%02X)", (byte)((h265Buf.get(offs) & 0x80) >> 7))
				);
		}
		resObj.nalUnitTypeBy = (byte)( ((h265Buf.get(offs) & 0x7E) >>> 1) & 0x3F);
		resObj.nalUnitTypeEn = VideoH265Info.NalUnitType.of(resObj.nalUnitTypeBy);
		resObj.nuhLayerId = (byte)( ( ((h265Buf.get(offs++) & 0x01) << 5) |
				((h265Buf.get(offs) & 0xF8) >> 3) ) & 0x3F);
		resObj.nuhTemporalIdPlus1 = (byte)(h265Buf.get(offs++) & 0x07);

		if (resObj.nuhLayerId != 0) {
			throw new AvInvalidH26xDataException(
					String.format("NAL unit layer ID must be zero (is=0x%02X)", resObj.nuhLayerId)
				);
		}
		if (resObj.nuhTemporalIdPlus1 == 0) {
			throw new AvInvalidH26xDataException(
					String.format("NAL unit temporal ID must be non-zero (is=0x%02X)", resObj.nuhTemporalIdPlus1)
				);
		}

		if (VideoH265Info.NalUnitType.isVclNalUnitType(resObj.nalUnitTypeBy)) {
			/*
			 * We don't do 'EBSP' to 'RBSP' (Emulation prevention three bytes) conversion here
			 * since the TemporalIdPlus1 must be non-zero and therefore the first two bytes
			 * of the NAL Unit cannot be 0x0000.
			 * The EBSP to RBSP conversion works like this:
			 *   ... 00 00 03 01 ... --> ... 00 00 01 ...
			 * This is done to prevent having the start code (0x000001) in a NAL Unit.
			 */
			if (h265Buf.getUsed() < resObj.nalUnitOffset + NAL_UNIT_HEADER_SIZE + 1) {
				throw new AvInvalidH26xDataException(FNC_NAME + ": Invalid H265 data size");
			}
			resObj.isVclFirstSliceSegmentInPic = ((byte)(h265Buf.get(offs) & 0x80) == (byte)0x80);
			/*debugLog(FNC_NAME, debugStreamOffset, offs,
					String.format("isVclFirstSliceSegmentInPic=%b", resObj.isVclFirstSliceSegmentInPic));*/

			resObj.isVclNalUnit = true;
		} else {
			resObj.isVclNalUnit = false;
		}

		return resObj;
	}

}

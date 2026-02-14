package org.tsitle.rtsp.avdata;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidH264DataException;

public class H264Parser {

	public static final int NAL_UNIT_HEADER_SIZE = 2;

	/**
	 * Parses the H264 data and returns an H264Info object with the parsed information.
	 * @param debugStreamOffset Offset of the H264 data in the H264 stream (used for error messages)
	 * @param startCodeLen Length of the start code (3 or 4 bytes for H.264)
	 * @param h264Buf H264 data
	 * @return Parsed H264 information
	 */
	public static H264Info parseH264Data(
				@SuppressWarnings("unused") int debugStreamOffset,
				int startCodeLen,
				BufferExt h264Buf
			) throws AvInvalidH264DataException {
		final String FNC_NAME = H264Parser.class.getSimpleName() + ".parseH264Data()";

		H264Info resObj = new H264Info();

		resObj.nalUnitOffset = startCodeLen;
		if (h264Buf.getUsed() < resObj.nalUnitOffset + NAL_UNIT_HEADER_SIZE) {
			throw new AvInvalidH264DataException(FNC_NAME + ": Invalid H264 data size");
		}
		resObj.nalUnitLength = h264Buf.getUsed() - resObj.nalUnitOffset;

		/*
		 * HEVC maintains the NAL unit concept of H.264 with modifications.
		 * HEVC uses a two-byte NAL unit header.
		 *
		 * +---------------+---------------+
		 *  |0|1|2|3|4|5|6|7|0|1|2|3|4|5|6|7|
		 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
		 *  |F|   Type    |  LayerId  | TID |
		 *  +-------------+-----------------+
		 */

		/*debugLog(FNC_NAME, debugStreamOffset, 0, String.format("0x%02X%02X",
				h264Buf.getByteAt(0), h264Buf.getByteAt(1)));*/
		int offs = resObj.nalUnitOffset;
		if ((byte)(h264Buf.get(offs) & 0x80) != 0) {
			throw new AvInvalidH264DataException(
					String.format("NAL unit F bit must be zero (is=0x%02X)", (byte)((h264Buf.get(offs) & 0x80) >> 7))
				);
		}
		resObj.nalUnitTypeBy = (byte)( ((h264Buf.get(offs) & 0x7E) >>> 1) & 0x3F);
		resObj.nalUnitTypeEn = H264Info.NalUnitType.of(resObj.nalUnitTypeBy);
		resObj.nuhLayerId = (byte)( ( ((h264Buf.get(offs++) & 0x01) << 5) |
				((h264Buf.get(offs) & 0xF8) >> 3) ) & 0x3F);
		resObj.nuhTemporalIdPlus1 = (byte)(h264Buf.get(offs++) & 0x07);

		if (resObj.nuhLayerId != 0) {
			throw new AvInvalidH264DataException(
					String.format("NAL unit layer ID must be zero (is=0x%02X)", resObj.nuhLayerId)
				);
		}
		if (resObj.nuhTemporalIdPlus1 == 0) {
			throw new AvInvalidH264DataException(
					String.format("NAL unit temporal ID must be non-zero (is=0x%02X)", resObj.nuhTemporalIdPlus1)
				);
		}

		if (H264Info.NalUnitType.isVclNalUnitType(resObj.nalUnitTypeBy)) {
			/*
			 * We don't do 'EBSP' to 'RBSP' (Emulation prevention three bytes) conversion here
			 * since the TemporalIdPlus1 must be non-zero and therefore the first two bytes
			 * of the NAL Unit cannot be 0x0000.
			 * The EBSP to RBSP conversion works like this:
			 *   ... 00 00 03 01 ... --> ... 00 00 01 ...
			 * This is done to prevent having the start code (0x000001) in a NAL Unit.
			 */
			if (h264Buf.getUsed() < resObj.nalUnitOffset + NAL_UNIT_HEADER_SIZE + 1) {
				throw new AvInvalidH264DataException(FNC_NAME + ": Invalid H264 data size");
			}
			if (h264Buf.get(offs) == (byte)0x03) {
				throw new AvInvalidH264DataException(FNC_NAME + ": Maybe need EBSP to RBSP conversion");
			}
			resObj.isVclFirstSliceSegmentInPic = ((byte)(h264Buf.get(offs) & 0x80) == (byte)0x80);
			/*debugLog(FNC_NAME, debugStreamOffset, offs,
					String.format("isVclFirstSliceSegmentInPic=%b", resObj.isVclFirstSliceSegmentInPic));*/

			resObj.isVclNalUnit = true;
		} else {
			resObj.isVclNalUnit = false;
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private static void debugLog(
				String fncName,
				int debugStreamOffset,
				@SuppressWarnings("SameParameterValue") int offset,
				String msg
			) {
		//if (debugStreamOffset != 0) { return; }
		System.out.format("%s: __ @ 0x%08X: %s%n", fncName, debugStreamOffset + offset, msg);
	}

}

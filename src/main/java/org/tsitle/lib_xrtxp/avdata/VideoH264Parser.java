package org.tsitle.lib_xrtxp.avdata;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PictureBoundaryInfo;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PpsContext;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264SpsContext;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;

import java.util.Map;

public final class VideoH264Parser {

	public static final int NAL_UNIT_HEADER_SIZE = 1;

	private final @NonNull Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext;
	private final @NonNull Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext;
	/** Buffer used to store temporary data for parsing NAL Units */
	private final BufferExt cacheH264RbspBuf = new BufferExt();

	/**
	 * Constructor.
	 * @param mapSpsContext Input: Already read SPS contexts
	 * @param mapPpsContext Input: Already read PPS contexts
	 */
	public VideoH264Parser(
				@NonNull Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext,
				@NonNull Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext
			) {
		this.mapSpsContext = mapSpsContext;
		this.mapPpsContext = mapPpsContext;
	}

	/**
	 * Parses the H264 data and returns an H264Info object with the parsed information.
	 * @param debugStreamOffset Offset of the H264 data in the H264 stream (used for error messages)
	 * @param startCodeLen Length of the start code (3 or 4 bytes for H.264)
	 * @param h264Buf H264 data
	 * @param inpPictBoundInfoPrev Input: previous picture boundary information (can be null)
	 * @return Parsed H264 information
	 */
	public @NonNull VideoH264Info parseH264Data(
				@SuppressWarnings("unused") long debugStreamOffset,
				int startCodeLen,
				@NonNull BufferExt h264Buf,
				@Nullable H264PictureBoundaryInfo inpPictBoundInfoPrev
			) throws AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseH264Data()";

		VideoH264Info resObj = new VideoH264Info();

		resObj.nalUnitOffset = startCodeLen;
		if (h264Buf.getUsed() < resObj.nalUnitOffset + NAL_UNIT_HEADER_SIZE) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid H264 data size");
		}
		resObj.nalUnitLength = h264Buf.getUsed() - resObj.nalUnitOffset;
		while (resObj.nalUnitLength > 0 && h264Buf.get(resObj.nalUnitOffset + resObj.nalUnitLength - 1) == 0) {
			--resObj.nalUnitLength;  // remove trailing zero bytes
		}

		/*
		 * H264 uses a one-byte NAL unit header.
		 *
		 *  +---------------+
		 *  |0|1|2|3|4|5|6|7|
		 *  +-+-+-+-+-+-+-+-+
		 *  |F|NRI|  Type   |
		 *  +---------------+
		 */

		/*logDebugTemp(FNC_NAME, debugStreamOffset, 0, String.format("0x%02X", h264Buf.getByteAt(0)));*/
		int offs = resObj.nalUnitOffset;
		if ((byte)(h264Buf.get(offs) & 0x80) != 0) {
			throw new AvInvalidCodecDataException(
					String.format("NAL unit F bit must be zero (is=0x%02X)", (byte)((h264Buf.get(offs) & 0x80) >> 7))
				);
		}
		resObj.nuhRefIdc = (byte)( ((h264Buf.get(offs) & 0x60) >>> 5) & 0x03);
		resObj.nalUnitTypeBy = (byte)(h264Buf.get(offs) & 0x1F);
		resObj.nalUnitTypeEn = VideoH264Info.NalUnitType.of(resObj.nalUnitTypeBy);

		// prepare RBSP decoded data
		cacheH264RbspBuf.clear();
		if (VideoH264Info.NalUnitType.isVclNalUnitType(resObj.nalUnitTypeBy) ||
				resObj.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_SPS ||
				resObj.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_PPS) {
			removeEmulationPreventionBytes(
					h264Buf,
					resObj.nalUnitOffset,
					Math.min(20, resObj.nalUnitLength),  // convert a maximum of 20 bytes
					cacheH264RbspBuf
				);
		}
		//
		try {
			if (VideoH264Info.NalUnitType.isVclNalUnitType(resObj.nalUnitTypeBy)) {
				if (haveAllRequiredMetadataPackets()) {
					parseSliceForBoundary(
							cacheH264RbspBuf,
							resObj.pictBoundInfo
						);
					resObj.isVclFirstSliceSegmentInPic = isFirstVclOfNewPicture(inpPictBoundInfoPrev, resObj.pictBoundInfo);
					/*logDebugTemp(FNC_NAME, debugStreamOffset, offs,
							String.format("isVclFirstSliceSegmentInPic=%b", resObj.isVclFirstSliceSegmentInPic));*/
				} else {
					resObj.isVclFirstSliceSegmentInPic = false;  // we pretend to know that it is not the 1stSliceSegmentInPic
				}
				resObj.isVclNalUnit = true;
			} else {
				if (resObj.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_SPS) {
					H264SpsContext tmpSpsContext = new H264SpsContext();
					parseSps(cacheH264RbspBuf, tmpSpsContext);
					mapSpsContext.put(tmpSpsContext.id, tmpSpsContext);
				} else if (resObj.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_PPS) {
					H264PpsContext tmpPpsContext = new H264PpsContext();
					parsePps(cacheH264RbspBuf, tmpPpsContext);
					mapPpsContext.put(tmpPpsContext.id, tmpPpsContext);
				}
				resObj.isVclNalUnit = false;
			}
		} catch (BitReaderEosException e) {
			throw new AvInvalidCodecDataException(FNC_NAME + ": Invalid H264 data size");
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public boolean haveAllRequiredMetadataPackets() {
		return (! mapSpsContext.isEmpty());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void removeEmulationPreventionBytes(
				@NonNull BufferExt inputEbsp,
				int srcOffset,
				int srcLen,
				@NonNull BufferExt outputRbsp
			) {
		if (inputEbsp.getUsed() < 3) {
			outputRbsp.copyOf(inputEbsp);
			return;
		}

		outputRbsp.clear();
		outputRbsp.increaseSize(srcLen);  // reserve enough space

		int zeroCount = 0;

		for (int i = srcOffset; i < srcOffset + srcLen; i++) {
			byte b = inputEbsp.get(i);

			if (zeroCount == 2 && b == 0x03) {
				// skip this emulation prevention byte
				zeroCount = 0;
				continue;
			}

			outputRbsp.append(b);

			if (b == 0x00) {
				zeroCount++;
			} else {
				zeroCount = 0;
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void parseSps(
				@NonNull BufferExt nalDataRbsp,
				@NonNull H264SpsContext outSpsContext
			) throws BitReaderEosException {
		outSpsContext.reset();

		BitReaderHelper br = new BitReaderHelper(nalDataRbsp, NAL_UNIT_HEADER_SIZE);

		// profile_idc
		br.readBits(8);

		// constraint flags + reserved
		br.readBits(8);

		// level_idc
		br.readBits(8);

		// seq_parameter_set_id
		outSpsContext.id = br.readH26xUE();

		// High profile handling (skip if baseline)
		int profileIdc = nalDataRbsp.get(NAL_UNIT_HEADER_SIZE) & 0xFF;  // or store earlier
		if (spsIsHighProfile(profileIdc)) {
			int chromaFormatIdc = br.readH26xUE();
			if (chromaFormatIdc == 3) {
				br.readBit();  // separate_colour_plane_flag
			}
			br.readH26xUE();  // bit_depth_luma_minus8
			br.readH26xUE();  // bit_depth_chroma_minus8
			br.readBit();  // qpprime_y_zero_transform_bypass_flag

			boolean scalingMatrixPresent = br.readBit() == 1;
			if (scalingMatrixPresent) {
				spsSkipScalingLists(br);
			}
		}

		outSpsContext.log2MaxFrameNumMinus4 = br.readH26xUE();

		outSpsContext.picOrderCntType = br.readH26xUE();

		if (outSpsContext.picOrderCntType == 0) {
			outSpsContext.log2MaxPicOrderCntLsbMinus4 = br.readH26xUE();
		} else if (outSpsContext.picOrderCntType == 1) {
			outSpsContext.deltaPicOrderAlwaysZeroFlag = br.readBit() == 1;
			br.readH26xSE();  // offset_for_non_ref_pic
			br.readH26xSE();  // offset_for_top_to_bottom_field

			int numRefFramesInPicOrderCntCycle = br.readH26xUE();
			for (int i = 0; i < numRefFramesInPicOrderCntCycle; i++) {
				br.readH26xSE();
			}
		}

		br.readH26xUE();  // max_num_ref_frames
		br.readBit();  // gaps_in_frame_num_value_allowed_flag

		br.readH26xUE();  // pic_width_in_mbs_minus1
		br.readH26xUE();  // pic_height_in_map_units_minus1

		outSpsContext.frameMbsOnlyFlag = br.readBit() == 1;

		if (! outSpsContext.frameMbsOnlyFlag) {
			br.readBit();  // mb_adaptive_frame_field_flag
		}

		br.readBit();  // direct_8x8_inference_flag
	}

	private static boolean spsIsHighProfile(int profileIdc) {
		return (profileIdc == 100 || profileIdc == 110 ||
				profileIdc == 122 || profileIdc == 244 ||
				profileIdc == 44  || profileIdc == 83  ||
				profileIdc == 86  || profileIdc == 118 ||
				profileIdc == 128 || profileIdc == 138 ||
				profileIdc == 144);
	}

	private static void spsSkipScalingLists(@NonNull BitReaderHelper br) throws BitReaderEosException {
		int count = 8;  // minimal safe skip for boundary parsing
		for (int i = 0; i < count; i++) {
			@SuppressWarnings("unused")
			boolean present = (br.readBit() == 1);
			//if (present) {
				// Proper scaling list parsing omitted (not needed for AU detection)
			//}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void parsePps(
				@NonNull BufferExt nalDataRbsp,
				@NonNull H264PpsContext ppsContext
			) throws BitReaderEosException {
		ppsContext.reset();

		BitReaderHelper br = new BitReaderHelper(nalDataRbsp, NAL_UNIT_HEADER_SIZE);

		ppsContext.id = br.readH26xUE();
		ppsContext.spsId = br.readH26xUE();

		br.readBit();  // entropy_coding_mode_flag

		ppsContext.bottomFieldPicOrderInFramePresentFlag = (br.readBit() == 1);

		ppsContext.numSliceGroupsMinus1 = br.readH26xUE();

		if (ppsContext.numSliceGroupsMinus1 > 0) {
			@SuppressWarnings("unused")
			int sliceGroupMapType = br.readH26xUE();
			// Full FMO parsing omitted — not required for AU detection
			// In production, handle slice groups if required.
		}

		br.readH26xUE();  // num_ref_idx_l0_default_active_minus1
		br.readH26xUE();  // num_ref_idx_l1_default_active_minus1

		br.readBit();  // weighted_pred_flag
		br.readBits(2);  // weighted_bipred_idc

		br.readH26xSE();  // pic_init_qp_minus26
		br.readH26xSE();  // pic_init_qs_minus26
		br.readH26xSE();  // chroma_qp_index_offset

		br.readBit();  // deblocking_filter_control_present_flag
		br.readBit();  // constrained_intra_pred_flag

		ppsContext.redundantPicCntPresentFlag = (br.readBit() == 1);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseSliceForBoundary(
				@NonNull BufferExt nalDataRbsp,
				@NonNull H264PictureBoundaryInfo outPictBoundInfo
			) throws BitReaderEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseSliceForBoundary()";

		outPictBoundInfo.reset();

		int currentOffs = 0;
		int header = nalDataRbsp.get(currentOffs++) & 0xFF;

		outPictBoundInfo.nalRefIdc = (header >> 5) & 0x03;
		outPictBoundInfo.nalUnitType = header & 0x1F;
		outPictBoundInfo.idrPicFlag = (outPictBoundInfo.nalUnitType == 5);

		BitReaderHelper br = new BitReaderHelper(nalDataRbsp, currentOffs);

		// first_mb_in_slice
		br.readH26xUE();

		// slice_type
		br.readH26xUE();

		// pic_parameter_set_id
		outPictBoundInfo.picParameterSetId = br.readH26xUE();

		//
		int tmpSpsId = 0;  // we assume that the SPS ID is 0 as long as we haven't received the PPS context
		if (! mapPpsContext.isEmpty()) {
			if (! mapPpsContext.containsKey(outPictBoundInfo.picParameterSetId)) {
				throw new IllegalArgumentException(FNC_NAME + ": PPS context not found for ID=" +
						outPictBoundInfo.picParameterSetId);
			}
			H264PpsContext tmpPpsContext = mapPpsContext.get(outPictBoundInfo.picParameterSetId);
			tmpSpsId = tmpPpsContext.spsId;
		}
		if (! mapSpsContext.containsKey(tmpSpsId)) {
			throw new IllegalArgumentException(FNC_NAME + ": SPS context not found for ID=" + tmpSpsId);
		}
		H264SpsContext tmpSpsContext = mapSpsContext.get(tmpSpsId);

		int frameNumBits = tmpSpsContext.log2MaxFrameNumMinus4 + 4;
		outPictBoundInfo.frameNum = br.readBits(frameNumBits);

		if (! tmpSpsContext.frameMbsOnlyFlag) {
			outPictBoundInfo.fieldPicFlag = br.readBit() == 1;
			if (outPictBoundInfo.fieldPicFlag) {
				outPictBoundInfo.bottomFieldFlag = br.readBit() == 1;
			}
		}

		if (outPictBoundInfo.idrPicFlag) {
			outPictBoundInfo.idrPicId = br.readH26xUE();
		}

		if (tmpSpsContext.picOrderCntType == 0) {
			int pocBits = tmpSpsContext.log2MaxPicOrderCntLsbMinus4 + 4;
			outPictBoundInfo.picOrderCntLsb = br.readBits(pocBits);

			// delta_pic_order_cnt_bottom may exist (needs the PPS flag in full implementation)
			outPictBoundInfo.deltaPicOrderCntBottom = 0;  // requires PPS flag check
		}

		if (tmpSpsContext.picOrderCntType == 1 && ! tmpSpsContext.deltaPicOrderAlwaysZeroFlag) {
			outPictBoundInfo.deltaPicOrderCnt0 = br.readH26xSE();
			outPictBoundInfo.deltaPicOrderCnt1 = br.readH26xSE();
		}
	}

	private static boolean isFirstVclOfNewPicture(
				@Nullable H264PictureBoundaryInfo prev,
				@NonNull H264PictureBoundaryInfo curr
			) {
		if (prev == null) { return true; }

		if (curr.frameNum != prev.frameNum) { return true; }
		if (curr.picParameterSetId != prev.picParameterSetId) { return true; }
		if (curr.fieldPicFlag != prev.fieldPicFlag) { return true; }
		if (curr.bottomFieldFlag != prev.bottomFieldFlag) { return true; }
		if (curr.nalRefIdc != prev.nalRefIdc) { return true; }
		if (curr.idrPicFlag && prev.idrPicFlag && curr.idrPicId != prev.idrPicId) { return true; }

		if (curr.picOrderCntLsb != prev.picOrderCntLsb) { return true; }
		if (curr.deltaPicOrderCnt0 != prev.deltaPicOrderCnt0) { return true; }
		//noinspection RedundantIfStatement
		if (curr.deltaPicOrderCnt1 != prev.deltaPicOrderCnt1) { return true; }

		return false;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/*private void logDebugTemp(String fncName, long debugStreamOffset, int curOffset, String msg) {
		//if (debugStreamOffset != 0) { return; }
		System.out.println(fncName + ": " + debugStreamOffset + "+" + curOffset + ": " + msg);
	}*/

}

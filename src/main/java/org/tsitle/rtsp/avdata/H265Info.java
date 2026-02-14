package org.tsitle.rtsp.avdata;

public class H265Info {

	/**
	 * NAL Unit Types<br />
	 * VCL: Video Coding Layer. NAL Unit Types 0 .. 31<br />
	 * Non-VCL: Metadata like parameter sets or SEI. NAL Unit Types 32 .. 47
	 */
	public enum NalUnitType {
		/** VCL: Trailing pictures (non-reference) */
		VCL_TRAIL_N(0),
		/** VCL: Trailing pictures (reference) */
		VCL_TRAIL_R(1),

		/** VCL: Temporal sublayer access (non-reference) */
		VCL_TSA_N(2),
		/** VCL: Temporal sublayer access (reference) */
		VCL_TSA_R(3),

		/** VCL: Stepwise temporal sublayer access (non-reference) */
		VCL_STSA_N(4),
		/** VCL: Stepwise temporal sublayer access (reference) */
		VCL_STSA_R(5),

		/** VCL: Random Access Decodable Leading pictures (non-reference) */
		VCL_RADL_N(6),
		/** VCL: Random Access Decodable Leading pictures (reference) */
		VCL_RADL_R(7),

		/** VCL: Random Access Skipped Leading pictures (non-reference) */
		VCL_RASL_N(8),
		/** VCL: Random Access Skipped Leading pictures (reference) */
		VCL_RASL_R(9),

		/** VCL: Broken Link Access pictures (W_LP) */
		VCL_BLA_W_LP(16),
		/** VCL: Broken Link Access pictures (W_RADL) */
		VCL_BLA_W_RADL(17),
		/** VCL: Broken Link Access pictures (N_LP) */
		VCL_BLA_N_LP(18),

		/** VCL: Instantaneous Decoder Refresh (key frames, W_RADL) */
		VCL_IDR_W_RADL(19),
		/** VCL: Instantaneous Decoder Refresh (key frames, N_LP) */
		VCL_IDR_N_LP(20),

		/** VCL: Clean Random Access pictures */
		VCL_CRA_NUT(21),

		/** Non-VCL: Video Parameter Set (defines overall stream constraints) */
		NVCL_VPS(32),
		/** Non-VCL: Sequence Parameter Set (sequence-level properties) */
		NVCL_SPS(33),
		/** Non-VCL: Picture Parameter Set (picture-level properties) */
		NVCL_PPS(34),
		/** Non-VCL: Access Unit Delimiter */
		NVCL_AUD(35),
		/** Non-VCL: End of Sequence */
		NVCL_EOS(36),
		/** Non-VCL: End of Bitstream */
		NVCL_EOB(37),
		/** Non-VCL: Filler Data */
		NVCL_FD(38),
		/** Non-VCL: Supplemental Enhancement Information (prefix) */
		NVCL_SEI_PREFIX(39),
		/** Non-VCL: Supplemental Enhancement Information (suffix) */
		NVCL_SEI_SUFFIX(40),

		/** Unknown type */
		UNKNOWN(0xFF);

		private final byte value;
		private static final byte VCL_NAL_UNIT_TYPES_BOUND_LOWER = 0;  // 'TRAIL_N'
		private static final byte VCL_NAL_UNIT_TYPES_BOUND_UPPER = 31;  // unknown / not specified

		NalUnitType(int value) {
			this.value = (byte)value;
		}
		public byte getValue() {
			return value;
		}
		public static NalUnitType of(byte value) {
			for (NalUnitType type : NalUnitType.values()) {
				if (type.getValue() == value) {
					return type;
				}
			}
			return UNKNOWN;
		}
		@SuppressWarnings("unused")
		public boolean isVclNalUnitType() {
			return NalUnitType.isVclNalUnitType(value);
		}
		public static boolean isVclNalUnitType(byte value) {
			return (value >= VCL_NAL_UNIT_TYPES_BOUND_LOWER) && (value <= VCL_NAL_UNIT_TYPES_BOUND_UPPER);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Offset of the NAL Unit data */
	public int nalUnitOffset;
	/** Length of the NAL Unit data */
	public int nalUnitLength;
	/** NAL Unit Type as byte (6 bits) */
	public byte nalUnitTypeBy;
	/** NAL Unit Type as enum */
	public NalUnitType nalUnitTypeEn;
	/** Layer ID, required to be equal to zero (6 bits) */
	public byte nuhLayerId;
	/** Temporal identifier of the NAL unit plus 1, required to be unequal to zero (3 bits) */
	public byte nuhTemporalIdPlus1;
	/** Is this a VCL NAL Unit? */
	public boolean isVclNalUnit;
	/** For VCL NAL Units: is this the first slice segment in a picture? */
	public boolean isVclFirstSliceSegmentInPic;

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"[" +
				"offset=" + Integer.toUnsignedString(nalUnitOffset) +
				", length=" + Integer.toUnsignedString(nalUnitLength) +
				String.format(", TypeBy=0x%02X", nalUnitTypeBy) +
				", TypeEn=" + nalUnitTypeEn +
				String.format(", LayerId=0x%02X", nuhLayerId) +
				String.format(", TID=0x%02X", nuhTemporalIdPlus1) +
				", isVclNalUnit=" + (isVclNalUnit ? "T" : "F") +
				", isVcl1stSSIP=" + (isVclFirstSliceSegmentInPic ? "T" : "F") +
				"]";
	}

	public String toString(boolean shortOutput) {
		if (! shortOutput) {
			return toString();
		}
		return getClass().getSimpleName() +
				"[" +
				String.format("T=0x%02X / %s", nalUnitTypeBy, nalUnitTypeEn) +
				", isVcl1stSSIP=" + (isVclFirstSliceSegmentInPic ? "T" : "F") +
				"]";
	}

}

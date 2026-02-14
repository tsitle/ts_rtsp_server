package org.tsitle.rtsp.avdata;

public class H264Info {

	/**
	 * NAL Unit Types<br />
	 * VCL: Video Coding Layer. NAL Unit Types 1 .. 5<br />
	 * Non-VCL: Metadata like parameter sets or SEI. NAL Unit Types 6 .. 23<br />
	 * See <a href="https://membrane.stream/learn/h264/6">Membrane - H264 Appendix</a>
	 */
	public enum NalUnitType {
		/** Unspecified */
		NVCL_UNSPECIFIED(0),

		/** VCL: Coded slice of a non-IDR picture  */
		VCL_SLICE_NIDR(1),
		/** VCL: Coded slice data partition A */
		VCL_SLICE_DP_A(2),
		/** VCL: Coded slice data partition B */
		VCL_SLICE_DP_B(3),
		/** VCL: Coded slice data partition C */
		VCL_SLICE_DP_C(4),
		/** VCL: Coded slice of an IDR picture */
		VCL_SLICE_IDR(5),

		/** Non-VCL: SEI - Supplemental enhancement information */
		NVCL_SEI(6),
		/** Non-VCL: SPS - Sequence parameter set */
		NVCL_SPS(7),
		/** Non-VCL: PPS - Picture parameter set */
		NVCL_PPS(8),
		/** Non-VCL: AUD - Access Unit Delimiter */
		NVCL_AUD(9),
		/** Non-VCL: EOS - End of Sequence */
		NVCL_EOS(10),
		/** Non-VCL: EOB - End of Bitstream */
		NVCL_EOB(11),
		/** Non-VCL: FD - Filler Data */
		NVCL_FD(12),
		/** Non-VCL: Sequence parameter set extension */
		NVCL_SPSE(13),
		/** Non-VCL: Prefix NAL unit */
		NVCL_PNU(14),
		/** Non-VCL: Subset sequence parameter set */
		NVCL_SSPS(15),
		/** Non-VCL: Reserved */
		NVCL_RESERVED1(16),
		/** Non-VCL: Reserved */
		NVCL_RESERVED2(17),
		/** Non-VCL: Reserved */
		NVCL_RESERVED3(18),
		/** Non-VCL: Coded slice of an auxiliary coded picture without partitioning */
		NVCL_CSACPWP(19),
		/** Non-VCL: Coded slice extension */
		NVCL_CSE(20),
		/** Non-VCL: Coded slice extension for depth view components */
		NVCL_CSE_DVC(21),
		/** Non-VCL: Reserved */
		NVCL_RESERVED4(22),
		/** Non-VCL: Reserved */
		NVCL_RESERVED5(23),

		/** Unknown type */
		UNKNOWN(0xFF);

		private final byte value;
		private static final byte VCL_NAL_UNIT_TYPES_BOUND_LOWER = 1;  // 'SLICE_NIDR'
		private static final byte VCL_NAL_UNIT_TYPES_BOUND_UPPER = 5;  // 'SLICE_IDR'

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
	/** NAL Unit Type as byte (5 bits) */
	public byte nalUnitTypeBy;
	/** NAL Unit Type as enum */
	public NalUnitType nalUnitTypeEn = NalUnitType.UNKNOWN;
	/** Ref IDC - indicates importance: 0=not used for reference, >0=used for reference (2 bits) */
	public byte nuhRefIdc;
	/** Is this a VCL NAL Unit? */
	public boolean isVclNalUnit;
	/** For VCL NAL Units: is this the first slice segment in a picture? */
	public boolean isVclFirstSliceSegmentInPic;
	/** Picture boundary information */
	public final H264PictureBoundaryInfo pictBoundInfo = new H264PictureBoundaryInfo();

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"[" +
				"offset=" + Integer.toUnsignedString(nalUnitOffset) +
				", length=" + Integer.toUnsignedString(nalUnitLength) +
				String.format(", TypeBy=0x%02X", nalUnitTypeBy) +
				", TypeEn=" + nalUnitTypeEn +
				String.format(", RefIdc=0x%02X", nuhRefIdc) +
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

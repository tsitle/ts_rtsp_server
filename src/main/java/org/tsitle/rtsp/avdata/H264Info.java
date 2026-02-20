package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class H264Info extends CodecInfoH26xBase<H264Info> implements Cloneable {

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

	/** NAL Unit Type as enum */
	public NalUnitType nalUnitTypeEn;
	/** Ref IDC - indicates importance: 0=not used for reference, >0=used for reference (2 bits) */
	public byte nuhRefIdc;
	/** Picture boundary information */
	public H264PictureBoundaryInfo pictBoundInfo = new H264PictureBoundaryInfo();

	public H264Info() {
		super();
		reset();
	}

	@Override
	public void reset() {
		super.reset();

		nalUnitTypeEn = NalUnitType.UNKNOWN;
		nuhRefIdc = 0;
		pictBoundInfo.reset();
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<H264Info> src) {
		super.copyOf(src);

		H264Info tmpSrc = (H264Info)src;
		nalUnitTypeEn = tmpSrc.nalUnitTypeEn;
		nuhRefIdc = tmpSrc.nuhRefIdc;
		pictBoundInfo.copyOf(tmpSrc.pictBoundInfo);
	}

	@Override
	public H264Info clone() {
		try {
			H264Info clone = (H264Info)super.clone();
			clone.pictBoundInfo = pictBoundInfo.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"[" +
				super.getToStringFields() +
				", TypeEn=" + nalUnitTypeEn +
				String.format(", RefIdc=0x%02X", nuhRefIdc) +
				"]";
	}

	@Override
	public String toString(boolean shortOutput) {
		if (! shortOutput) {
			return toString();
		}
		return getClass().getSimpleName() +
				"[" +
				super.getToStringShortFields() +
				String.format(" (en=%s)", nalUnitTypeBy) +
				"]";
	}

	@Override
	public String hashSum() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try {
			baos.write(super.hashSum().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		baos.write(nalUnitTypeEn.hashCode());
		baos.write(nuhRefIdc);
		try {
			baos.write(pictBoundInfo.hashSum().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return HashMd5Helper.hashOfBytes(baos.toByteArray());
	}

}

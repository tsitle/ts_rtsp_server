package org.tsitle.rtsp.avdata;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.helpers.HashMd5Helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class H265Info extends CodecInfoH26xBase<H265Info, H265Info.NalUnitType> implements Cloneable {

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
		VCL_CRA(21),

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

	/** Layer ID, required to be equal to zero (6 bits) */
	public byte nuhLayerId;
	/** Temporal identifier of the NAL unit plus 1, required to be unequal to zero (3 bits) */
	public byte nuhTemporalIdPlus1;

	public H265Info() {
		super(H265Info.NalUnitType.UNKNOWN);
		reset();
	}

	@Override
	public void reset() {
		super.reset();

		nuhLayerId = 0;
		nuhTemporalIdPlus1 = 0;
	}

	@Override
	public void copyOf(@NonNull CodecInfoInterface<H265Info> src) {
		super.copyOf(src);

		H265Info tmpSrc = (H265Info)src;
		nuhLayerId = tmpSrc.nuhLayerId;
		nuhTemporalIdPlus1 = tmpSrc.nuhTemporalIdPlus1;
	}

	@Override
	public H265Info clone() {
		return (H265Info)super.clone();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() +
				"[" +
				super.getToStringFields() +
				String.format(", LayerId=0x%02X", nuhLayerId) +
				String.format(", TID=0x%02X", nuhTemporalIdPlus1) +
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
		baos.write(nuhLayerId);
		baos.write(nuhTemporalIdPlus1);

		return HashMd5Helper.hashOfBytes(baos.toByteArray());
	}

}

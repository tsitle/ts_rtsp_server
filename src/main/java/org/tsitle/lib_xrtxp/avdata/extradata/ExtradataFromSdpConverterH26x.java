package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

final class ExtradataFromSdpConverterH26x {

	private ExtradataFromSdpConverterH26x() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static @NonNull String encodeH26xNalUnitListBase64ToHex_annexB(@NonNull String inputNuListB64) {
		StringBuilder sb = new StringBuilder();
		for (String tmp : inputNuListB64.split(",")) {
			if (tmp.isBlank()) {
				continue;
			}
			sb.append("00000001").append(HexFormat.of().formatHex(Base64.getDecoder().decode(tmp)));
		}
		return sb.toString();
	}

	static @NonNull ExtradataContainerHex encodeH264NalUnitsBase64ToHex_avcc(
				@NonNull String sps,
				@NonNull String pps
			) {
		final String FNC_NAME = ExtradataFromSdpConverterH26x.class.getSimpleName() + ".encodeH264NalUnitsBase64ToHex_avcc()";

		List<byte[]> spsList = decodeNalUnitListBase64(sps);
		List<byte[]> ppsList = decodeNalUnitListBase64(pps);

		byte[] firstSps = spsList.getFirst();
		if (firstSps.length < 4) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid H.264 SPS: too short");
		}
		if (spsList.size() > 31) {
			throw new IllegalArgumentException(FNC_NAME + ": Too many H.264 SPS NAL units (max 31)");
		}
		if (ppsList.size() > 255) {
			throw new IllegalArgumentException(FNC_NAME + ": Too many H.264 PPS NAL units (max 255)");
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// AVCDecoderConfigurationRecord (ISO/IEC 14496-15)
		out.write(0x01);                    // configurationVersion
		out.write(firstSps[1] & 0xFF);      // AVCProfileIndication
		out.write(firstSps[2] & 0xFF);      // profile_compatibility
		out.write(firstSps[3] & 0xFF);      // AVCLevelIndication
		out.write(0xFF);                    // 6 bits reserved + lengthSizeMinusOne(3 => 4-byte NAL length)
		out.write(0xE0 | spsList.size());   // 3 bits reserved + numOfSequenceParameterSets

		for (byte[] oneSps : spsList) {
			writeU16(out, oneSps.length);
			out.write(oneSps, 0, oneSps.length);
		}

		out.write(ppsList.size());          // numOfPictureParameterSets
		for (byte[] onePps : ppsList) {
			writeU16(out, onePps.length);
			out.write(onePps, 0, onePps.length);
		}

		return ExtradataContainerHex.createH264_avcC(
				HexFormat.of().formatHex(out.toByteArray())
			);
	}

	static @NonNull ExtradataContainerHex encodeH265NalUnitsBase64ToHex_hvcc(
				@NonNull String sps,
				@NonNull String pps,
				@NonNull String vps
			) {
		List<byte[]> spsList = decodeNalUnitListBase64(sps);
		List<byte[]> ppsList = decodeNalUnitListBase64(pps);
		List<byte[]> vpsList = decodeNalUnitListBase64(vps);

		int numArrays = 0;
		if (! vpsList.isEmpty()) { numArrays++; }
		if (! spsList.isEmpty()) { numArrays++; }
		if (! ppsList.isEmpty()) { numArrays++; }

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// HEVCDecoderConfigurationRecord (ISO/IEC 14496-15)
		out.write(0x01);   // configurationVersion
		out.write(0x01);   // general_profile_space(0), general_tier_flag(0), general_profile_idc(1=Main)
		out.write(0x00);   // general_profile_compatibility_flags (32 bits)
		out.write(0x00);
		out.write(0x00);
		out.write(0x00);
		out.write(0x00);   // general_constraint_indicator_flags (48 bits)
		out.write(0x00);
		out.write(0x00);
		out.write(0x00);
		out.write(0x00);
		out.write(0x00);
		out.write(0x00);   // general_level_idc (unknown -> 0)
		out.write(0xF0);   // reserved(4) + min_spatial_segmentation_idc high bits
		out.write(0x00);   // min_spatial_segmentation_idc low bits
		out.write(0xFC);   // reserved(6) + parallelismType(2)
		out.write(0xFC);   // reserved(6) + chromaFormat(2)
		out.write(0xF8);   // reserved(5) + bitDepthLumaMinus8(3)
		out.write(0xF8);   // reserved(5) + bitDepthChromaMinus8(3)
		out.write(0x00);   // avgFrameRate
		out.write(0x00);
		out.write(0x03);   // constFrameRate(2), numTemporalLayers(3), temporalIdNested(1), lengthSizeMinusOne(2=3 => 4-byte NAL lengths)
		out.write(numArrays & 0xFF);

		// array for VPS (NAL type 32)
		if (! vpsList.isEmpty()) {
			out.write(0x80 | 32); // array_completeness=1, reserved=0, NAL_unit_type=32
			writeU16(out, vpsList.size());
			for (byte[] nal : vpsList) {
				writeU16(out, nal.length);
				out.write(nal, 0, nal.length);
			}
		}

		// array for SPS (NAL type 33)
		if (! spsList.isEmpty()) {
			out.write(0x80 | 33); // array_completeness=1, reserved=0, NAL_unit_type=33
			writeU16(out, spsList.size());
			for (byte[] nal : spsList) {
				writeU16(out, nal.length);
				out.write(nal, 0, nal.length);
			}
		}

		// array for PPS (NAL type 34)
		if (! ppsList.isEmpty()) {
			out.write(0x80 | 34); // array_completeness=1, reserved=0, NAL_unit_type=34
			writeU16(out, ppsList.size());
			for (byte[] nal : ppsList) {
				writeU16(out, nal.length);
				out.write(nal, 0, nal.length);
			}
		}

		return ExtradataContainerHex.createH265_hvcC(
				HexFormat.of().formatHex(out.toByteArray())
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull List<byte[]> decodeNalUnitListBase64(@NonNull String inputNu) {
		final String FNC_NAME = ExtradataFromSdpConverterH26x.class.getSimpleName() + ".decodeNalUnitListBase64()";

		List<byte[]> out = new ArrayList<>();
		for (String tmp : inputNu.split(",")) {
			String t = tmp.trim();
			if (t.isEmpty()) {
				continue;
			}
			out.add(Base64.getDecoder().decode(t));
		}
		if (out.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": No NAL units provided");
		}
		return out;
	}

	private static void writeU16(@NonNull ByteArrayOutputStream out, int value) {
		final String FNC_NAME = ExtradataFromSdpConverterH26x.class.getSimpleName() + ".writeU16()";

		if (value < 0 || value > 0xFFFF) {
			throw new IllegalArgumentException(FNC_NAME + ": Value out of range for uint16: " + value);
		}
		out.write((value >>> 8) & 0xFF);
		out.write(value & 0xFF);
	}

}

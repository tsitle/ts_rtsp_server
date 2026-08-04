package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.VideoH265Info;

import java.util.ArrayList;
import java.util.List;

final class ExtradataForSdpConverterH265 extends ExtradataForSdpConverterBase {

	private static final class InternalResultH265 {
		final List<byte[]> sps = new ArrayList<>();
		final List<byte[]> pps = new ArrayList<>();
		final List<byte[]> vps = new ArrayList<>();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private ExtradataForSdpConverterH265() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Build H265 'extradata' as a colon-separated list of Base64-encoded NAL Units grouped by type.<br />
	 * The NAL Units within a group are separated by commas.<br />
	 * The start codes have been removed from the NAL Units.<br />
	 * The output can be used directly in the SDP output.
	 * @param noExpectations If true, no expectations are made about the 'extradata' format
	 * @param extradataHex Hex-encoded NAL Units or HVCC data
	 * @return Colon-separated list of Base64-encoded NAL Units grouped by type
	 *         (e.g., '&lt;Base64_SPS_1&gt;,&lt;Base64_SPS_2&gt;:&lt;Base64_PPS_1&gt;,&lt;Base64_PPS_2&gt;:&lt;Base64_VPS_1&gt;,&lt;Base64_VPS_2&gt;')
	 */
	static @NonNull ExtradataContainerSdp buildForSdp(
				boolean noExpectations,
				@NonNull ExtradataContainerHex extradataHex
			) {
		final String FNC_NAME = ExtradataForSdpConverterH265.class.getSimpleName()+ ".buildForSdp()";

		if (! extradataHex.isCodecH265()) {
			throw new IllegalArgumentException(FNC_NAME + ": Extradata is not for H265");
		}

		ExtradataForSdpConverterH265 edParser = new ExtradataForSdpConverterH265();

		byte[] tmpEdBa = edParser.parseHexStringToBytes(extradataHex.getEd());
		if (tmpEdBa == null || tmpEdBa.length == 0) {
			return ExtradataContainerSdp.ofEmpty();
		}

		return ExtradataContainerSdp.ofH265(
				edParser.buildExtradataFromBytes(noExpectations, extradataHex.isFmtH26xAnnexB(), tmpEdBa)
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected @NonNull String buildExtradataFromBytes(
				boolean noExpectations,
				boolean expectH26xAnnexB,
				byte[] extradata
			) {
		final String FNC_NAME = getClass().getSimpleName()+ ".buildExtradataFromBytes()";

		// Heuristic: hvcC usually starts with configurationVersion = 1 and has enough header bytes
		InternalResultH265 tmpRes;
		if (looksLikeHvcc(extradata)) {
			if (! noExpectations && expectH26xAnnexB) {
				throw new IllegalArgumentException(FNC_NAME + ": Expected Extradata H265 AnnexB but found hvcC");
			}
			tmpRes = parseHvcc(extradata);
		} else {
			if (! noExpectations && ! expectH26xAnnexB) {
				throw new IllegalArgumentException(FNC_NAME + ": Expected Extradata H265 hvcC but found AnnexB");
			}
			tmpRes = parseAnnexB(extradata);
		}

		//
		String resS = "";
		if (! tmpRes.sps.isEmpty()) {
			resS += encodeListAsB64String_h26xNalUnits(tmpRes.sps);
		}
		resS += ":";
		if (! tmpRes.pps.isEmpty()) {
			resS += encodeListAsB64String_h26xNalUnits(tmpRes.pps);
		}
		resS += ":";
		if (! tmpRes.vps.isEmpty()) {
			resS += encodeListAsB64String_h26xNalUnits(tmpRes.vps);
		}
		return resS;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static boolean looksLikeHvcc(byte[] d) {
		if (d == null || d.length < 23) return false;
		if ((d[0] & 0xFF) != 1) return false;  // configurationVersion

		// reserved-bit sanity checks from hvcC structure:
		// min_spatial_segmentation_idc high nibble reserved '1111'
		if ((d[13] & 0xF0) != 0xF0) return false;
		// parallelismType/chroma/bitDepth* bytes have top reserved bits '111111'
		if ((d[15] & 0xFC) != 0xFC) return false;
		if ((d[16] & 0xFC) != 0xFC) return false;
		if ((d[17] & 0xF8) != 0xF8) return false;
		if ((d[18] & 0xF8) != 0xF8) return false;

		int numOfArrays = d[22] & 0xFF;
		return (numOfArrays > 0);
	}

	private static @NonNull InternalResultH265 parseHvcc(byte[] d) {
		InternalResultH265 out = new InternalResultH265();

		if (d.length < 23) {
			return out;
		}

		int p = 22;
		int numOfArrays = d[p++] & 0xFF;  // now p == 23 (first array)

		for (int i = 0; i < numOfArrays; i++) {
			if (p + 3 > d.length) {
				break;
			}

			int nalType = d[p++] & 0x3F;
			int numNalus = ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
			p += 2;

			for (int n = 0; n < numNalus; n++) {
				if (p + 2 > d.length) {
					break;
				}
				int nalLen = ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
				p += 2;

				if (nalLen <= 0 || p + nalLen > d.length) {
					break;
				}

				byte[] nalContentBa = new byte[nalLen];
				System.arraycopy(d, p, nalContentBa, 0, nalLen);
				p += nalLen;

				addByType(out, nalType, nalContentBa);
			}
		}
		return out;
	}

	private static @NonNull InternalResultH265 parseAnnexB(byte[] d) {
		InternalResultH265 out = new InternalResultH265();

		int i = 0;
		while (true) {
			int start = findStartCode_h26x(d, i);
			if (start < 0) {
				break;
			}

			int scLen = startCodeLen_h26x(d, start);
			int nalStart = start + scLen;
			if (nalStart >= d.length) {
				break;
			}

			int next = findStartCode_h26x(d, nalStart);
			int nalEnd = (next < 0 ? d.length : next);
			if (nalEnd <= nalStart) {
				i = nalStart + 1;
				continue;
			}

			byte[] nalContentBa = new byte[nalEnd - nalStart];
			System.arraycopy(d, nalStart, nalContentBa, 0, nalContentBa.length);

			// HEVC NAL unit type from first header byte:
			// forbidden_zero_bit(1), nal_unit_type(6), nuh_layer_id bit(1) ...
			int nalType = (nalContentBa[0] >> 1) & 0x3F;
			addByType(out, nalType, nalContentBa);

			i = nalEnd;
		}

		return out;
	}

	private static void addByType(@NonNull InternalResultH265 out, int nalType, byte[] nal) {
		if ((byte)nalType == VideoH265Info.NalUnitType.NVCL_VPS.getValue()) {
			out.vps.add(nal);
		} else if ((byte)nalType == VideoH265Info.NalUnitType.NVCL_SPS.getValue()) {
			out.sps.add(nal);
		} else if ((byte)nalType == VideoH265Info.NalUnitType.NVCL_PPS.getValue()) {
			out.pps.add(nal);
		}
	}

}

package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.VideoH264Info;

import java.util.*;

public final class ExtradataParserH264 extends ExtradataParserBase {

	private static final class InternalResultH264 {
		final List<byte[]> sps = new ArrayList<>();
		final List<byte[]> pps = new ArrayList<>();
		byte[] firstSps = null;
		@Nullable String profileLevelIdHex = null;  // e.g. "64001F"
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private ExtradataParserH264() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Parse H264 extradata into a colon-separated list of Base64-encoded NAL Units grouped by type.<br />
	 * The NAL Units within a group are separated by commas.<br />
	 * The start codes have been removed from the NAL Units.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHex Hex-encoded NAL Units or AVCC data
	 * @return Colon-separated list of Base64-encoded NAL Units grouped by type plus the Profile Level Indication
	 *         (e.g., '&lt;Base64_SPS_1&gt;,&lt;Base64_SPS_2&gt;:&lt;Base64_PPS_1&gt;,&lt;Base64_PPS_2&gt;#&lt;PLI&gt;')
	 */
	public static @NonNull String parse(@NonNull String extradataHex) {
		ExtradataParserH264 edParser = new ExtradataParserH264();

		byte[] extradata = edParser.parseHexStringToBytes(extradataHex);
		if (extradata == null || extradata.length == 0) {
			return "";
		}

		return edParser.parseExtradataBytes(extradata);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected @NonNull String parseExtradataBytes(byte[] extradata) {
		InternalResultH264 tmpRes;
		if (looksLikeAvcc(extradata)) {
			tmpRes = parseAvcc(extradata);
			if (! tmpRes.sps.isEmpty() || ! tmpRes.pps.isEmpty()) {
				fillProfileLevelId(tmpRes, extradata, true);
			}
		} else {
			tmpRes = parseAnnexB(extradata);
			fillProfileLevelId(tmpRes, extradata, false);
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
		//
		resS += "#";
		if (tmpRes.profileLevelIdHex != null) {
			resS += tmpRes.profileLevelIdHex;
		}
		return resS;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static boolean looksLikeAvcc(byte[] d) {
		if (d.length < 7) return false;
		if ((d[0] & 0xFF) != 1) return false;     // configurationVersion
		if ((d[4] & 0xFC) != 0xFC) return false;  // reserved 111111xx
		if ((d[5] & 0xE0) != 0xE0) return false;  // reserved 111xxxxx
		int numSps = d[5] & 0x1F;
		return (numSps > 0);
	}

	private static @NonNull InternalResultH264 parseAvcc(byte[] d) {
		InternalResultH264 out = new InternalResultH264();
		int p = 5;

		int numSps = d[p++] & 0x1F;
		for (int i = 0; i < numSps; i++) {
			if (p + 2 > d.length) {
				return out;
			}
			int len = ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
			p += 2;
			if (len <= 0 || p + len > d.length) {
				return out;
			}
			byte[] nalContentBa = new byte[len];
			System.arraycopy(d, p, nalContentBa, 0, len);
			p += len;
			if (! isAnnexB_startCode_h26x_nal(nalContentBa)) {
				out.sps.add(nalContentBa);
			}
		}

		if (p + 1 > d.length) {
			return out;
		}
		int numPps = d[p++] & 0xFF;
		for (int i = 0; i < numPps; i++) {
			if (p + 2 > d.length) {
				return out;
			}
			int len = ((d[p] & 0xFF) << 8) | (d[p + 1] & 0xFF);
			p += 2;
			if (len <= 0 || p + len > d.length) {
				return out;
			}
			byte[] nalContentBa = new byte[len];
			System.arraycopy(d, p, nalContentBa, 0, len);
			p += len;
			if (! isAnnexB_startCode_h26x_nal(nalContentBa)) {
				out.pps.add(nalContentBa);
			}
		}

		if (! out.sps.isEmpty()) {
			out.firstSps = out.sps.getFirst();
		}
		return out;
	}

	private static @NonNull InternalResultH264 parseAnnexB(byte[] d) {
		InternalResultH264 out = new InternalResultH264();
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

			int nalType = nalContentBa[0] & 0x1F;  // H264 NAL type
			addByType(out, nalType, nalContentBa);

			i = nalEnd;
		}

		if (! out.sps.isEmpty()) {
			out.firstSps = out.sps.getFirst();
		}
		return out;
	}

	private static void addByType(@NonNull InternalResultH264 out, int nalType, byte[] nal) {
		if ((byte)nalType == VideoH264Info.NalUnitType.NVCL_SPS.getValue()) {
			out.sps.add(nal);
		} else if ((byte)nalType == VideoH264Info.NalUnitType.NVCL_PPS.getValue()) {
			out.pps.add(nal);
		}
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private static boolean isAnnexB_startCode_h26x_nal(byte[] nal) {
		return (nal.length >= 3 &&
				nal[0] == 0 &&
				nal[1] == 0 &&
				(nal[2] == 1 || (nal.length >= 4 && nal[2] == 0 && nal[3] == 1)));
	}

	private static void fillProfileLevelId(@NonNull InternalResultH264 r, byte[] extradata, boolean fromAvccHeader) {
		if (fromAvccHeader && extradata.length >= 4) {
			r.profileLevelIdHex = String.format(Locale.ROOT, "%02X%02X%02X",
					extradata[1] & 0xFF, extradata[2] & 0xFF, extradata[3] & 0xFF);
			return;
		}
		if (r.firstSps != null && r.firstSps.length >= 4) {
			// SPS NAL: [0]=nal hdr, [1]=profile_idc, [2]=constraints, [3]=level_idc
			r.profileLevelIdHex = String.format(Locale.ROOT, "%02X%02X%02X",
					r.firstSps[1] & 0xFF, r.firstSps[2] & 0xFF, r.firstSps[3] & 0xFF);
		}
	}

}

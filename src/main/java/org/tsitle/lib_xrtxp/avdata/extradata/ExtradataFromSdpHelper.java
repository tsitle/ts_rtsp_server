package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;

/**
 * Helper class for converting A/V codec 'extradata' from SDP data to hex-encoded strings suitable for A/V encoders.
 */
@SuppressWarnings("unused")
public final class ExtradataFromSdpHelper {

	private ExtradataFromSdpHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Convert H.264 SPS/PPS NAL Units from SDP data (prefix-less Base64-encoded strings) into a single
	 * AnnexB-prefixed or avcC hex-encoded string. The output is suitable for use as the
	 * 'extradata' for an H.264 video stream with FFmpeg.
	 * @param outputAsAnnexB Whether to output the 'extradata' in AnnexB format (true) or avcC format (false).
	 * @param sdpData Codec-specific SDP data
	 * @return Hex-encoded 'extradata'
	 */
	public static @NonNull String buildH264EncoderExtradataFromSdp(boolean outputAsAnnexB, @NonNull String sdpData) {
		final String FNC_NAME = ExtradataFromSdpHelper.class.getSimpleName() + ".convertH264FromSdpExtradataToHex()";

		/*
		 * Colon-separated list of Base64-encoded NAL Units grouped by type plus the Profile Level Indication.
		 * Example: '<Base64_SPS_1>,<Base64_SPS_2>:<Base64_PPS_1>,<Base64_PPS_2>#<PLI>'
		 */
		String[] tmpSplit = sdpData.split("#");
		if (tmpSplit.length != 1 && tmpSplit.length != 2) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid H.264 SDP extradata");
		}
		tmpSplit = tmpSplit[0].split(":");
		if (tmpSplit.length != 2) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid H.264 SDP extradata");
		}
		String nuSps = tmpSplit[0];
		String nuPps = tmpSplit[1];

		//
		if (outputAsAnnexB) {
			String resS = "";
			resS += ExtradataFromSdpConverterH26x.encodeH26xNalUnitListBase64ToHex_annexB(nuSps);  // first SPS
			resS += ExtradataFromSdpConverterH26x.encodeH26xNalUnitListBase64ToHex_annexB(nuPps);  // then PPS
			return resS;
		}
		return ExtradataFromSdpConverterH26x.encodeH264NalUnitsBase64ToHex_avcc(nuSps, nuPps);
	}

	/**
	 * Convert H.265 SPS/PPS/VPS NAL Units from SDP data (prefix-less Base64-encoded strings) into a single
	 * AnnexB-prefixed or hvcC hex-encoded string. The output is suitable for use as the
	 * 'extradata' for an H.265 video stream with FFmpeg.
	 * @param outputAsAnnexB Whether to output the 'extradata' in AnnexB format (true) or hvcC format (false).
	 * @param sdpData Codec-specific SDP data
	 * @return Hex-encoded 'extradata'
	 */
	public static @NonNull String buildH265EncoderExtradataFromSdp(boolean outputAsAnnexB, @NonNull String sdpData) {
		final String FNC_NAME = ExtradataFromSdpHelper.class.getSimpleName() + ".convertH265FromSdpExtradataToHex()";

		/*
		 * Colon-separated list of Base64-encoded NAL Units grouped by type.
		 * Example: '<Base64_SPS_1>,<Base64_SPS_2>:<Base64_PPS_1>,<Base64_PPS_2>:<Base64_VPS_1>,<Base64_VPS_2>'
		 */
		String[] tmpSplit = sdpData.split(":");
		if (tmpSplit.length != 3) {
			throw new IllegalArgumentException(FNC_NAME + ": Invalid H.265 SDP extradata");
		}
		String nuSps = tmpSplit[0];
		String nuPps = tmpSplit[1];
		String nuVps = tmpSplit[2];

		//
		if (outputAsAnnexB) {
			String resS = "";
			resS += ExtradataFromSdpConverterH26x.encodeH26xNalUnitListBase64ToHex_annexB(nuVps);  // VPS comes first
			resS += ExtradataFromSdpConverterH26x.encodeH26xNalUnitListBase64ToHex_annexB(nuSps);  // then SPS
			resS += ExtradataFromSdpConverterH26x.encodeH26xNalUnitListBase64ToHex_annexB(nuPps);  // then PPS
			return resS;
		}
		return ExtradataFromSdpConverterH26x.encodeH265NalUnitsBase64ToHex_hvcc(nuSps, nuPps, nuVps);
	}

}

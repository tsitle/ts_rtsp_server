package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * Helper class for converting A/V codec 'extradata' from SDP data to hex-encoded strings suitable for A/V encoders.
 */
@SuppressWarnings("unused")
public final class ExtradataFromSdpHelper {

	private ExtradataFromSdpHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Convert 'extradata' from SDP data in a codec-specific format.<br />
	 * The output is suitable for use as the 'extradata' for an A/V encoder.
	 * @param codec A/V codec
	 * @param outputH26xAsAnnexB Whether to output the 'extradata' for H264/H265 in AnnexB format (true) or avcC/hvcC format (false).
	 * @param sdpData Codec-specific SDP data
	 * @return Hex-encoded 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerHex buildExtradataForSdp(
				@NonNull RtpPacketType codec,
				boolean outputH26xAsAnnexB,
				@NonNull ExtradataContainerSdp sdpData
			) {
		return switch (codec) {
				case A_AAC -> buildAacEncoderExtradataFromSdp(sdpData);
				case A_OPUS_UNSUPPORTED -> buildOpusEncoderExtradataFromSdp(sdpData);
				case V_H264 -> buildH264EncoderExtradataFromSdp(outputH26xAsAnnexB, sdpData);
				case V_H265 -> buildH265EncoderExtradataFromSdp(outputH26xAsAnnexB, sdpData);
				default -> ExtradataContainerHex.ofEmpty();
			};
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Convert SDP data to an output that is suitable for use as the
	 * 'extradata' for an AAC audio stream with FFmpeg.
	 * @param sdpData Codec-specific SDP data
	 * @return Hex-encoded 'extradata'
	 */
	public static @NonNull ExtradataContainerHex buildAacEncoderExtradataFromSdp(
				@NonNull ExtradataContainerSdp sdpData
			) {
		final String FNC_NAME = ExtradataFromSdpHelper.class.getSimpleName() + ".buildAacEncoderExtradataFromSdp()";

		if (sdpData.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": sdpData is empty");
		}
		if (! sdpData.isCodecAac()) {
			throw new IllegalArgumentException(FNC_NAME + ": sdpData is not for AAC");
		}

		return ExtradataContainerHex.ofAac(sdpData.getEd());
	}

	/**
	 * Convert H.264 SPS/PPS NAL Units from SDP data (prefix-less Base64-encoded strings) into a single
	 * AnnexB-prefixed or avcC hex-encoded string. The output is suitable for use as the
	 * 'extradata' for an H.264 video stream with FFmpeg.
	 * @param outputAsAnnexB Whether to output the 'extradata' in AnnexB format (true) or avcC format (false).
	 * @param sdpData Codec-specific SDP data
	 * @return Hex-encoded 'extradata'
	 */
	public static @NonNull ExtradataContainerHex buildH264EncoderExtradataFromSdp(
				boolean outputAsAnnexB,
				@NonNull ExtradataContainerSdp sdpData
			) {
		final String FNC_NAME = ExtradataFromSdpHelper.class.getSimpleName() + ".buildH264EncoderExtradataFromSdp()";

		if (sdpData.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": sdpData is empty");
		}
		if (! sdpData.isCodecH264()) {
			throw new IllegalArgumentException(FNC_NAME + ": sdpData is not for H264");
		}

		/*
		 * Colon-separated list of Base64-encoded NAL Units grouped by type plus the Profile Level Indication.
		 * Example: '<Base64_SPS_1>,<Base64_SPS_2>:<Base64_PPS_1>,<Base64_PPS_2>#<PLI>'
		 */
		String[] tmpSplit = sdpData.getEd().split("#");
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
			return ExtradataContainerHex.ofH264_annexB(resS);
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
	public static @NonNull ExtradataContainerHex buildH265EncoderExtradataFromSdp(
				boolean outputAsAnnexB,
				@NonNull ExtradataContainerSdp sdpData
			) {
		final String FNC_NAME = ExtradataFromSdpHelper.class.getSimpleName() + ".buildH265EncoderExtradataFromSdp()";

		if (sdpData.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": sdpData is empty");
		}
		if (! sdpData.isCodecH265()) {
			throw new IllegalArgumentException(FNC_NAME + ": sdpData is not for H265");
		}

		/*
		 * Colon-separated list of Base64-encoded NAL Units grouped by type.
		 * Example: '<Base64_SPS_1>,<Base64_SPS_2>:<Base64_PPS_1>,<Base64_PPS_2>:<Base64_VPS_1>,<Base64_VPS_2>'
		 */
		String[] tmpSplit = sdpData.getEd().split(":");
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
			return ExtradataContainerHex.ofH265_annexB(resS);
		}
		return ExtradataFromSdpConverterH26x.encodeH265NalUnitsBase64ToHex_hvcc(nuSps, nuPps, nuVps);
	}

	/**
	 * Convert SDP data to an output that is suitable for use as the
	 * 'extradata' for an Opus audio stream with FFmpeg.
	 * @param sdpData Codec-specific SDP data
	 * @return Hex-encoded 'extradata'
	 */
	public static @NonNull ExtradataContainerHex buildOpusEncoderExtradataFromSdp(
				@NonNull ExtradataContainerSdp sdpData
			) {
		final String FNC_NAME = ExtradataFromSdpHelper.class.getSimpleName() + ".buildOpusEncoderExtradataFromSdp()";

		if (sdpData.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": sdpData is empty");
		}
		if (! sdpData.isCodecOpus()) {
			throw new IllegalArgumentException(FNC_NAME + ": sdpData is not for Opus");
		}

		return ExtradataContainerHex.ofOpus(sdpData.getEd());
	}

}

package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;

/**
 * Helper class for converting A/V codec 'extradata' from one format to another.
 */
public final class ExtradataConvHexFmtHelper {

	private ExtradataConvHexFmtHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Convert H.264 'extradata' from AnnexB to avcC format or vice versa.
	 * @param outputAsAnnexB Whether to output the 'extradata' in AnnexB format (true) or avcC format (false).
	 * @param extradataHexStr Hex-encoded 'extradata' string
	 * @return Hex-encoded 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerHex convertH264EncoderExtradata(
				boolean outputAsAnnexB,
				@NonNull String extradataHexStr
			) {
		ExtradataContainerSdp tmpEdSdp = ExtradataForSdpHelper.buildH264ExtradataForSdp(extradataHexStr);
		return ExtradataFromSdpHelper.buildH264EncoderExtradataFromSdp(outputAsAnnexB, tmpEdSdp);
	}

	/**
	 * Convert H.264 'extradata' from AnnexB to avcC format or vice versa.
	 * @param outputAsAnnexB Whether to output the 'extradata' in AnnexB format (true) or avcC format (false).
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Hex-encoded 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerHex convertH264EncoderExtradata(
				boolean outputAsAnnexB,
				@NonNull ExtradataContainerHex extradataHex
			) {
		ExtradataContainerSdp tmpEdSdp = ExtradataForSdpHelper.buildH264ExtradataForSdp(extradataHex);
		return ExtradataFromSdpHelper.buildH264EncoderExtradataFromSdp(outputAsAnnexB, tmpEdSdp);
	}

	/**
	 * Convert H.265 'extradata' from AnnexB to hvcC format or vice versa.
	 * @param outputAsAnnexB Whether to output the 'extradata' in AnnexB format (true) or hvcC format (false).
	 * @param extradataHexStr Hex-encoded 'extradata' string
	 * @return Hex-encoded 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerHex convertH265EncoderExtradata(
				boolean outputAsAnnexB,
				@NonNull String extradataHexStr
			) {
		ExtradataContainerSdp tmpEdSdp = ExtradataForSdpHelper.buildH265ExtradataForSdp(extradataHexStr);
		return ExtradataFromSdpHelper.buildH265EncoderExtradataFromSdp(outputAsAnnexB, tmpEdSdp);
	}

	/**
	 * Convert H.265 'extradata' from AnnexB to hvcC format or vice versa.
	 * @param outputAsAnnexB Whether to output the 'extradata' in AnnexB format (true) or hvcC format (false).
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Hex-encoded 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerHex convertH265EncoderExtradata(
				boolean outputAsAnnexB,
				@NonNull ExtradataContainerHex extradataHex
			) {
		ExtradataContainerSdp tmpEdSdp = ExtradataForSdpHelper.buildH265ExtradataForSdp(extradataHex);
		return ExtradataFromSdpHelper.buildH265EncoderExtradataFromSdp(outputAsAnnexB, tmpEdSdp);
	}

}

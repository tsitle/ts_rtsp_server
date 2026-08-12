package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;

/**
 * Helper class for creating A/V codec 'extradata' suitable for SDP output.
 */
public final class ExtradataForSdpHelper {

	private ExtradataForSdpHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Build AAC audio 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerSdp buildAacExtradataForSdp(
				@NonNull ExtradataContainerHex extradataHex
			) {
		final String FNC_NAME = ExtradataForSdpHelper.class.getSimpleName()+ ".buildAacExtradataForSdp()";

		if (! extradataHex.isCodecAac()) {
			throw new IllegalArgumentException(FNC_NAME + ": Extradata is not for AAC");
		}

		// output == input for AAC
		return ExtradataContainerSdp.ofAac(extradataHex.getEd());
	}

	/**
	 * Build H264 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHexStr Hex-encoded 'extradata' string
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerSdp buildH264ExtradataForSdp(
				@NonNull String extradataHexStr
			) {
		return buildH264ExtradataForSdp(
				true,
				ExtradataContainerHex.ofH264_annexB(extradataHexStr)
			);
	}

	/**
	 * Build H264 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerSdp buildH264ExtradataForSdp(
				@NonNull ExtradataContainerHex extradataHex
			) {
		return buildH264ExtradataForSdp(false, extradataHex);
	}

	/**
	 * Build H264 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param noExpectations If true, no expectations are applied regarding the 'extradata' format
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	public static @NonNull ExtradataContainerSdp buildH264ExtradataForSdp(
				boolean noExpectations,
				@NonNull ExtradataContainerHex extradataHex
			) {
		return ExtradataForSdpConverterH264.buildForSdp(noExpectations, extradataHex);
	}

	/**
	 * Build H265 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHexStr Hex-encoded 'extradata' string
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerSdp buildH265ExtradataForSdp(
				@NonNull String extradataHexStr
			) {
		return buildH265ExtradataForSdp(
				true,
				ExtradataContainerHex.ofH265_annexB(extradataHexStr)
			);
	}

	/**
	 * Build H265 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerSdp buildH265ExtradataForSdp(
				@NonNull ExtradataContainerHex extradataHex
			) {
		return buildH265ExtradataForSdp(false, extradataHex);
	}

	/**
	 * Build H265 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param noExpectations If true, no expectations are applied regarding the 'extradata' format
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	public static @NonNull ExtradataContainerSdp buildH265ExtradataForSdp(
				boolean noExpectations,
				@NonNull ExtradataContainerHex extradataHex
			) {
		return ExtradataForSdpConverterH265.buildForSdp(noExpectations, extradataHex);
	}

}

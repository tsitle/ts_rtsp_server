package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * Helper class for creating A/V codec 'extradata' suitable for SDP output.
 */
public final class ExtradataForSdpHelper {

	private ExtradataForSdpHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Build 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param codec A/V codec
	 * @param extradataHexStr Hex-encoded 'extradata' string
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerSdp buildExtradataForSdp(
				@NonNull RtpPacketType codec,
				@NonNull String extradataHexStr
			) {
		return switch (codec) {
				case A_AAC -> internalBuildAacExtradataForSdp(ExtradataContainerHex.ofAac(extradataHexStr));
				case V_H264 -> internalBuildH264ExtradataForSdp(
						true,
						ExtradataContainerHex.ofH264_annexB(extradataHexStr)
					);
				case V_H265 -> internalBuildH265ExtradataForSdp(
						true,
						ExtradataContainerHex.ofH265_annexB(extradataHexStr)
					);
				default -> ExtradataContainerSdp.ofEmpty();
			};
	}

	/**
	 * Build 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param codec A/V Codec
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull ExtradataContainerSdp buildExtradataForSdp(
				@NonNull RtpPacketType codec,
				@NonNull ExtradataContainerHex extradataHex
			) {
		return switch (codec) {
				case A_AAC -> internalBuildAacExtradataForSdp(extradataHex);
				case V_H264 -> internalBuildH264ExtradataForSdp(false, extradataHex);
				case V_H265 -> internalBuildH265ExtradataForSdp(false, extradataHex);
				default -> ExtradataContainerSdp.ofEmpty();
			};
	}

	// -----------------------------------------------------------------------------------------------------------------

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
		return internalBuildH264ExtradataForSdp(
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
		return internalBuildH264ExtradataForSdp(false, extradataHex);
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
		return internalBuildH265ExtradataForSdp(
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
		return internalBuildH265ExtradataForSdp(false, extradataHex);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Build AAC audio 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	private static @NonNull ExtradataContainerSdp internalBuildAacExtradataForSdp(
				@NonNull ExtradataContainerHex extradataHex
			) {
		final String FNC_NAME = ExtradataForSdpHelper.class.getSimpleName()+ ".internalBuildAacExtradataForSdp()";

		if (! extradataHex.isCodecAac()) {
			throw new IllegalArgumentException(FNC_NAME + ": Extradata is not for AAC");
		}

		// output == input for AAC
		return ExtradataContainerSdp.ofAac(extradataHex.getEd());
	}

	/**
	 * Build H264 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param noExpectations If true, no expectations are made about the 'extradata' format
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	private static @NonNull ExtradataContainerSdp internalBuildH264ExtradataForSdp(
				boolean noExpectations,
				@NonNull ExtradataContainerHex extradataHex
			) {
		return ExtradataForSdpConverterH264.buildForSdp(noExpectations, extradataHex);
	}

	/**
	 * Build H265 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param noExpectations If true, no expectations are made about the 'extradata' format
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	private static @NonNull ExtradataContainerSdp internalBuildH265ExtradataForSdp(
				boolean noExpectations,
				@NonNull ExtradataContainerHex extradataHex
			) {
		return ExtradataForSdpConverterH265.buildForSdp(noExpectations, extradataHex);
	}

}

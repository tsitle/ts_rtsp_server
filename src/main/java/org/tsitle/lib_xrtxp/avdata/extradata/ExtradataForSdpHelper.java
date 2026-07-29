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
	 * Build video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull String buildVideoExtradataForSdp(
				@NonNull RtpPacketType codec,
				@NonNull String extradataHex
			) {
		return switch (codec) {
				case V_H264 -> ExtradataForSdpConverterH264.buildForSdp(extradataHex);
				case V_H265 -> ExtradataForSdpConverterH265.buildForSdp(extradataHex);
				default -> "";
			};
	}

	/**
	 * Build H264 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull String buildH264ExtradataForSdp(@NonNull String extradataHex) {
		return ExtradataForSdpConverterH264.buildForSdp(extradataHex);
	}

	/**
	 * Build H265 video 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param extradataHex Hex-encoded 'extradata'
	 * @return Codec-specific 'extradata'
	 */
	@SuppressWarnings("unused")
	public static @NonNull String buildH265ExtradataForSdp(@NonNull String extradataHex) {
		return ExtradataForSdpConverterH265.buildForSdp(extradataHex);
	}

}

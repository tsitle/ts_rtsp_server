package org.tsitle.rtsp_server.availstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerHex;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataContainerSdp;
import org.tsitle.lib_xrtxp.avdata.extradata.ExtradataForSdpHelper;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

public final class RtspAsEdSdpHelper {

	private RtspAsEdSdpHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Build 'extradata' in a codec-specific format.<br />
	 * The output can be used directly in the SDP output.
	 * @param codec A/V codec
	 * @param extradataHexStr Hex-encoded 'extradata' string
	 * @return Codec-specific 'extradata'
	 */
	public static @NonNull ExtradataContainerSdp buildExtradataForSdp(
				@NonNull RtpPacketType codec,
				@NonNull String extradataHexStr
			) {
		return switch (codec) {
				case A_AAC -> ExtradataForSdpHelper.buildAacExtradataForSdp(ExtradataContainerHex.ofAac(extradataHexStr));
				case V_H264 -> ExtradataForSdpHelper.buildH264ExtradataForSdp(
						true,
						ExtradataContainerHex.ofH264_annexB(extradataHexStr)
					);
				case V_H265 -> ExtradataForSdpHelper.buildH265ExtradataForSdp(
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
	public static @NonNull ExtradataContainerSdp buildExtradataForSdp(
				@NonNull RtpPacketType codec,
				@NonNull ExtradataContainerHex extradataHex
			) {
		return switch (codec) {
				case A_AAC -> ExtradataForSdpHelper.buildAacExtradataForSdp(extradataHex);
				case V_H264 -> ExtradataForSdpHelper.buildH264ExtradataForSdp(false, extradataHex);
				case V_H265 -> ExtradataForSdpHelper.buildH265ExtradataForSdp(false, extradataHex);
				default -> ExtradataContainerSdp.ofEmpty();
			};
	}

}

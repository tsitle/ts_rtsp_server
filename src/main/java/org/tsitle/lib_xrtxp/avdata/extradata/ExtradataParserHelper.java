package org.tsitle.lib_xrtxp.avdata.extradata;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

public final class ExtradataParserHelper {

	private ExtradataParserHelper() { }

	public static @NonNull String parseVideoExtradataHex(
				@NonNull RtpPacketType codec,
				@NonNull String extradataHex
			) {
		return switch (codec) {
				case V_H264 -> ExtradataParserH264.parse(extradataHex);
				case V_H265 -> ExtradataParserH265.parse(extradataHex);
				default -> "";
			};
	}

}

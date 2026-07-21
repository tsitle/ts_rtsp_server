package org.tsitle.rtsp_server.threads.dataprovider.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.VideoH265Info;
import org.tsitle.lib_xrtxp.avdata.VideoH265Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

final class PacketParserH265 {

	private final @NonNull VideoH265Parser pktParser;

	PacketParserH265() {
		this.pktParser = new VideoH265Parser();
	}

	@NonNull VideoH265Info parseData(long debugStreamOffset, @NonNull BufferView inputBv)
			throws AvInvalidCodecDataException {
		int magicBytesLength = MagicBytesH26xHelper.findH26xMagicBytesLength(inputBv);
		//
		return pktParser.parseH265Data(
				debugStreamOffset,
				magicBytesLength,
				inputBv
			);
	}

}

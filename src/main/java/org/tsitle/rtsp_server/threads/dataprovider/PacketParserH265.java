package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.VideoH265Info;
import org.tsitle.lib_xrtxp.avdata.VideoH265Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

final class PacketParserH265 {

	private final @NonNull VideoH265Parser pktParser;

	PacketParserH265() {
		this.pktParser = new VideoH265Parser();
	}

	@NonNull VideoH265Info parseAndConvertData(long debugStreamOffset, @NonNull BufferExt inputBuf)
			throws AvInvalidCodecDataException {
		int magicBytesLength = MagicBytesH26xHelper.findH26xMagicBytesLength(inputBuf);
		//
		return pktParser.parseH265Data(
				debugStreamOffset,
				magicBytesLength,
				inputBuf
			);
	}

}

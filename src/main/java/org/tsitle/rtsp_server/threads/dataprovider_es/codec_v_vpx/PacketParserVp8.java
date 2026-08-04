package org.tsitle.rtsp_server.threads.dataprovider_es.codec_v_vpx;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Info;
import org.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

final class PacketParserVp8 {

	private final @NonNull VideoVp8Parser pktParser;

	PacketParserVp8() {
		this.pktParser = new VideoVp8Parser();
	}

	@NonNull VideoVp8Info parseData(long debugStreamOffset, @NonNull BufferView inputBv)
			throws AvInvalidCodecDataException {
		return pktParser.parseVp8Data(
				debugStreamOffset,
				inputBv
			);
	}

}

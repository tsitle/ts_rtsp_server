package org.tsitle.lib_dataprov.threads_es.codec_v_vpx;

import org.jspecify.annotations.NonNull;
import io.github.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Info;
import io.github.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Parser;
import io.github.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import io.github.tsitle.lib_xrtxp.common.buffers.BufferView;

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

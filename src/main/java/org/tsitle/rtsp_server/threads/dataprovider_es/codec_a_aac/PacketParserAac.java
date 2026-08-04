package org.tsitle.rtsp_server.threads.dataprovider_es.codec_a_aac;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacParser;
import org.tsitle.lib_xrtxp.avdata.codec_a_aac.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

final class PacketParserAac {

	private final @NonNull AudioAacParser pktParser;

	PacketParserAac() {
		this.pktParser = new AudioAacParser();
	}

	@NonNull AudioAacInfo parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return pktParser.parseAacData(inputBv);
	}

}

package org.tsitle.lib_dataprov.threads_es.codec_a_opus;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_opus.AudioOpusInfo;
import org.tsitle.lib_xrtxp.avdata.codec_a_opus.AudioOpusParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

final class PacketParserOpus {

	private final @NonNull AudioOpusParser pktParser;

	PacketParserOpus() {
		this.pktParser = new AudioOpusParser();
	}

	@NonNull AudioOpusInfo parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return pktParser.parseOpusData(inputBv);
	}

}

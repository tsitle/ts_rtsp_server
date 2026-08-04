package org.tsitle.lib_dataprov.threads_es.codec_a_ac3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Info;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

final class PacketParserAc3 {

	private final @NonNull AudioAc3Parser pktParser;

	PacketParserAc3() {
		this.pktParser = new AudioAc3Parser();
	}

	@NonNull AudioAc3Info parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return pktParser.parseAc3Data(inputBv);
	}

}

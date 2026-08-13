package org.tsitle.lib_dataprov.threads_es.codec_a_mp3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_mp3.AudioMp3Info;
import org.tsitle.lib_xrtxp.avdata.codec_a_mp3.AudioMp3Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

final class PacketParserMp3 {

	private final @NonNull AudioMp3Parser pktParser;

	PacketParserMp3() {
		this.pktParser = new AudioMp3Parser();
	}

	@NonNull AudioMp3Info parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return pktParser.parseMp3Data(inputBv);
	}

}

package org.tsitle.lib_dataprov.threads_es.codec_a_mpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_mpeg.AudioMpegInfo;
import org.tsitle.lib_xrtxp.avdata.codec_a_mpeg.AudioMpegParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

final class PacketParserMpa {

	private final @NonNull AudioMpegParser pktParser;

	PacketParserMpa() {
		this.pktParser = new AudioMpegParser();
	}

	@NonNull AudioMpegInfo parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException {
		return pktParser.parseMpaData(inputBv);
	}

}

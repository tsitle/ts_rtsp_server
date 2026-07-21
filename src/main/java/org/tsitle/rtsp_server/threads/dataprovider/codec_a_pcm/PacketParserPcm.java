package org.tsitle.rtsp_server.threads.dataprovider.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioPcmInfo;
import org.tsitle.lib_xrtxp.avdata.AudioPcmParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;

final class PacketParserPcm {

	private final @NonNull AudioPcmParser pktParser;

	PacketParserPcm(int channels, int bitsPerSample) {
		this.pktParser = new AudioPcmParser(channels, bitsPerSample);
	}

	@NonNull AudioPcmInfo parseData(@NonNull BufferView inputBv)
			throws AvInvalidCodecDataException {
		return pktParser.parsePcmData(inputBv);
	}

}

package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioPcmInfo;
import org.tsitle.lib_xrtxp.avdata.AudioPcmParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

final class PacketParserPcm {

	private final @NonNull AudioPcmParser pktParser;

	PacketParserPcm(int channels, int bitsPerSample) {
		this.pktParser = new AudioPcmParser(channels, bitsPerSample);
	}

	@NonNull AudioPcmInfo parseAndConvertData(@NonNull BufferExt inputBuf)
			throws AvInvalidCodecDataException {
		return pktParser.parsePcmData(inputBuf);
	}

}

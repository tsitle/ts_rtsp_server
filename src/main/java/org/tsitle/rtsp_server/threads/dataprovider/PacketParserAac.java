package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAacParser;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;

final class PacketParserAac {

	private final @NonNull AudioAacParser pktParser;

	PacketParserAac() {
		this.pktParser = new AudioAacParser();
	}

	@NonNull AudioAacInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		return pktParser.parseAacData(inputBuf);
	}

}

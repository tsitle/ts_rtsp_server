package org.tsitle.rtsp_server.threads.dataprovider_es.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioPcmInfo;
import org.tsitle.lib_xrtxp.avdata.AudioPcmParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.helpers.SampleRateEnum;

final class PacketParserPcm {

	private final @NonNull AudioPcmParser pktParser;

	PacketParserPcm(int channels, int bitsPerSample, @NonNull SampleRateEnum samplerate) {
		this.pktParser = new AudioPcmParser(channels, bitsPerSample, samplerate);
	}

	@NonNull AudioPcmInfo parseData(@NonNull BufferView inputBv)
			throws AvInvalidCodecDataException {
		return pktParser.parsePcmData(inputBv);
	}

}

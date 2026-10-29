package org.tsitle.lib_dataprov.threads_es.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import io.github.tsitle.lib_xrtxp.avdata.codec_a_pcm.AudioPcmInfo;
import io.github.tsitle.lib_xrtxp.avdata.codec_a_pcm.AudioPcmParser;
import io.github.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import io.github.tsitle.lib_xrtxp.common.buffers.BufferView;
import io.github.tsitle.lib_xrtxp.common.types.SampleRateEnum;

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

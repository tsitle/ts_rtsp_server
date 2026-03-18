package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.AudioPcmInfo;
import org.tsitle.rtsp.avdata.AudioPcmParser;
import org.tsitle.rtsp.avstreams.AudioStreamOutgoingPcmFromMq;
import org.tsitle.rtsp.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderPcm;

public class ThreadDataProvPcmFromMq extends ThreadDataProvFromMqBase<AudioPcmInfo> {

	private final AudioPcmParser pcmParser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public ThreadDataProvPcmFromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface);

		//
		paramsAudioCommon.validate();
		paramsPcm.validate();

		//
		this.mediaOutgoingStream = new AudioStreamOutgoingPcmFromMq(
				logMsgInterface,
				avStreamIncoming,
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample(),
				paramsPcm.getIsAudioInputBigEndian()
			);
		this.pcmParser = new AudioPcmParser(
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample()
			);

		//
		this.haveAllRequiredMetadataPackets = true;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull AudioPcmInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		return pcmParser.parsePcmData(inputBuf);
	}

}

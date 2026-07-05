package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioPcmInfo;
import org.tsitle.lib_xrtxp.avdata.AudioPcmParser;
import org.tsitle.rtsp_server.avstreams.AudioStreamOutgoingPcmFromMq;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderPcm;

public final class ThreadDataProvPcmFromMq extends ThreadDataProvFromMqBase<AudioPcmInfo> {

	private final @NonNull AudioPcmParser pcmParser;

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
		super(logMsgInterface, false);

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

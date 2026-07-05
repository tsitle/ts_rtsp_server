package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioPcmInfo;
import org.tsitle.lib_xrtxp.avdata.AudioPcmParser;
import org.tsitle.rtsp_server.avstreams.AudioStreamOutgoingPcmFromFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderPcm;

public class ThreadDataProvPcmFromFile extends ThreadDataProvFromFileBase<AudioPcmInfo> {

	private final @NonNull AudioPcmParser pcmParser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 * @param avStreamIncoming Incoming A/V stream
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvPcmFromFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm,
				@NonNull AvStreamIncomingFromFile avStreamIncoming,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				logMsgInterface,
				queueSize,
				debugRewindMediaFiles
			);

		//
		paramsAudioCommon.validate();
		paramsPcm.validate();

		//
		this.mediaOutgoingStream = new AudioStreamOutgoingPcmFromFile(
				logMsgInterface,
				avStreamIncoming,
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample(),
				paramsAudioCommon.getRtpAudioSpf(),
				paramsPcm.getIsAudioInputBigEndian()
			);
		this.pcmParser = new AudioPcmParser(
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	@Override
	public synchronized void notifyCongestionLevelChange(@SuppressWarnings("unused") int congestionLevel) {
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull AudioPcmInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		return pcmParser.parsePcmData(inputBuf);
	}

}

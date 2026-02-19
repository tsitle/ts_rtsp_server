package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.PcmInfo;
import org.tsitle.rtsp.avdata.PcmParser;
import org.tsitle.rtsp.avstreams.AudioStreamOutgoingPcm;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidPcmDataException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderPcm;

import java.io.FileNotFoundException;

public class ThreadDataProvPcm extends ThreadDataProvBase<PcmInfo> {

	private final PcmParser pcmParser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOF is reached
	 */
	public ThreadDataProvPcm(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				logMsgInterface,
				queueSize,
				debugRewindMediaFiles
			);

		//
		try {
			this.mediaOutgoingStream = new AudioStreamOutgoingPcm(
					paramsAudioCommon.getAudioFilePath().orElseThrow(),
					paramsPcm.getAudioChannelCount(),
					paramsPcm.getAudioBitsPerSample(),
					paramsPcm.getRtpAudioSpf(),
					paramsPcm.getIsAudioInputBigEndian()
				);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		this.pcmParser = new PcmParser(
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
	protected void parseAndConvertData(BufferExt inputBuf) throws AvInvalidPcmDataException {
		PcmInfo curFramePcmInfo = pcmParser.parsePcmData(inputBuf);

		infoQueue.add(curFramePcmInfo);
	}

}

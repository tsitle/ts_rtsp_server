package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.AudioAacInfo;
import org.tsitle.rtsp.avdata.AudioAacParser;
import org.tsitle.rtsp.avstreams.AudioStreamOutgoingAac;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidAacDataException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAac;

import java.io.FileNotFoundException;

public class ThreadDataProvAac extends ThreadDataProvBase<AudioAacInfo> {

	private final AudioAacParser aacParser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsAac Thread-specific parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOF is reached
	 */
	public ThreadDataProvAac(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderAac paramsAac,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				logMsgInterface,
				queueSize,
				debugRewindMediaFiles
			);

		//
		paramsAac.validate();

		//
		try {
			this.mediaOutgoingStream = new AudioStreamOutgoingAac(
					paramsAudioCommon.getAudioFilePath().orElseThrow()
				);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		this.aacParser = new AudioAacParser();
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
	protected void parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidAacDataException {
		AudioAacInfo curFrameAacInfo = aacParser.parseAacData(inputBuf);

		infoQueue.add(curFrameAacInfo);
	}

}

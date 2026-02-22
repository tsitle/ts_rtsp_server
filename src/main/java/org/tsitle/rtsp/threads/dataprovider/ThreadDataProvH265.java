package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.*;
import org.tsitle.rtsp.avstreams.VideoStreamOutgoingH26x;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidH26xDataException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.io.FileNotFoundException;

public class ThreadDataProvH265 extends ThreadDataProvBase<VideoH265Info> {

	private final VideoH265Parser h265Parser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOF is reached
	 */
	public ThreadDataProvH265(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
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
			this.mediaOutgoingStream = new VideoStreamOutgoingH26x(paramsVideoCommon.getVideoFilePath().orElseThrow());
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		this.h265Parser = new VideoH265Parser();
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
	protected void parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidH26xDataException {
		VideoH265Info curFrameH265Info = h265Parser.parseH265Data(
				debugStreamOffset,
				mediaOutgoingStream.getMagicBytesLengthBits() / 8,
				inputBuf
			);

		infoQueue.add(curFrameH265Info);
	}

}

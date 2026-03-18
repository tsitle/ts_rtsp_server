package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.*;
import org.tsitle.rtsp.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp.avstreams.VideoStreamOutgoingH26xFromFile;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

public class ThreadDataProvH265FromFile extends ThreadDataProvFromFileBase<VideoH265Info> {

	private final VideoH265Parser h265Parser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param avStreamIncoming Incoming A/V stream
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvH265FromFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
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
		paramsVideoCommon.validate();

		//
		this.mediaOutgoingStream = new VideoStreamOutgoingH26xFromFile(logMsgInterface, avStreamIncoming);
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
	protected void parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		VideoH265Info curFrameH265Info = h265Parser.parseH265Data(
				debugStreamOffset,
				mediaOutgoingStream.getMagicBytesLengthBits() / 8,
				inputBuf
			);

		infoQueue.add(curFrameH265Info);
	}

}

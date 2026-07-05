package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.VideoH265Info;
import org.tsitle.lib_xrtxp.avdata.VideoH265Parser;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp_server.avstreams.VideoStreamOutgoingH26xFromFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

public final class ThreadDataProvH265FromFile extends ThreadDataProvFromFileBase<VideoH265Info> {

	private final @NonNull VideoH265Parser h265Parser;

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
	protected @NonNull VideoH265Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		return h265Parser.parseH265Data(
				debugStreamOffset,
				mediaOutgoingStream.getMagicBytesLengthBits() / 8,
				inputBuf
			);
	}

}

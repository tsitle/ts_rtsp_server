package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.VideoJpegInfo;
import org.tsitle.lib_xrtxp.avdata.VideoJpegParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoMjpegFromMq;

public final class ThreadDataProvMjpegFromMq extends ThreadDataProvFromMqBase<VideoJpegInfo> {

	private final @NonNull VideoJpegParser jpegParser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public ThreadDataProvMjpegFromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, true);

		//
		this.frameGrabber = new FrameGrabberVideoMjpegFromMq(logMsgInterface, avStreamIncoming);
		this.jpegParser = new VideoJpegParser(logMsgInterface, Thread.currentThread().getName());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull VideoJpegInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		VideoJpegInfo curFrameInfo = jpegParser.parseJpegData(debugStreamOffset, inputBuf);
		//
		haveAllRequiredMetadataPackets = true;
		//
		return curFrameInfo;
	}

}

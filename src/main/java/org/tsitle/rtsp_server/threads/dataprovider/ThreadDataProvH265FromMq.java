package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.VideoH265Info;
import org.tsitle.lib_xrtxp.avdata.VideoH265Parser;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

public final class ThreadDataProvH265FromMq extends ThreadDataProvFromMqBase<VideoH265Info> {

	private final @NonNull VideoH265Parser h265Parser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public ThreadDataProvH265FromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, true);

		//
		this.frameGrabber = new FrameGrabberVideoH26xFromMq(logMsgInterface, avStreamIncoming);
		this.h265Parser = new VideoH265Parser();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull VideoH265Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		// @TODO fix this
		if (magicBytesLength == -1) {
			magicBytesLength = findH26xMagicBytesLength(inputBuf);
			magicBytesArrPtr = (magicBytesLength == 3 ?
					FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_3 :
					FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4);
		}
		//
		VideoH265Info curFrameH265Info = h265Parser.parseH265Data(
				debugStreamOffset,
				magicBytesLength,
				inputBuf
			);
		//
		haveAllRequiredMetadataPackets = true;
		//
		return curFrameH265Info;
	}

	@Override
	protected int findNextMagicBytes(final @NonNull BufferExt inputBuf) {
		return findH26xNextNalUnit(inputBuf);
	}

}

package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public final class FrameGrabberVideoMjpegFromEsFile extends FrameGrabberVideoFromEsFileBase {

	private static final byte[] MJPEG_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xD8};

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoMjpegFromEsFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsFile avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				MJPEG_FRAME_START_MAGICBYTES,
				MJPEG_FRAME_START_MAGICBYTES.length * 8
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next video frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 * @param stTimestamp Output for sample-time timestamp
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampEpochNs stTimestamp)
			throws InputStreamIoException, InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

		stTimestamp.clear();

		internalGetNextFrameWithStartCode(
				FNC_NAME,
				frameBuf,
				false,
				null,
				null,
				-1
			);
	}

}

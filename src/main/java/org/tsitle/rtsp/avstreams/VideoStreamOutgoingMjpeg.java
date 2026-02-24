package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;

public class VideoStreamOutgoingMjpeg extends VideoStreamOutgoingBase {

	private static final byte[] MJPEG_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xD8};

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public VideoStreamOutgoingMjpeg(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncoming avStreamIncoming
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
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf) throws InputStreamIoException, InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

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

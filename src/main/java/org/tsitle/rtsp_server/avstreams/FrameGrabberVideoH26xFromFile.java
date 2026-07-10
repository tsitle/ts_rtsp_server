package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public final class FrameGrabberVideoH26xFromFile extends FrameGrabberVideoFromFileBase {

	/** Magic bytes ('Start Code') for H264/H265 NAL Units - 3-byte version */
	public static final byte[] H26X_FRAME_START_MAGICBYTES_3 = {0x00, 0x00, 0x01};
	/** Magic bytes ('Start Code') for H264/H265 NAL Units - 4-byte version */
	public static final byte[] H26X_FRAME_START_MAGICBYTES_4 = {0x00, 0x00, 0x00, 0x01};

	private boolean isFirstFrame = true;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoH26xFromFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromFile avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				new byte[0],
				0
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

		/*
		 * A H264/H265 NAL Unit can either start with 0x00000001 or 0x000001.<br />
		 * Therefore, we first need to check whether to use the 3-byte or the 4-byte version.
		 */
		internalGetNextFrameWithStartCode(
				FNC_NAME,
				frameBuf,
				isFirstFrame,
				H26X_FRAME_START_MAGICBYTES_4,
				H26X_FRAME_START_MAGICBYTES_3,
				-1
			);
		isFirstFrame = false;
	}

}

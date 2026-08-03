package org.tsitle.rtsp_server.avstreams.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.MagicBytesH26xHelper;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromEsFileBase;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public final class FrameGrabberVideoH26xFromEsFile extends FrameGrabberAvFromEsFileBase {

	private boolean isFirstFrame = true;

	/**
	 * Constructor.
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoH26xFromEsFile(
				@NonNull AvStreamIncomingFromEsFile avStreamIncoming
			) {
		super(
				null,
				avStreamIncoming,
				new byte[0],
				0
			);
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoH26xFromEsFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsFile avStreamIncoming
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
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampMonotonic stTimestamp)
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
				MagicBytesH26xHelper.H26X_FRAME_START_MAGICBYTES_4,
				MagicBytesH26xHelper.H26X_FRAME_START_MAGICBYTES_3,
				-1
			);
		isFirstFrame = false;
	}

}

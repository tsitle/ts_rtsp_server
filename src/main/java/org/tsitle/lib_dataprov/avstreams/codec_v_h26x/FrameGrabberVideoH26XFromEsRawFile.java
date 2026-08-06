package org.tsitle.lib_dataprov.avstreams.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.MagicBytesH26xHelper;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsRawFile;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromEsRawFileBase;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public final class FrameGrabberVideoH26XFromEsRawFile extends FrameGrabberAvFromEsRawFileBase {

	private boolean isFirstFrame = true;

	/**
	 * Constructor.
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoH26XFromEsRawFile(
				@NonNull AvStreamIncomingFromEsRawFile avStreamIncoming
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
	public FrameGrabberVideoH26XFromEsRawFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsRawFile avStreamIncoming
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

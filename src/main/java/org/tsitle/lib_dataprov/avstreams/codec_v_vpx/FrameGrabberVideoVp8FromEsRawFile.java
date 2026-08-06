package org.tsitle.lib_dataprov.avstreams.codec_v_vpx;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsRawFile;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromEsRawFileBase;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;

public final class FrameGrabberVideoVp8FromEsRawFile extends FrameGrabberAvFromEsRawFileBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberVideoVp8FromEsRawFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsRawFile avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				VideoVp8Parser.VP8_CUSTOM_FRAME_START_MAGICBYTES,
				VideoVp8Parser.VP8_CUSTOM_FRAME_START_MAGICBYTES.length * 8
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
			throws InputStreamIoException, InputStreamEosException, AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

		stTimestamp.clear();

		internalGetNextFrameWithStartCode(
				FNC_NAME,
				frameBuf,
				false,
				null,
				null,
				VideoVp8Parser.VP8_CUSTOM_HEADER_SIZE
			);
		//
		int remainingPayloadLength = VideoVp8Parser.getRemainingVp8PayloadLengthToRead(frameBuf);
		//
		internalReadRemainingFrameForFrameWithStartCode(frameBuf, remainingPayloadLength);
	}

}

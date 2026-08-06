package org.tsitle.lib_dataprov.avstreams.codec_a_ac3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsRawFile;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromEsRawFileBase;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;

public final class FrameGrabberAudioAc3FromEsRawFile extends FrameGrabberAvFromEsRawFileBase {

	/**
	 * Constructor.
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberAudioAc3FromEsRawFile(
				@NonNull AvStreamIncomingFromEsRawFile avStreamIncoming
			) {
		super(
				null,
				avStreamIncoming,
				AudioAc3Parser.AC3_FRAME_START_MAGICBYTES,
				16
			);
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberAudioAc3FromEsRawFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsRawFile avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				AudioAc3Parser.AC3_FRAME_START_MAGICBYTES,
				16
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next audio frame from the stream.
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
				AudioAc3Parser.AC3_HEADER_SIZE_MIN
			);
		//
		int remainingPayloadLength = AudioAc3Parser.getRemainingAc3PayloadLengthToRead(frameBuf);
		//
		internalReadRemainingFrameForFrameWithStartCode(frameBuf, remainingPayloadLength);
	}

}

package org.tsitle.rtsp_server.avstreams.codec_a_ac3;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Parser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampEpochNs;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromEsFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromEsFileBase;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;

public final class FrameGrabberAudioAc3FromEsFile extends FrameGrabberAvFromEsFileBase {

	private static final byte[] AC3_FRAME_START_MAGICBYTES = {(byte)0x0B, (byte)0x77};

	/**
	 * Constructor.
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberAudioAc3FromEsFile(
				@NonNull AvStreamIncomingFromEsFile avStreamIncoming
			) {
		super(
				null,
				avStreamIncoming,
				AC3_FRAME_START_MAGICBYTES,
				16
			);
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public FrameGrabberAudioAc3FromEsFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsFile avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				AC3_FRAME_START_MAGICBYTES,
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
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampEpochNs stTimestamp)
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

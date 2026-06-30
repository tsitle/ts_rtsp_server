package org.tsitle.rtsp_server.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.AudioAacParser;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public class AudioStreamOutgoingAacFromFile extends AvStreamOutgoingFromFileBase {

	private static final byte[] AAC_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xF0};  // only 12 bits

	/**
	 * Constructor.
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public AudioStreamOutgoingAacFromFile(
				@NonNull AvStreamIncomingFromFile avStreamIncoming
			) {
		super(
				null,
				avStreamIncoming,
				AAC_FRAME_START_MAGICBYTES,
				12
			);
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public AudioStreamOutgoingAacFromFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromFile avStreamIncoming
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				AAC_FRAME_START_MAGICBYTES,
				12
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Checks if we can still read data from the stream.
	 * @return True if the end of the stream has been reached, false otherwise
	 */
	@Override
	public boolean haveEos() {
		return (getCachedDataLengthForFramesWithStartCode() == 0 && avStreamIncoming.haveEos());
	}

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
				AudioAacParser.AAC_HEADER_SIZE_MAX
			);
		//
		int remainingPayloadLength = AudioAacParser.getRemainingAacPayloadLengthToRead(frameBuf);
		//
		internalReadRemainingFrameForFrameWithStartCode(frameBuf, remainingPayloadLength);
	}

}

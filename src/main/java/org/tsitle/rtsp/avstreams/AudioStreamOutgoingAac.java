package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.AudioAacParser;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.io.FileNotFoundException;

public class AudioStreamOutgoingAac extends AvStreamOutgoingBase {

	private static final byte[] AAC_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xF0};  // only 12 bits

	/**
	 * Constructor.
	 * @param filename Audio file name
	 * @throws FileNotFoundException If the audio file cannot be found
	 */
	public AudioStreamOutgoingAac(@NonNull String filename) throws FileNotFoundException {
		super(
				null,
				AAC_FRAME_START_MAGICBYTES,
				12,
				filename
			);
	}

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param filename Audio file name
	 * @throws FileNotFoundException If the audio file cannot be found
	 */
	public AudioStreamOutgoingAac(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull String filename
			) throws FileNotFoundException {
		super(
				logMsgInterface,
				AAC_FRAME_START_MAGICBYTES,
				12,
				filename
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Checks if there could be more frames in the stream
	 * @return True if there could be more frames, false otherwise
	 */
	@Override
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean hasMoreFrames() {
		return (getCachedDataLengthForFramesWithStartCode() > 0 || bisAvailableBytes() > 0);
	}

	/**
	 * Reads the next audio frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf)
			throws InputStreamIoException, InputStreamEofException, AvInvalidCodecDataException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextFrame()";

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

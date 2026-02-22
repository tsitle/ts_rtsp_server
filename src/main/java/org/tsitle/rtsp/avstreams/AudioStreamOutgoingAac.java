package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.AudioAacParser;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidAacDataException;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

import java.io.FileNotFoundException;

public class AudioStreamOutgoingAac extends AvStreamOutgoingBase {

	private static final byte[] AAC_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xF0};  // only 12 bits

	/**
	 * Constructor.
	 * @param filename Audio file name
	 * @throws FileNotFoundException If the audio file cannot be found
	 */
	public AudioStreamOutgoingAac(@NonNull String filename) throws FileNotFoundException {
		super(AAC_FRAME_START_MAGICBYTES, 12, filename);
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
	public void getNextFrame(@NonNull BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException {
		internalGetNextFrameWithStartCode(
				frameBuf,
				false,
				null,
				null,
				AudioAacParser.AAC_HEADER_SIZE_MAX
			);
		//
		int remainingPayloadLength;
		try {
			remainingPayloadLength = AudioAacParser.getRemainingAacPayloadLengthToRead(frameBuf);
		} catch (AvInvalidAacDataException e) {
			throw new InputStreamIoException("AvInvalidAacDataException caught: " + e.getMessage());
		}
		//
		internalReadRemainingFrameForFrameWithStartCode(frameBuf, remainingPayloadLength);
	}

}

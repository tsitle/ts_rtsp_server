package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

import java.io.FileNotFoundException;

public class VideoStreamOutgoingMjpeg extends VideoStreamOutgoingBase {

	private static final byte[] MJPEG_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xD8};

	/**
	 * Constructor.
	 * @param filename Video file name
	 * @throws FileNotFoundException If the video file cannot be found
	 */
	public VideoStreamOutgoingMjpeg(@NonNull String filename) throws FileNotFoundException {
		super(MJPEG_FRAME_START_MAGICBYTES, MJPEG_FRAME_START_MAGICBYTES.length * 8, filename);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next video frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException {
		internalGetNextFrameWithStartCode(
				frameBuf,
				false,
				null,
				null
			);
	}

}

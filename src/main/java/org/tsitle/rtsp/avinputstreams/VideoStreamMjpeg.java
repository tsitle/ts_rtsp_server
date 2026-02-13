package org.tsitle.rtsp.avinputstreams;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

import java.io.FileNotFoundException;

public class VideoStreamMjpeg extends VideoStreamBase {

	private static final byte[] MJPEG_FRAME_START_MAGICBYTES = {(byte)0xFF, (byte)0xD8};

	/**
	 * Constructor.
	 * @param filename Video file name
	 * @throws FileNotFoundException If the video file cannot be found
	 */
	public VideoStreamMjpeg(String filename) throws FileNotFoundException {
		super(MJPEG_FRAME_START_MAGICBYTES, filename);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next video frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 */
	public void getNextFrame(BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException {
		internalGetNextFrame(frameBuf, false, null, null);
	}

}

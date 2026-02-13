package org.tsitle.rtsp.avinputstreams;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

public interface AvInputStreamInterface {

	/**
	 * Checks if there could be more frames/samples in the stream
	 * @return True if there could be more frames/samples, false otherwise
	 */
	boolean hasMoreFrames();

	/**
	 * Reads the next video frame or audio samples from the stream
	 *
	 * @param frameBuf Output buffer to store the frame/samples in
	 */
	void getNextFrame(BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException;

	/**
	 * Rewinds the stream to the beginning
	 */
	void rewind();

}

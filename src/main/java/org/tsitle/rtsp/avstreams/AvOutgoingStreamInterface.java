package org.tsitle.rtsp.avstreams;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

/**
 * Interface for outgoing streams that provide video frames or audio samples.
 */
public interface AvOutgoingStreamInterface {

	/**
	 * Gets the length of the magic bytes array for frame start detection.
	 * @return Length of the magic bytes array
	 */
	int getMagicBytesLength();

	/**
	 * Checks if there could be more frames/samples in the stream
	 * @return True if there could be more frames/samples, false otherwise
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
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

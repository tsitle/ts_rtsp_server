package org.tsitle.rtsp.avinputstreams;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;

import java.io.FileNotFoundException;

public class VideoStreamH264 extends VideoStreamBase {

	/** Magic bytes ('Start Code') for HEVC (aka H264) NAL Units - 3-byte version */
	private static final byte[] H264_FRAME_START_MAGICBYTES_3 = {0x00, 0x00, 0x01};
	/** Magic bytes ('Start Code') for HEVC (aka H264) NAL Units - 4-byte version */
	private static final byte[] H264_FRAME_START_MAGICBYTES_4 = {0x00, 0x00, 0x00, 0x01};

	private boolean isFirstFrame = true;

	/**
	 * Constructor.
	 * @param filename Video file name
	 * @throws FileNotFoundException If the video file cannot be found
	 */
	public VideoStreamH264(String filename) throws FileNotFoundException {
		super(new byte[0], filename);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next video frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 */
	@Override
	public void getNextFrame(BufferExt frameBuf) throws InputStreamIoException, InputStreamEofException {
		/*
		 * A HEVC (aka H264) NAL Unit can either start with 0x00000001 or 0x000001.<br />
		 * Therefore, we first need to check whether to use the 3-byte or the 4-byte version.
		 */
		internalGetNextFrame(frameBuf, isFirstFrame, H264_FRAME_START_MAGICBYTES_4, H264_FRAME_START_MAGICBYTES_3);
		isFirstFrame = false;
	}

}

package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.io.FileNotFoundException;

public abstract class VideoStreamOutgoingBase extends AvStreamOutgoingBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param frameStartMagicbytes Magic bytes array for frame start detection
	 * @param magicBytesLengthInBits Length of the magic bytes array in bits
	 * @param filename Video file name
	 * @throws FileNotFoundException If the video file cannot be found
	 */
	protected VideoStreamOutgoingBase(
				@NonNull LogMsgInterface logMsgInterface,
				byte[] frameStartMagicbytes,
				int magicBytesLengthInBits,
				@NonNull String filename
			) throws FileNotFoundException {
		super(
				logMsgInterface,
				frameStartMagicbytes,
				magicBytesLengthInBits,
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

}

package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;

import java.io.FileNotFoundException;

public abstract class VideoStreamOutgoingBase extends AvStreamOutgoingBase {

	/**
	 * Constructor.
	 * @param frameStartMagicbytes Magic bytes array for frame start detection
	 * @param magicBytesLengthInBits Length of the magic bytes array in bits
	 * @param filename Video file name
	 * @throws FileNotFoundException If the video file cannot be found
	 */
	protected VideoStreamOutgoingBase(
				byte[] frameStartMagicbytes,
				int magicBytesLengthInBits,
				@NonNull String filename
			) throws FileNotFoundException {
		super(frameStartMagicbytes, magicBytesLengthInBits, filename);
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

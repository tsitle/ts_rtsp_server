package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public abstract class VideoStreamOutgoingFromFileBase extends AvStreamOutgoingFromFileBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 * @param frameStartMagicbytes Magic bytes array for frame start detection
	 * @param magicBytesLengthInBits Length of the magic bytes array in bits
	 */
	protected VideoStreamOutgoingFromFileBase(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromFile avStreamIncoming,
				byte[] frameStartMagicbytes,
				int magicBytesLengthInBits
			) {
		super(
				logMsgInterface,
				avStreamIncoming,
				frameStartMagicbytes,
				magicBytesLengthInBits
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

}

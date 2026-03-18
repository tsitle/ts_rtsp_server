package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;

public abstract class VideoStreamOutgoingFromMqBase extends AvStreamOutgoingFromMqBase {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	protected VideoStreamOutgoingFromMqBase(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, avStreamIncoming);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Checks if we can still read data from the stream.
	 * @return True if the end of the stream has been reached, false otherwise
	 */
	@Override
	public boolean haveEos() {
		return avStreamIncoming.haveEos();
	}

}

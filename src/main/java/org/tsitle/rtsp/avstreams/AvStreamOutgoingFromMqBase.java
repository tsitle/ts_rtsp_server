package org.tsitle.rtsp.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;

public abstract class AvStreamOutgoingFromMqBase extends AvStreamOutgoingBase<AvStreamIncomingFromMq> {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	protected AvStreamOutgoingFromMqBase(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, avStreamIncoming);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Reads the next video frame from the stream.
	 * @param frameBuf Output buffer to store the frame in
	 */
	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf) throws InputStreamIoException, InputStreamEosException {
		avStreamIncoming.readFrame(frameBuf);
	}

}

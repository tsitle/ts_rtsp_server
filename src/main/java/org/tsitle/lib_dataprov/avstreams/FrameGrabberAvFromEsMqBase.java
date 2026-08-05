package org.tsitle.lib_dataprov.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public abstract class FrameGrabberAvFromEsMqBase extends FrameGrabberAvBase<AvStreamIncomingFromEsMq> {

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avStreamIncoming Incoming A/V stream
	 */
	protected FrameGrabberAvFromEsMqBase(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsMq avStreamIncoming
			) {
		super(logMsgInterface, avStreamIncoming);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public int getMinimumMagicBytesLengthBits() {
		throw new RuntimeException(getClass().getSimpleName() + ".getMinimumMagicBytesLengthBits(): not implemented");
	}

	@Override
	public boolean haveEos() {
		return avStreamIncoming.haveEos();
	}

	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamIoException, InputStreamEosException {
		avStreamIncoming.readFrame(frameBuf, stTimestamp);
	}

}

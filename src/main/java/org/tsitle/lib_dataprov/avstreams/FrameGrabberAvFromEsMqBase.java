package org.tsitle.lib_dataprov.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

public abstract class FrameGrabberAvFromEsMqBase extends FrameGrabberAvBase {

	protected @NonNull AvStreamIncomingFromEsMq avstricFromEsMq;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avstricFromEsMq Incoming A/V stream
	 */
	protected FrameGrabberAvFromEsMqBase(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromEsMq avstricFromEsMq
			) {
		super(logMsgInterface, avstricFromEsMq);

		this.avstricFromEsMq = avstricFromEsMq;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public int getMinimumMagicBytesLengthBits() {
		throw new RuntimeException(getClass().getSimpleName() + ".getMinimumMagicBytesLengthBits(): not implemented");
	}

	@Override
	public boolean haveEos() {
		return avstricFromEsMq.haveEos();
	}

	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamIoException, InputStreamEosException {
		avstricFromEsMq.readFrame(frameBuf, stTimestamp);
	}

}

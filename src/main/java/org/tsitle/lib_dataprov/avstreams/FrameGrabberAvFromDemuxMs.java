package org.tsitle.lib_dataprov.avstreams;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_dataprov.exceptions.InputStreamIoException;
import org.tsitle.lib_dataprov.exceptions.InputStreamThreadEndedException;

public class FrameGrabberAvFromDemuxMs extends FrameGrabberAvBase {

	protected @NonNull AvStreamIncomingFromDemuxMs avstricFromDemuxMs;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message interface
	 * @param avstricFromDemuxMs Incoming A/V stream
	 */
	public FrameGrabberAvFromDemuxMs(
				@Nullable LogMsgInterface logMsgInterface,
				@NonNull AvStreamIncomingFromDemuxMs avstricFromDemuxMs
			) {
		super(logMsgInterface, avstricFromDemuxMs);

		this.avstricFromDemuxMs = avstricFromDemuxMs;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public int getMinimumMagicBytesLengthBits() {
		throw new RuntimeException(getClass().getSimpleName() + ".getMinimumMagicBytesLengthBits(): not implemented");
	}

	@Override
	public boolean haveEos() {
		return avstricFromDemuxMs.haveEos();
	}

	@Override
	public void getNextFrame(@NonNull BufferExt frameBuf, @NonNull TimestampMonotonic stTimestamp)
			throws InputStreamIoException, InputStreamEosException, InputStreamThreadEndedException {
		avstricFromDemuxMs.readFrame(frameBuf, stTimestamp);
	}

}

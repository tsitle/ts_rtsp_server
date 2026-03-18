package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingBase;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.ThreadBase;

public abstract class ThreadDataProvBase<I extends CodecInfoInterface<I>, AVSTROG extends AvStreamOutgoingBase<?>> extends ThreadBase {

	protected long debugStreamOffset = 0;

	protected AVSTROG mediaOutgoingStream;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 */
	protected ThreadDataProvBase(@NonNull LogMsgInterface logMsgInterface) {
		super(logMsgInterface);

		//
		this.mediaOutgoingStream = null;  // needs to be set by the child class
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public abstract boolean haveEos();

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public abstract boolean haveFullInputQueue();

	public abstract int getInputQueueSize();

	public abstract void getNextFrame(@NonNull BufferExt buf, @NonNull I infoObj) throws InputStreamEosException;

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	public abstract void notifyCongestionLevelChange(@SuppressWarnings("unused") int congestionLevel);

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void stopThreadHook() {
		// nothing to do
	}

}

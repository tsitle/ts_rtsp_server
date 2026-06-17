package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.rtsp_server.avstreams.AvStreamOutgoingBase;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.ThreadBase;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ThreadDataProvBase<I extends CodecInfoInterface<I>, AVSTROG extends AvStreamOutgoingBase<?>> extends ThreadBase {

	protected long debugStreamOffset = 0;

	protected AVSTROG mediaOutgoingStream;

	protected final ReentrantLock lock = new ReentrantLock();
	/** Condition to signal that a frame has been removed from the queue or the thread has been requested to stop */
	protected final Condition stateChanged = lock.newCondition();

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
		lock.lock();
		try {
			stateChanged.signalAll();
		} finally {
			lock.unlock();
		}
	}

}

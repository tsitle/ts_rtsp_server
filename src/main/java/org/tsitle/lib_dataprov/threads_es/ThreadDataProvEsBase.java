package org.tsitle.lib_dataprov.threads_es;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.ThreadDpBase;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvBase;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpCommon;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.types.TimestampMonotonic;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.lib_dataprov.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_dataprov.exceptions.InputStreamThreadEndedException;

import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ThreadDataProvEsBase<I extends CodecInfoInterface<I>> extends ThreadDpBase {

	protected final @NonNull ParamsThreadDpCommon paramsCommon;

	protected long debugStreamOffset = 0;

	protected @Nullable FrameGrabberAvBase frameGrabber;

	protected final ReentrantLock lock = new ReentrantLock();
	/** Condition to signal that a frame has been removed from the queue or the thread has been requested to stop */
	protected final Condition stateChanged = lock.newCondition();

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	protected ThreadDataProvEsBase(@NonNull ParamsThreadDpCommon paramsCommon) {
		super(paramsCommon.getLogMsgInterface().orElseThrow());

		//
		paramsCommon.validate();
		this.paramsCommon = paramsCommon.clone();

		//
		this.frameGrabber = null;  // needs to be set by the child class
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public abstract boolean haveEos();

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public abstract boolean haveFullInputQueue();

	public abstract int getInputQueueSize();

	public abstract void getNextFrame(@NonNull BufferExt buf, @NonNull TimestampMonotonic stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException, InputStreamThreadEndedException, AvInvalidCodecDataException;

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	public abstract void notifyCongestionLevelChange(@SuppressWarnings("unused") int congestionLevel);

	/**
	 * Returns the video frames per second if available
	 * @return Video frames per second
	 */
	public Optional<Double> getVideoFps() {
		if (frameGrabber == null) {
			return Optional.empty();
		}
		return frameGrabber.getVideoFps();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void internalRun(@NonNull String fncName) {
		try {
			logDebug(fncName, "Prepare ASI and FG");
			createAvStreamIncoming();
			createFrameGrabber();
		} catch (AvCannotOpenInputException e) {
			logError(fncName, "cannot open input: " + e.getMessage());
			return;
		}

		//
		isRunning.set(true);
		logDebug(fncName, "Thread started");

		//
		try {
			while (! doStop.get() && ! haveEos()) {
				mainLoop();
				//noinspection BusyWait
				Thread.sleep(0L, 100_000);
			}
		} catch (InterruptedException e) {
			logError(fncName, "InterruptedException caught");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(fncName, "Exception caught: " + e.getMessage());
		} finally {
			closeAvStreamIncoming();
			//
			isRunning.set(false);
			logDebug(fncName, "Thread ended");
		}
	}

	@Override
	protected void stopThreadHook() {
		lock.lock();
		try {
			stateChanged.signalAll();
		} finally {
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void createAvStreamIncoming() throws AvCannotOpenInputException;

	protected abstract void createFrameGrabber();

	protected abstract void closeAvStreamIncoming();

	// -----------------------------------------------------------------------------------------------------------------

	protected void mainLoop() throws InterruptedException {
		Thread.sleep(50);
	}

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract @NonNull I parseAndConvertData(@NonNull BufferExt ioBuf) throws AvInvalidCodecDataException;

	protected abstract @NonNull I parseData(@NonNull BufferView inputBv) throws AvInvalidCodecDataException;

}

package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.CodecInfoInterface;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromFileBase;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.exceptions.AvCannotOpenInputException;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamEosException;
import org.tsitle.rtsp_server.exceptions.InputStreamIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;

public abstract class ThreadDataProvFromFileBase<I extends CodecInfoInterface<I>>
		extends ThreadDataProvBase<I, FrameGrabberAvFromFileBase> {

	private static class DataQueueEntry {
		final @NonNull BufferExt buf = new BufferExt();
		final @NonNull TimestampEpochNs stTimestamp = TimestampEpochNs.ofEmpty();
	}

	private final ArrayList<@NonNull DataQueueEntry> dataQueue = new ArrayList<>();
	protected final ArrayList<@Nullable I> infoQueue = new ArrayList<>();

	private final AtomicBoolean eosReached = new AtomicBoolean(false);
	private long frameCountInp = 0;

	private final AtomicInteger queueIxRead = new AtomicInteger(0);
	private final AtomicInteger queueIxWrite = new AtomicInteger(0);
	private final AtomicInteger queueAvail = new AtomicInteger(0);
	private final AtomicBoolean queueBlockedState = new AtomicBoolean(false);
	/** Condition to signal that the queue has been unblocked */
	private final Condition queueBlockedChanged = lock.newCondition();

	private final boolean doDebugRewindMediaFiles;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	protected ThreadDataProvFromFileBase(
				@NonNull LogMsgInterface logMsgInterface,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(logMsgInterface);

		if (queueSize <= 0) {
			throw new IllegalArgumentException("queueSize must be positive");
		}

		//
		for (int i = 0; i < queueSize; ++i) {
			dataQueue.add(new DataQueueEntry());
			infoQueue.add(null);
		}
		this.doDebugRewindMediaFiles = debugRewindMediaFiles;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		//
		try {
			while (! (doStop.get() || eosReached.get())) {
				mainLoop();
			}
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception: " + e.getMessage());
		} finally {
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public synchronized boolean haveEos() {
		return eosReached.get();
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	@Override
	public synchronized boolean haveFullInputQueue() {
		return ((queueAvail.get() > 0 && eosReached.get()) || (queueAvail.get() == dataQueue.size()));
	}

	@Override
	public synchronized int getInputQueueSize() {
		return queueAvail.get();
	}

	@Override
	public void getNextFrame(@NonNull BufferExt buf, @NonNull TimestampEpochNs stTimestamp, @NonNull I infoObj)
			throws InputStreamEosException {
		if (eosReached.get()) {
			throw new InputStreamEosException();
		}

		if (! waitForQueueUnblockedAndThenBlock(true)) {
			if (doStop.get()) {
				buf.clear();
				infoObj.reset();
			}
			return;
		}
		lock.lock();
		try {
			buf.copyOf(dataQueue.get(queueIxRead.get()).buf);
			stTimestamp.copyFrom(dataQueue.get(queueIxRead.get()).stTimestamp);
			I tmpInfoObj = infoQueue.get(queueIxRead.get());
			if (tmpInfoObj == null) {
				throw new IllegalStateException("tmpInfoObj == null");
			}
			infoObj.copyOf(tmpInfoObj);
			if (queueIxRead.incrementAndGet() >= dataQueue.size()) {
				queueIxRead.set(0);
			}
			queueAvail.decrementAndGet();

			//
			stateChanged.signalAll();
		} finally {
			queueBlockedState.set(false);
			queueBlockedChanged.signalAll();
			lock.unlock();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract I parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException {
		// fill the queue first
		while (! (doStop.get() || eosReached.get()) && queueAvail.get() < dataQueue.size()) {
			try {
				acquireData();
			} catch (InputStreamEosException e) {
				// nothing to do
			}
		}
		// wait until an element from the queue has been removed
		if (! (doStop.get() || eosReached.get())) {
			lock.lock();
			try {
				stateChanged.await();
			} finally {
				lock.unlock();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void acquireData() throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".acquireData()";

		if (frameGrabber.haveEos()) {
			if (doDebugRewindMediaFiles) {
				logDebug(FNC_NAME, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames, rewinding");
				try {
					frameGrabber.rewind();
				} catch (AvCannotOpenInputException e) {
					logError(FNC_NAME, "AvCannotOpenInputException caught while rewinding: " + e.getMessage());
					// we have reached the end of the input
					eosReached.set(true);
					return;
				}
			} else {
				if (! eosReached.get()) {
					logDebug(FNC_NAME, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames");
				}
				eosReached.set(true);
				return;
			}
		}

		//
		if (! waitForQueueUnblockedAndThenBlock(false)) {
			return;
		}
		lock.lock();
		try {
			acquireData_sub(FNC_NAME);
		} finally {
			queueBlockedState.set(false);
			queueBlockedChanged.signalAll();
			lock.unlock();
		}
	}

	private void acquireData_sub(@NonNull String fncName) {
		// get the next frame from the input, as well as its size
		BufferExt tmpFrameBufPtr = dataQueue.get(queueIxWrite.get()).buf;
		TimestampEpochNs tmpStTimestampPtr = dataQueue.get(queueIxWrite.get()).stTimestamp;
		try {
			frameGrabber.getNextFrame(tmpFrameBufPtr, tmpStTimestampPtr);
			if (doStop.get()) {
				return;
			}
			if (tmpFrameBufPtr.getUsed() < frameGrabber.getMinimumMagicBytesLengthBits() / 8) {
				// we have reached the end of the input
				logDebug(fncName, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames -- getNextFrame");
				eosReached.set(true);
				throw new InputStreamEosException();
			}
		} catch (InputStreamIoException | InputStreamEosException e) {
			if (doStop.get()) {
				return;
			}
			if (e instanceof InputStreamIoException) {
				logError(fncName, "InputStreamIoException caught while reading next frame: " + e.getMessage());
				// we have reached the end of the input
				eosReached.set(true);
			} else {
				logDebug(fncName, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames -- InputStreamEosException");
				// try to rewind in the next iteration
			}
			return;
		} catch (AvInvalidCodecDataException e) {
			logError(fncName, "AvInvalidCodecDataException caught: " + e.getMessage());
			// we have reached the end of the input
			eosReached.set(true);
			return;
		}
		if (tmpFrameBufPtr.getUsed() == 0) {  // sanity check
			// we have reached the end of the input
			logDebug(fncName, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames -- empty frame buf");
			eosReached.set(true);
			return;
		}

		//
		try {
			I tmpInfoObj = parseAndConvertData(tmpFrameBufPtr);
			infoQueue.set(queueIxWrite.get(), tmpInfoObj);
		} catch (Exception e) {
			logError(fncName, "caught: " + e);
			eosReached.set(true);
			return;
		}

		if (queueIxWrite.incrementAndGet() >= dataQueue.size()) {
			queueIxWrite.set(0);
		}
		queueAvail.incrementAndGet();
		debugStreamOffset += tmpFrameBufPtr.getUsed();
		++frameCountInp;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean waitForQueueUnblockedAndThenBlock(boolean needAvailFrame) throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".waitForQueueUnblockedAndThenBlock()";

		lock.lock();
		try {
			final int MAX_TIMEOUT = 1000;
			int timeoutCnt = 0;
			while (! eosReached.get() && ++timeoutCnt <= MAX_TIMEOUT) {
				while (! doStop.get() && queueBlockedState.get()) {
					queueBlockedChanged.await();
				}
				if (doStop.get()) {
					return false;
				}
				if (needAvailFrame && queueAvail.get() == 0) {
					Thread.sleep(1);
					continue;
				}
				queueBlockedState.set(true);
				break;
			}
			if (timeoutCnt >= MAX_TIMEOUT) {
				logWarn(FNC_NAME, "timeout waiting for queue");
				return false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		} finally {
			lock.unlock();
		}
		if (eosReached.get()) {
			throw new InputStreamEosException();
		}
		return true;
	}

}

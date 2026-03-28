package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingFromFileBase;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ThreadDataProvFromFileBase<I extends CodecInfoInterface<I>>
		extends ThreadDataProvBase<I, AvStreamOutgoingFromFileBase> {

	private final ArrayList<@NonNull BufferExt> dataQueue = new ArrayList<>();
	protected final ArrayList<@Nullable I> infoQueue = new ArrayList<>();

	private final AtomicBoolean eosReached = new AtomicBoolean(false);
	private long frameCountInp = 0;

	private final AtomicInteger queueIxRead = new AtomicInteger(0);
	private final AtomicInteger queueIxWrite = new AtomicInteger(0);
	private final AtomicInteger queueAvail = new AtomicInteger(0);
	private final AtomicBoolean queueIsLocked = new AtomicBoolean(false);

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
			dataQueue.add(new BufferExt());
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
	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull I infoObj) throws InputStreamEosException {
		if (eosReached.get()) {
			throw new InputStreamEosException();
		}

		//
		if (! waitForQueueUnlocked(true)) {
			if (doStop.get()) {
				buf.clear();
				infoObj.reset();
			}
			return;
		}

		//
		queueIsLocked.set(true);
		buf.copyOf(dataQueue.get(queueIxRead.get()));
		I tmpInfoObj = infoQueue.get(queueIxRead.get());
		if (tmpInfoObj == null) {
			queueIsLocked.set(false);
			throw new IllegalStateException("tmpInfoObj == null");
		}
		infoObj.copyOf(tmpInfoObj);
		if (queueIxRead.incrementAndGet() >= dataQueue.size()) {
			queueIxRead.set(0);
		}
		queueAvail.decrementAndGet();
		queueIsLocked.set(false);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract I parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException {
		if (! (doStop.get() || eosReached.get() || queueIsLocked.get()) && queueAvail.get() < dataQueue.size()) {
			try {
				acquireData();
			} catch (InputStreamEosException e) {
				// nothing to do
			}
		} else {
			Thread.sleep(1);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void acquireData() throws InputStreamEosException {
		final String FNC_NAME = getClass().getSimpleName() + ".acquireData()";

		if (mediaOutgoingStream.haveEos()) {
			if (doDebugRewindMediaFiles) {
				logDebug(FNC_NAME, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames, rewinding");
				try {
					mediaOutgoingStream.rewind();
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
		if (! waitForQueueUnlocked(false)) {
			return;
		}

		// get the next frame from the input, as well as its size
		queueIsLocked.set(true);
		BufferExt tmpFrameBufPtr = dataQueue.get(queueIxWrite.get());
		try {
			mediaOutgoingStream.getNextFrame(tmpFrameBufPtr);
			if (doStop.get()) {
				queueIsLocked.set(false);
				return;
			}
			if (tmpFrameBufPtr.getUsed() < mediaOutgoingStream.getMagicBytesLengthBits() / 8) {
				// we have reached the end of the input
				logDebug(FNC_NAME, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames -- getNextFrame");
				eosReached.set(true);
				queueIsLocked.set(false);
				throw new InputStreamEosException();
			}
		} catch (InputStreamIoException | InputStreamEosException e) {
			if (doStop.get()) {
				queueIsLocked.set(false);
				return;
			}
			if (e instanceof InputStreamIoException) {
				logError(FNC_NAME, "InputStreamIoException caught while reading next frame: " + e.getMessage());
			} else {
				logDebug(FNC_NAME, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames -- InputStreamEosException");
			}
			// we have reached the end of the input
			eosReached.set(true);
			queueIsLocked.set(false);
			return;
		} catch (AvInvalidCodecDataException e) {
			logError(FNC_NAME, "AvInvalidCodecDataException caught: " + e.getMessage());
			// we have reached the end of the input
			eosReached.set(true);
			queueIsLocked.set(false);
			return;
		}
		if (tmpFrameBufPtr.getUsed() == 0) {  // sanity check
			// we have reached the end of the input
			logDebug(FNC_NAME, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames -- empty frame buf");
			eosReached.set(true);
			queueIsLocked.set(false);
			return;
		}

		//
		try {
			I tmpInfoObj = parseAndConvertData(tmpFrameBufPtr);
			infoQueue.set(queueIxWrite.get(), tmpInfoObj);
		} catch (Exception e) {
			logError(FNC_NAME, "caught: " + e);
			eosReached.set(true);
			queueIsLocked.set(false);
			return;
		}

		if (queueIxWrite.incrementAndGet() >= dataQueue.size()) {
			queueIxWrite.set(0);
		}
		queueAvail.incrementAndGet();
		debugStreamOffset += tmpFrameBufPtr.getUsed();
		++frameCountInp;
		queueIsLocked.set(false);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean waitForQueueUnlocked(boolean needAvailFrame) throws InputStreamEosException {
		while (! (doStop.get() || eosReached.get()) && (queueIsLocked.get() || (needAvailFrame && queueAvail.get() == 0))) {
			try {
				wait(0, 500_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();  // restore flag
				break;
			}
		}
		if (doStop.get()) {
			return false;
		}
		if (eosReached.get()) {
			throw new InputStreamEosException();
		}
		return true;
	}

}

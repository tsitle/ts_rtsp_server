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

public abstract class ThreadDataProvFromFileBase<I extends CodecInfoInterface<I>>
		extends ThreadDataProvBase<I, AvStreamOutgoingFromFileBase> {

	private final ArrayList<@NonNull BufferExt> dataQueue = new ArrayList<>();
	protected final ArrayList<@Nullable I> infoQueue = new ArrayList<>();

	private boolean isFirstRun = true;
	private boolean eosReached = false;
	private long frameCountInp = 0;

	private int queueIxRead = 0;
	private int queueIxWrite = 0;
	private int queueAvail = 0;

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
			while (! doStop.get() && (isFirstRun || queueAvail > 0)) {
				mainLoop();
				isFirstRun = false;
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
		return eosReached;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	@Override
	public synchronized boolean haveFullInputQueue() {
		return ((queueAvail > 0 && haveEos()) || (queueAvail == dataQueue.size()));
	}

	@Override
	public synchronized int getInputQueueSize() {
		return queueAvail;
	}

	@Override
	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull I infoObj) throws InputStreamEosException {
		if (haveEos()) {
			throw new InputStreamEosException();
		}
		buf.copyOf(dataQueue.get(queueIxRead));
		I tmpInfoObj = infoQueue.get(queueIxRead);
		if (tmpInfoObj == null) {
			throw new IllegalStateException("tmpInfoObj == null");
		}
		infoObj.copyOf(tmpInfoObj);
		if (++queueIxRead >= dataQueue.size()) {
			queueIxRead = 0;
		}
		--queueAvail;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract I parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException {
		if (isFirstRun || (! haveEos() && queueAvail < dataQueue.size())) {
			acquireData();
		} else {
			Thread.sleep(1);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void acquireData() {
		final String FNC_NAME = getClass().getSimpleName() + ".acquireData()";

		if (mediaOutgoingStream.haveEos()) {
			if (doDebugRewindMediaFiles) {
				logDebug(FNC_NAME, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames, rewinding");
				try {
					mediaOutgoingStream.rewind();
				} catch (AvCannotOpenInputException e) {
					logError(FNC_NAME, "AvCannotOpenInputException caught while rewinding: " + e.getMessage());
					// we have reached the end of the input
					eosReached = true;
					return;
				}
			} else {
				if (! eosReached) {
					logDebug(FNC_NAME, "EOS reached after " + Long.toUnsignedString(frameCountInp) + " frames");
				}
				eosReached = true;
				return;
			}
		}

		// get the next frame from the input, as well as its size
		BufferExt tmpFrameBufPtr = dataQueue.get(queueIxWrite);
		try {
			mediaOutgoingStream.getNextFrame(tmpFrameBufPtr);
			if (tmpFrameBufPtr.getUsed() < mediaOutgoingStream.getMagicBytesLengthBits() / 8) {
				// we have reached the end of the input
				throw new InputStreamEosException();
			}
		} catch (InputStreamIoException | InputStreamEosException e) {
			if (doStop.get()) {
				return;
			}
			if (e instanceof InputStreamIoException) {
				logError(FNC_NAME, "InputStreamIoException caught while reading next frame: " + e.getMessage());
			}
			// we have reached the end of the input
			eosReached = true;
			return;
		} catch (AvInvalidCodecDataException e) {
			logError(FNC_NAME, "AvInvalidCodecDataException caught: " + e.getMessage());
			// we have reached the end of the input
			eosReached = true;
			return;
		}
		if (tmpFrameBufPtr.getUsed() == 0) {  // sanity check
			// we have reached the end of the input
			eosReached = true;
			return;
		}

		//
		try {
			I tmpInfoObj = parseAndConvertData(tmpFrameBufPtr);
			infoQueue.set(queueIxWrite, tmpInfoObj);
		} catch (Exception e) {
			logError(FNC_NAME, "caught: " + e);
			eosReached = true;
			return;
		}

		if (++queueIxWrite >= dataQueue.size()) {
			queueIxWrite = 0;
		}
		++queueAvail;
		debugStreamOffset += tmpFrameBufPtr.getUsed();
		++frameCountInp;
	}

}

package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingFromFileBase;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class ThreadDataProvFromFileBase<I extends CodecInfoInterface<I>> extends ThreadDataProvBase<I, AvStreamOutgoingFromFileBase> {

	private final Queue<@NonNull BufferExt> bufferQueue = new ConcurrentLinkedQueue<>();
	protected final Queue<@NonNull I> infoQueue = new ConcurrentLinkedQueue<>();

	private long frameCountInp = 0;
	private long frameNrOutp = 1;
	private long eosAfterFrameNr = -1;

	private final int queueSize;
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
		this.queueSize = queueSize;
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
			boolean isFirstRun = true;
			while (! doStop.get() && (isFirstRun || ! bufferQueue.isEmpty())) {
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
		return (eosAfterFrameNr >= 0L && frameNrOutp > eosAfterFrameNr);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	@Override
	public synchronized boolean haveFullInputQueue() {
		return ((! bufferQueue.isEmpty() && haveEos()) || (bufferQueue.size() >= queueSize));
	}

	@Override
	public synchronized int getInputQueueSize() {
		return bufferQueue.size();
	}

	@Override
	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull I infoObj) throws InputStreamEosException {
		if (haveEos() || bufferQueue.isEmpty() || infoQueue.isEmpty()) {
			throw new InputStreamEosException();
		}
		buf.copyOf(bufferQueue.poll());
		//noinspection DataFlowIssue
		infoObj.copyOf(infoQueue.poll());
		++frameNrOutp;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException;

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException {
		if (eosAfterFrameNr < 0L && bufferQueue.size() < queueSize) {
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
				logDebug(FNC_NAME, "EOS reached, rewinding");
				try {
					mediaOutgoingStream.rewind();
				} catch (AvCannotOpenInputException e) {
					logError(FNC_NAME, "AvCannotOpenInputException caught while rewinding: " + e.getMessage());
					// we have reached the end of the input
					eosAfterFrameNr = frameCountInp;
					return;
				}
			} else {
				if (eosAfterFrameNr < 0L) {
					logDebug(FNC_NAME, "EOS reached");
				}
				eosAfterFrameNr = frameCountInp;
				return;
			}
		}
		// get the next frame from the input, as well as its size
		BufferExt tmpFrameBuf = new BufferExt();
		try {
			mediaOutgoingStream.getNextFrame(tmpFrameBuf);
			if (tmpFrameBuf.getUsed() < mediaOutgoingStream.getMagicBytesLengthBits() / 8) {
				// we have reached the end of the input
				throw new InputStreamEosException();
			}
			bufferQueue.add(tmpFrameBuf);
		} catch (InputStreamIoException | InputStreamEosException e) {
			if (doStop.get()) {
				return;
			}
			if (e instanceof InputStreamIoException) {
				logError(FNC_NAME, "InputStreamIoException caught while reading next frame: " + e.getMessage());
			}
			// we have reached the end of the input
			eosAfterFrameNr = frameCountInp;
			return;
		} catch (AvInvalidCodecDataException e) {
			logError(FNC_NAME, "AvInvalidCodecDataException caught: " + e.getMessage());
			// we have reached the end of the input
			eosAfterFrameNr = frameCountInp;
			return;
		}
		if (tmpFrameBuf.getUsed() == 0) {  // sanity check
			// we have reached the end of the input
			eosAfterFrameNr = frameCountInp;
			return;
		}

		//
		try {
			parseAndConvertData(tmpFrameBuf);
		} catch (Exception e) {
			logError(FNC_NAME, "caught: " + e);
			eosAfterFrameNr = frameCountInp;
			return;
		}
		debugStreamOffset += tmpFrameBuf.getUsed();

		//
		++frameCountInp;
	}

}

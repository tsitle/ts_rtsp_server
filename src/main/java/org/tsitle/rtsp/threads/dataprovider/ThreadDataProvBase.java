package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingBase;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvCannotOpenInputException;
import org.tsitle.rtsp.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp.exceptions.InputStreamEosException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.ThreadBase;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class ThreadDataProvBase<I extends CodecInfoInterface<I>> extends ThreadBase {

	private final Queue<@NonNull BufferExt> bufferQueue = new ConcurrentLinkedQueue<>();
	protected final Queue<@NonNull I> infoQueue = new ConcurrentLinkedQueue<>();

	private long frameCountInp = 0;
	private long frameNrOutp = 1;
	private long eosAfterFrameNr = -1;
	protected long debugStreamOffset = 0;

	protected AvStreamOutgoingBase mediaOutgoingStream;

	private final int queueSize;
	private final boolean doDebugRewindMediaFiles;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	protected ThreadDataProvBase(
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

		//
		this.mediaOutgoingStream = null;  // needs to be set by the child class
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
		} catch (Exception e) {
			logError(FNC_NAME, "Exception: " + e.getMessage());
		} finally {
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public synchronized boolean haveEos() {
		return (eosAfterFrameNr >= 0L && frameNrOutp > eosAfterFrameNr);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public synchronized boolean haveFullInputQueue() {
		return ((! bufferQueue.isEmpty() && haveEos()) || (bufferQueue.size() >= queueSize));
	}

	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull I infoObj) throws InputStreamEosException {
		if (haveEos() || bufferQueue.isEmpty() || infoQueue.isEmpty()) {
			throw new InputStreamEosException();
		}
		buf.copyOf(bufferQueue.poll());
		//noinspection DataFlowIssue
		infoObj.copyOf(infoQueue.poll());
		++frameNrOutp;
	}

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

	// -----------------------------------------------------------------------------------------------------------------

	protected abstract void parseAndConvertData(@NonNull BufferExt inputBuf) throws Exception;

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

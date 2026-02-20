package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.CodecInfoInterface;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingBase;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
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
	private long eofAfterFrameNr = -1;
	protected long debugStreamOffset = 0;

	protected AvStreamOutgoingBase mediaOutgoingStream;

	private final int queueSize;
	private final boolean doDebugRewindMediaFiles;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOF is reached
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
			while (! doStop.get() && eofAfterFrameNr < 0L) {
				mainLoop();
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

	public synchronized boolean haveEof() {
		return (eofAfterFrameNr >= 0L && frameNrOutp > eofAfterFrameNr);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public synchronized boolean haveFullInputQueue() {
		return ((! bufferQueue.isEmpty() && haveEof()) || (bufferQueue.size() >= queueSize));
	}

	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull I infoObj) throws InputStreamEofException {
		if (haveEof() || bufferQueue.isEmpty() || infoQueue.isEmpty()) {
			throw new InputStreamEofException();
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
		if (bufferQueue.size() < queueSize) {
			acquireData();
		} else {
			Thread.sleep(1);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void acquireData() {
		final String FNC_NAME = getClass().getSimpleName() + ".acquireData()";

		if (! mediaOutgoingStream.hasMoreFrames()) {
			if (doDebugRewindMediaFiles) {
				logDebug(FNC_NAME, "haveEof, rewinding");
				mediaOutgoingStream.rewind();
			} else {
				eofAfterFrameNr = frameCountInp;
				return;
			}
		}
		// get the next frame to send from the video, as well as its size
		BufferExt tmpFrameBuf = new BufferExt();
		try {
			mediaOutgoingStream.getNextFrame(tmpFrameBuf);
			if (tmpFrameBuf.getUsed() < mediaOutgoingStream.getMagicBytesLengthBits() / 8) {
				// we have reached the end of the video file
				throw new InputStreamEofException();
			}
			bufferQueue.add(tmpFrameBuf);
		} catch (InputStreamIoException | InputStreamEofException e) {
			if (e instanceof InputStreamIoException) {
				logError(FNC_NAME, "InputStreamIoException caught: " + e.getMessage());
			}
			// we have reached the end of the video file
			eofAfterFrameNr = frameCountInp;
			return;
		}
		if (tmpFrameBuf.getUsed() == 0) {  // sanity check
			// we have reached the end of the video file
			eofAfterFrameNr = frameCountInp;
			return;
		}

		//
		try {
			parseAndConvertData(tmpFrameBuf);
		} catch (Exception e) {
			logError(FNC_NAME, "caught: " + e);
			eofAfterFrameNr = frameCountInp;
			return;
		}
		debugStreamOffset += tmpFrameBuf.getUsed();

		//
		++frameCountInp;
	}

}

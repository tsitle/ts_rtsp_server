package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.*;
import org.tsitle.rtsp.avinputstreams.VideoStreamH265;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidH265DataException;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.ThreadBase;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ThreadDataProvH265 extends ThreadBase {

	private final Queue<@NonNull BufferExt> bufferQueue = new ConcurrentLinkedQueue<>();
	private final Queue<@NonNull H265Info> h265InfoQueue = new ConcurrentLinkedQueue<>();

	private long frameCountInp = 0;
	private long frameNrOutp = 1;
	private long eofAfterFrameNr = -1;
	private long debugStreamOffset = 0;

	/** VideoStream object used to access video frames */
	private final VideoStreamH265 videoStream;
	private final H265Parser h265Parser;

	private final int queueSize;
	private final boolean doDebugRewindMediaFiles;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOF is reached
	 */
	public ThreadDataProvH265(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
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
		try {
			this.videoStream = new VideoStreamH265(paramsVideoCommon.getVideoFilePath().orElseThrow());
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		this.h265Parser = new H265Parser();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		isRunning.set(true);

		//
		try {
			while (! doStop.get()) {
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
		return ((eofAfterFrameNr >= 0L && frameNrOutp > eofAfterFrameNr) || bufferQueue.isEmpty());
	}

	public synchronized boolean haveFullInputQueue() {
		return (haveEof() || (bufferQueue.size() >= queueSize));
	}

	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull H265Info h265Info) throws InputStreamEofException {
		if (haveEof() || bufferQueue.isEmpty() || h265InfoQueue.isEmpty()) {
			throw new InputStreamEofException();
		}
		buf.copyOf(bufferQueue.poll());
		//noinspection DataFlowIssue
		h265Info.copyOf(h265InfoQueue.poll());
		++frameNrOutp;
	}

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	public synchronized void notifyCongestionLevelChange(@SuppressWarnings("unused") int congestionLevel) {
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void stopThreadHook() {
		// nothing to do
	}

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

		if (! videoStream.hasMoreFrames() && doDebugRewindMediaFiles) {
			logDebug(FNC_NAME, "haveEof, rewinding");
			videoStream.rewind();
		}
		// get the next frame to send from the video, as well as its size
		BufferExt tmpFrameBuf = new BufferExt();
		try {
			videoStream.getNextFrame(tmpFrameBuf);
			if (tmpFrameBuf.getUsed() < videoStream.getMagicBytesLength() + H265Parser.NAL_UNIT_HEADER_SIZE) {
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
			parseAndConvertH265Data(tmpFrameBuf);
		} catch (AvInvalidH265DataException e) {
			logError(FNC_NAME, "AvInvalidH265DataException caught: " + e.getMessage());
			eofAfterFrameNr = frameCountInp;
			return;
		}
		debugStreamOffset += tmpFrameBuf.getUsed();

		//
		++frameCountInp;
	}

	private void parseAndConvertH265Data(BufferExt inputBuf) throws AvInvalidH265DataException {
		H265Info curFrameH265Info = h265Parser.parseH265Data(
				debugStreamOffset,
				videoStream.getMagicBytesLength(),
				inputBuf
			);

		h265InfoQueue.add(curFrameH265Info);
	}

}

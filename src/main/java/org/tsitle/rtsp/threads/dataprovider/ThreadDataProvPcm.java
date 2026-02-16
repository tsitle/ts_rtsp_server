package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.PcmInfo;
import org.tsitle.rtsp.avdata.PcmParser;
import org.tsitle.rtsp.avinputstreams.AudioStreamPcm;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidPcmDataException;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.ThreadBase;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderPcm;

import java.io.FileNotFoundException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ThreadDataProvPcm extends ThreadBase {

	private final Queue<@NonNull BufferExt> bufferQueue = new ConcurrentLinkedQueue<>();
	private final Queue<@NonNull PcmInfo> pcmInfoQueue = new ConcurrentLinkedQueue<>();

	private long frameCountInp = 0;
	private long frameNrOutp = 1;
	private long eofAfterFrameNr = -1;

	/** AudioStream object used to access audio samples */
	private final AudioStreamPcm audioStream;
	private final PcmParser pcmParser;

	private final int queueSize;
	private final boolean doDebugRewindMediaFiles;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOF is reached
	 */
	public ThreadDataProvPcm(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm,
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
			this.audioStream = new AudioStreamPcm(
					paramsAudioCommon.getAudioFilePath().orElseThrow(),
					paramsPcm.getAudioChannelCount(),
					paramsPcm.getAudioBitsPerSample(),
					paramsPcm.getRtpAudioSpf(),
					paramsPcm.getIsAudioInputBigEndian()
				);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		this.pcmParser = new PcmParser(
				paramsPcm.getAudioChannelCount(),
				paramsPcm.getAudioBitsPerSample()
			);
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

	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull PcmInfo pcmInfo) throws InputStreamEofException {
		if (haveEof() || bufferQueue.isEmpty() || pcmInfoQueue.isEmpty()) {
			throw new InputStreamEofException();
		}
		buf.copyOf(bufferQueue.poll());
		//noinspection DataFlowIssue
		pcmInfo.copyOf(pcmInfoQueue.poll());
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

		if (! audioStream.hasMoreFrames() && doDebugRewindMediaFiles) {
			logDebug(FNC_NAME, "haveEof, rewinding");
			audioStream.rewind();
		}
		// get the next frame to send from the video, as well as its size
		BufferExt tmpFrameBuf = new BufferExt();
		try {
			audioStream.getNextFrame(tmpFrameBuf);
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
			parseAndConvertPcmData(tmpFrameBuf);
		} catch (AvInvalidPcmDataException e) {
			logError(FNC_NAME, "AvInvalidPcmDataException caught: " + e.getMessage());
			eofAfterFrameNr = frameCountInp;
			return;
		}

		//
		++frameCountInp;
	}

	private void parseAndConvertPcmData(BufferExt inputBuf) throws AvInvalidPcmDataException {
		PcmInfo curFramePcmInfo = pcmParser.parsePcmData(inputBuf);

		pcmInfoQueue.add(curFramePcmInfo);
	}

}

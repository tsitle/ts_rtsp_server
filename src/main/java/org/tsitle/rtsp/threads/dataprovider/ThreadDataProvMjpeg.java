package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.ImageReencoder;
import org.tsitle.rtsp.avdata.JpegInfo;
import org.tsitle.rtsp.avdata.JpegParser;
import org.tsitle.rtsp.avinputstreams.VideoStreamMjpeg;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidJpegDataException;
import org.tsitle.rtsp.exceptions.ImageReencoderIoException;
import org.tsitle.rtsp.exceptions.InputStreamEofException;
import org.tsitle.rtsp.exceptions.InputStreamIoException;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadMjpeg;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.ThreadBase;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ThreadDataProvMjpeg extends ThreadBase {

	private final BufferExt cacheTempBuffer = new BufferExt();
	private final Queue<@NonNull BufferExt> bufferQueue = new ConcurrentLinkedQueue<>();
	private final Queue<@NonNull JpegInfo> jpegInfoQueue = new ConcurrentLinkedQueue<>();

	private long frameCountInp = 0;
	private long frameNrOutp = 1;
	private long eofAfterFrameNr = -1;
	private long debugStreamOffset = 0;

	/** VideoStream object used to access video frames */
	private final VideoStreamMjpeg videoStream;
	private final ImageReencoder imageReencoder;
	private final JpegParser jpegParser;

	private final int queueSize;
	private final boolean doDebugRewindMediaFiles;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOF is reached
	 */
	public ThreadDataProvMjpeg(
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
			this.videoStream = new VideoStreamMjpeg(paramsVideoCommon.getVideoFilePath().orElseThrow());
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		this.imageReencoder = new ImageReencoder();
		this.jpegParser = new JpegParser(
				logMsgInterface,
				Thread.currentThread().getName()
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

	public synchronized void getNextFrame(@NonNull BufferExt buf, @NonNull JpegInfo jpegInfo) throws InputStreamEofException {
		if (haveEof() || bufferQueue.isEmpty() || jpegInfoQueue.isEmpty()) {
			throw new InputStreamEofException();
		}
		buf.copyOf(bufferQueue.poll());
		//noinspection DataFlowIssue
		jpegInfo.copyOf(jpegInfoQueue.poll());
		++frameNrOutp;
	}

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	public synchronized void notifyCongestionLevelChange(int congestionLevel) {
		if (congestionLevel < 0 || congestionLevel > 4) {
			throw new IllegalArgumentException("congestionLevel must be in range 0..4");
		}
		/*
		 * CL 0 --> CQ 100%
		 * CL 1 --> CQ  85%
		 * CL 2 --> CQ  70%
		 * CL 3 --> CQ  55%
		 * CL 4 --> CQ  40%
		 */
		imageReencoder.setCompressionQuality(1.0f - (0.15f * (float)congestionLevel));
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
			parseAndConvertJpegData(tmpFrameBuf);
		} catch (ImageReencoderIoException e) {
			logError(FNC_NAME, "ImageReencoderIoException caught: " + e.getMessage());
			eofAfterFrameNr = frameCountInp;
			return;
		} catch (AvInvalidJpegDataException e) {
			logError(FNC_NAME, "AvInvalidJpegDataException caught: " + e.getMessage());
			eofAfterFrameNr = frameCountInp;
			return;
		}
		debugStreamOffset += tmpFrameBuf.getUsed();

		//
		++frameCountInp;
	}

	private void parseAndConvertJpegData(BufferExt inputBuf) throws AvInvalidJpegDataException, ImageReencoderIoException {
		JpegInfo curFrameJpegInfo = jpegParser.parseJpegData(debugStreamOffset, inputBuf);

		// re-encode or scale the image if necessary
		if ((curFrameJpegInfo.sof0_channelEncoding != JpegInfo.ChannelEncoding.YCBCR420 &&
					curFrameJpegInfo.sof0_channelEncoding != JpegInfo.ChannelEncoding.YCBCR422) ||
				curFrameJpegInfo.sof0_quantTableSelY == curFrameJpegInfo.sof0_quantTableSelCb ||
				curFrameJpegInfo.sof0_imgWidth > RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT ||
				curFrameJpegInfo.sof0_imgHeight > RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT) {
			cacheTempBuffer.copyOf(inputBuf);
			/*
			 * To provide a compatible JPEG image, we need to re-encode the image.
			 */
			if (curFrameJpegInfo.sof0_imgWidth > RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT ||
					curFrameJpegInfo.sof0_imgHeight > RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT) {
				imageReencoder.scaleImage(
						cacheTempBuffer,
						RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT,
						inputBuf
					);
			} else {
				imageReencoder.reencodeImage(cacheTempBuffer, inputBuf);
			}
			/*if (frameCountInp == 0) {
				writeJpegToFile(curImageDataPtr, "reenc", (int)(frameCountInp + 1));
			}*/

			//
			curFrameJpegInfo = jpegParser.parseJpegData(debugStreamOffset, inputBuf);
		}

		jpegInfoQueue.add(curFrameJpegInfo);
	}

	/**
	 * For debugging purposes only.
	 */
	@SuppressWarnings({"unused", "SameParameterValue"})
	private void writeJpegToFile(BufferExt data, String baseFilename, int frameNr) {
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(String.format("%s_%06d.jpg", baseFilename, frameNr))) {
			fos.write(data.getBuf(), 0, data.getUsed());
		} catch (IOException ex) {
			logError("writeJpegToFile()", "IOException caught: " + ex);
		}
	}

}

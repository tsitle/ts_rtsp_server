package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.ImageReencoder;
import org.tsitle.rtsp.avdata.JpegInfo;
import org.tsitle.rtsp.avdata.JpegParser;
import org.tsitle.rtsp.avstreams.VideoStreamOutgoingMjpeg;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidJpegDataException;
import org.tsitle.rtsp.exceptions.ImageReencoderIoException;
import org.tsitle.rtsp.packets.rtp.RtpPacketMjpeg;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.io.FileNotFoundException;
import java.io.IOException;

public class ThreadDataProvMjpeg extends ThreadDataProvBase<JpegInfo> {

	private final BufferExt cacheTempBuffer = new BufferExt();

	private final ImageReencoder imageReencoder;
	private final JpegParser jpegParser;

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
		super(
				logMsgInterface,
				queueSize,
				debugRewindMediaFiles
			);

		//
		try {
			this.mediaOutgoingStream = new VideoStreamOutgoingMjpeg(paramsVideoCommon.getVideoFilePath().orElseThrow());
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

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	@Override
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
	protected void parseAndConvertData(BufferExt inputBuf) throws AvInvalidJpegDataException, ImageReencoderIoException {
		JpegInfo curFrameJpegInfo = jpegParser.parseJpegData(debugStreamOffset, inputBuf);

		// re-encode or scale the image if necessary
		if ((curFrameJpegInfo.sof0_channelEncoding != JpegInfo.ChannelEncoding.YCBCR420 &&
					curFrameJpegInfo.sof0_channelEncoding != JpegInfo.ChannelEncoding.YCBCR422) ||
				curFrameJpegInfo.sof0_quantTableSelY == curFrameJpegInfo.sof0_quantTableSelCb ||
				curFrameJpegInfo.sof0_imgWidth > RtpPacketMjpeg.IMAGE_MAX_WIDTH_HEIGHT ||
				curFrameJpegInfo.sof0_imgHeight > RtpPacketMjpeg.IMAGE_MAX_WIDTH_HEIGHT) {
			cacheTempBuffer.copyOf(inputBuf);
			/*
			 * To provide a compatible JPEG image, we need to re-encode the image.
			 */
			if (curFrameJpegInfo.sof0_imgWidth > RtpPacketMjpeg.IMAGE_MAX_WIDTH_HEIGHT ||
					curFrameJpegInfo.sof0_imgHeight > RtpPacketMjpeg.IMAGE_MAX_WIDTH_HEIGHT) {
				imageReencoder.scaleImage(
						cacheTempBuffer,
						RtpPacketMjpeg.IMAGE_MAX_WIDTH_HEIGHT,
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

		infoQueue.add(curFrameJpegInfo);
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

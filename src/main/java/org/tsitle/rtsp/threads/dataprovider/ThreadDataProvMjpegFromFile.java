package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.ImageReencoder;
import org.tsitle.lib_xrtxp.avdata.VideoJpegInfo;
import org.tsitle.lib_xrtxp.avdata.VideoJpegParser;
import org.tsitle.rtsp.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp.avstreams.VideoStreamOutgoingMjpegFromFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.avdata.exceptions.ImageReencoderIoException;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketMjpeg;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.io.IOException;

public class ThreadDataProvMjpegFromFile extends ThreadDataProvFromFileBase<VideoJpegInfo> {

	private final BufferExt cacheTempBuffer = new BufferExt();

	private final ImageReencoder imageReencoder;
	private final VideoJpegParser jpegParser;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param avStreamIncoming Incoming A/V stream
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvMjpegFromFile(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull AvStreamIncomingFromFile avStreamIncoming,
				int queueSize,
				boolean debugRewindMediaFiles
			) {
		super(
				logMsgInterface,
				queueSize,
				debugRewindMediaFiles
			);

		//
		paramsVideoCommon.validate();

		//
		this.mediaOutgoingStream = new VideoStreamOutgoingMjpegFromFile(logMsgInterface, avStreamIncoming);
		this.imageReencoder = new ImageReencoder();
		this.jpegParser = new VideoJpegParser(
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
	protected VideoJpegInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		VideoJpegInfo curFrameJpegInfo = jpegParser.parseJpegData(debugStreamOffset, inputBuf);

		// re-encode or scale the image if necessary
		if ((curFrameJpegInfo.sof0_channelEncoding != VideoJpegInfo.ChannelEncoding.YCBCR420 &&
					curFrameJpegInfo.sof0_channelEncoding != VideoJpegInfo.ChannelEncoding.YCBCR422) ||
				curFrameJpegInfo.sof0_quantTableSelY == curFrameJpegInfo.sof0_quantTableSelCb ||
				curFrameJpegInfo.sof0_imgWidth > RtpPacketMjpeg.IMAGE_MAX_WIDTH_HEIGHT ||
				curFrameJpegInfo.sof0_imgHeight > RtpPacketMjpeg.IMAGE_MAX_WIDTH_HEIGHT) {
			cacheTempBuffer.copyOf(inputBuf);
			/*
			 * To provide a compatible JPEG image, we need to re-encode the image.
			 */
			try {
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
			} catch (ImageReencoderIoException e) {
				throw new AvInvalidCodecDataException("ImageReencoderIoException caught: " + e.getMessage());
			}
			/*if (frameCountInp == 0) {
				writeJpegToFile(curImageDataPtr, "reenc", (int)(frameCountInp + 1));
			}*/

			//
			curFrameJpegInfo = jpegParser.parseJpegData(debugStreamOffset, inputBuf);
		}

		return curFrameJpegInfo;
	}

	/**
	 * For debugging purposes only.
	 */
	@SuppressWarnings({"unused", "SameParameterValue"})
	private void writeJpegToFile(@NonNull BufferExt data, @NonNull String baseFilename, int frameNr) {
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(String.format("%s_%06d.jpg", baseFilename, frameNr))) {
			fos.write(data.getBaPtr(), 0, data.getUsed());
		} catch (IOException e) {
			logError("writeJpegToFile()", "IOException caught: " + e.getMessage());
		}
	}

}

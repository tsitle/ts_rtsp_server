package org.tsitle.rtsp.threads.rtp.codec_mjpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderMjpeg;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;
import org.tsitle.rtsp.avdata.ImageReencoder;
import org.tsitle.rtsp.avdata.JpegInfo;
import org.tsitle.rtsp.avdata.JpegParser;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadInterface;
import org.tsitle.rtsp.packets.rtp.RtpPacketPayloadMjpeg;
import org.tsitle.rtsp.avinputstreams.VideoStreamMjpeg;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

public final class ThreadRtpSenderMjpeg extends ThreadRtpSenderBase {

	/** VideoStream object used to access video frames */
	private final VideoStreamMjpeg videoStream;
	private final ImageReencoder imageReencoder;

	private final JpegParser jpegParser;
	private JpegInfo curFrameJpegInfo = null;
	/** Buffer used to store some image data */
	private final BufferExt cacheImageBuf = new BufferExt();
	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgVideoFrameBuf = new BufferExt();

	/**
	 * Constructor.
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsMjpeg Thread-specific parameters
	 * @throws FileNotFoundException If the video file cannot be opened
	 */
	public ThreadRtpSenderMjpeg(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderMjpeg paramsMjpeg
			) throws FileNotFoundException {
		super(
				paramsCommon,
				RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_JPEG),
				(long)((float)RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_JPEG) /
						Objects.requireNonNull(paramsCommon).getAvFramesPerSecond()),
				RtpPacketType.V_JPEG
			);

		//
		paramsVideoCommon.validate();
		paramsMjpeg.validate();
		//
		this.videoStream = new VideoStreamMjpeg(paramsVideoCommon.getVideoFilePath().orElseThrow());
		this.imageReencoder = new ImageReencoder();

		this.jpegParser = new JpegParser(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				Thread.currentThread().getName()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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
	protected FrameData cbFrameDataSupplier() {
		final String FNC_NAME = getClass().getSimpleName() + ".cbFrameDataSupplier()";

		cacheFrameData.reset();

		//
		cacheFrameData.rtpFrameTimestamp = getRtpTimestampAsInt();

		try {
			if (! videoStream.hasMoreFrames() && paramsCommon.getDebugRewindMediaFiles()) {
				logDebug(FNC_NAME, "haveEof, rewinding");
				videoStream.rewind();
			}
			// get the next frame to send from the video, as well as its size
			videoStream.getNextFrame(cacheOrgVideoFrameBuf);
			if (cacheOrgVideoFrameBuf.getUsed() == 0) {
				// we have reached the end of the video file
				throw new InputStreamEofException();
			}

			//
			cacheFrameData.totalFrameSize = cacheOrgVideoFrameBuf.getUsed();

			//
			curFrameJpegInfo = jpegParser.parseJpegData(debugStreamOffset, cacheOrgVideoFrameBuf);

			//
			BufferExt tmpImageDataPtr;

			//
			if ((curFrameJpegInfo.sof0_channelEncoding != JpegInfo.ChannelEncoding.YCBCR420 &&
						curFrameJpegInfo.sof0_channelEncoding != JpegInfo.ChannelEncoding.YCBCR422) ||
					curFrameJpegInfo.sof0_quantTableSelY == curFrameJpegInfo.sof0_quantTableSelCb ||
					curFrameJpegInfo.sof0_imgWidth > RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT ||
					curFrameJpegInfo.sof0_imgHeight > RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT) {
				/*
				 * To provide a compatible JPEG image, we need to re-encode the image.
				 */
				if (curFrameJpegInfo.sof0_imgWidth > RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT ||
						curFrameJpegInfo.sof0_imgHeight > RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT) {
					imageReencoder.scaleImage(
							cacheOrgVideoFrameBuf,
							RtpPacketPayloadMjpeg.IMAGE_MAX_WIDTH_HEIGHT,
							cacheImageBuf
						);
				} else {
					imageReencoder.reencodeImage(cacheOrgVideoFrameBuf, cacheImageBuf);
				}
				/*if (rtpRealFrameNr.get() == 0) {
					writeJpegToFile(cacheImageBuf, "reenc", (int) rtpRealFrameNr.get());
				}*/

				//
				curFrameJpegInfo = jpegParser.parseJpegData(debugStreamOffset, cacheImageBuf);

				//
				tmpImageDataPtr = cacheImageBuf;
			} else {
				tmpImageDataPtr = cacheOrgVideoFrameBuf;
			}

			// extract the actual RTP/JPEG payload
			cacheFrameData.rtpPayloadData.copyOf(
					tmpImageDataPtr,
					curFrameJpegInfo.sos_scanDataOffs,
					curFrameJpegInfo.sos_scanDataLength
			);
		} catch (ImageReencoderIoException | AvInvalidJpegDataException | InputStreamIoException ex) {
			cacheFrameData.haveErrorOther = true;
			cacheFrameData.errorMsg = FNC_NAME + ": " + ex;
		} catch (InputStreamEofException ex) {
			cacheFrameData.haveErrorEof = true;
			cacheFrameData.errorMsg = FNC_NAME + ": InputStreamEofException caught";
		}

		// update frame number
		incrRtpAndNtpTsFrameNr();

		return cacheFrameData;
	}

	@Override
	protected Boolean cbRtpPacketMarkerBitSupplier(int currentOffsetInFramePlusFragmentSize, int framePayloadSize) {
		return (currentOffsetInFramePlusFragmentSize == framePayloadSize);
	}

	@Override
	protected RtpPacketPayloadInterface cbRtpPacketPayloadSupplier(FrameFragmentData curFragmentData) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbRtpPacketPayloadSupplier()";

		if (curFrameJpegInfo == null) {
			throw new IllegalStateException(FNC_NAME + ": curFrameJpegInfo == null");
		}
		cacheRtpInnerPayloadBuf.copyOf(
				curFragmentData.frameData().rtpPayloadData,
				curFragmentData.fragmentOffset(),
				curFragmentData.fragmentSize()
			);

		return new RtpPacketPayloadMjpeg(
				curFragmentData.fragmentOffset(),
				curFrameJpegInfo,
				cacheRtpInnerPayloadBuf
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

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

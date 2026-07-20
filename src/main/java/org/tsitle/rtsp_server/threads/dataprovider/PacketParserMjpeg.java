package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.ImageReencoder;
import org.tsitle.lib_xrtxp.avdata.VideoJpegInfo;
import org.tsitle.lib_xrtxp.avdata.VideoJpegParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.avdata.exceptions.ImageReencoderIoException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketMjpeg;

import java.io.IOException;

final class PacketParserMjpeg {

	private final @NonNull VideoJpegParser pktParser;
	private final @NonNull ImageReencoder imageReencoder;
	private final BufferExt cacheTempBuffer = new BufferExt();

	PacketParserMjpeg(@NonNull LogMsgInterface logMsgInterface) {
		this.pktParser = new VideoJpegParser(
				logMsgInterface,
				Thread.currentThread().getName()
			);
		this.imageReencoder = new ImageReencoder();
	}

	void setCompressionQuality(float quality) {
		imageReencoder.setCompressionQuality(quality);
	}

	@NonNull VideoJpegInfo parseAndConvertData(long debugStreamOffset, @NonNull BufferExt inputBuf)
			throws AvInvalidCodecDataException {
		VideoJpegInfo curFrameJpegInfo = pktParser.parseJpegData(debugStreamOffset, inputBuf);

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
			curFrameJpegInfo = pktParser.parseJpegData(debugStreamOffset, inputBuf);
		}

		return curFrameJpegInfo;
	}

	/**
	 * For debugging purposes only.
	 */
	@SuppressWarnings({"unused", "SameParameterValue"})
	private static void writeJpegToFile(@NonNull BufferExt data, @NonNull String baseFilename, int frameNr) {
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(String.format("%s_%06d.jpg", baseFilename, frameNr))) {
			fos.write(data.getBaPtr(), 0, data.getUsed());
		} catch (IOException e) {
			System.err.println(PacketParserMjpeg.class.getSimpleName() + ".writeJpegToFile(): IOException caught: " + e.getMessage());
		}
	}

}

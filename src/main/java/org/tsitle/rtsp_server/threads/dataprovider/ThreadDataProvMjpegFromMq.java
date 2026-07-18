package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.VideoJpegInfo;
import org.tsitle.lib_xrtxp.avdata.VideoJpegParser;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoMjpegFromMq;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

public final class ThreadDataProvMjpegFromMq extends ThreadDataProvFromMqBase<VideoJpegInfo> {

	private @Nullable VideoJpegParser jpegParser = null;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvMjpegFromMq(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon
			) {
		super(paramsCommon, true);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void createFrameGrabber() {
		if (avStreamIncoming == null) {
			throw new IllegalStateException("avStreamIncoming is null");
		}
		this.frameGrabber = new FrameGrabberVideoMjpegFromMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);
		this.jpegParser = new VideoJpegParser(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				Thread.currentThread().getName()
			);
	}

	@Override
	protected @NonNull VideoJpegInfo parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		if (jpegParser == null) {
			throw new IllegalStateException("jpegParser is null");
		}
		VideoJpegInfo curFrameInfo = jpegParser.parseJpegData(debugStreamOffset, inputBuf);
		//
		haveAllRequiredMetadataPackets = true;
		//
		return curFrameInfo;
	}

}

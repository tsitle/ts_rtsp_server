package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.VideoH264Info;
import org.tsitle.lib_xrtxp.avdata.VideoH264Parser;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PictureBoundaryInfo;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PpsContext;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264SpsContext;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.util.HashMap;
import java.util.Map;

public final class ThreadDataProvH264FromMq extends ThreadDataProvFromMqBase<VideoH264Info> {

	private @Nullable VideoH264Parser h264Parser = null;

	private @Nullable H264PictureBoundaryInfo cachePictBoundInfoPrev = null;

	/**
	 * Constructor.
	 * @param paramsCommon Common parameters for RTP sender threads
	 */
	public ThreadDataProvH264FromMq(
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
		this.frameGrabber = new FrameGrabberVideoH26xFromMq(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				avStreamIncoming
			);

		Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext = new HashMap<>();
		Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext = new HashMap<>();
		this.h264Parser = new VideoH264Parser(mapSpsContext, mapPpsContext);
	}

	@Override
	protected @NonNull VideoH264Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		if (h264Parser == null) {
			throw new IllegalStateException("h264Parser is null");
		}
		if (magicBytesLength == -1) {
			// @TODO fix this
			magicBytesLength = findH26xMagicBytesLength(inputBuf);
			magicBytesArrPtr = (magicBytesLength == 3 ?
					FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_3 :
					FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4);
		}
		//
		VideoH264Info curFrameH264Info = h264Parser.parseH264Data(
				debugStreamOffset,
				magicBytesLength,
				inputBuf,
				cachePictBoundInfoPrev
			);
		if (curFrameH264Info.isVclNalUnit) {
			cachePictBoundInfoPrev = curFrameH264Info.pictBoundInfo.clone();
		}
		haveAllRequiredMetadataPackets = h264Parser.haveAllRequiredMetadataPackets();
		//
		return curFrameH264Info;
	}

	@Override
	protected int findNextMagicBytes(final @NonNull BufferExt inputBuf) {
		return findH26xNextNalUnit(inputBuf);
	}

}

package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.VideoH264Info;
import org.tsitle.lib_xrtxp.avdata.VideoH264Parser;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PictureBoundaryInfo;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PpsContext;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264SpsContext;
import org.tsitle.rtsp.avstreams.AvStreamIncomingFromMq;
import org.tsitle.rtsp.avstreams.VideoStreamOutgoingH26xFromFile;
import org.tsitle.rtsp.avstreams.VideoStreamOutgoingH26xFromMq;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.util.HashMap;
import java.util.Map;

public class ThreadDataProvH264FromMq extends ThreadDataProvFromMqBase<VideoH264Info> {

	private final VideoH264Parser h264Parser;

	private @Nullable H264PictureBoundaryInfo cachePictBoundInfoPrev = null;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param avStreamIncoming Incoming A/V stream
	 */
	public ThreadDataProvH264FromMq(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull AvStreamIncomingFromMq avStreamIncoming
			) {
		super(logMsgInterface, true);

		//
		paramsVideoCommon.validate();

		//
		this.mediaOutgoingStream = new VideoStreamOutgoingH26xFromMq(logMsgInterface, avStreamIncoming);
		Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext = new HashMap<>();
		Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext = new HashMap<>();
		this.h264Parser = new VideoH264Parser(
				mapSpsContext,
				mapPpsContext
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull VideoH264Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		if (magicBytesLength == -1) {
			magicBytesLength = findH26xMagicBytesLength(inputBuf);
			magicBytesArrPtr = (magicBytesLength == 3 ?
					VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_3 :
					VideoStreamOutgoingH26xFromFile.H26X_FRAME_START_MAGICBYTES_4);
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
	protected int findNextMagicBytes(final BufferExt inputBuf) {
		return findH26xNextNalUnit(inputBuf);
	}

}

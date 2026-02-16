package org.tsitle.rtsp.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.*;
import org.tsitle.rtsp.avinputstreams.VideoStreamH264;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.AvInvalidH264DataException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

public class ThreadDataProvH264 extends ThreadDataProvBase<H264Info> {

	private final H264Parser h264Parser;

	private @Nullable H264PictureBoundaryInfo cachePictBoundInfoPrev = null;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOF is reached
	 */
	public ThreadDataProvH264(
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
			this.mediaInputStream = new VideoStreamH264(paramsVideoCommon.getVideoFilePath().orElseThrow());
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext = new HashMap<>();
		Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext = new HashMap<>();
		this.h264Parser = new H264Parser(
				mapSpsContext,
				mapPpsContext
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	@Override
	public synchronized void notifyCongestionLevelChange(@SuppressWarnings("unused") int congestionLevel) {
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected void parseAndConvertData(BufferExt inputBuf) throws AvInvalidH264DataException {
		H264Info curFrameH264Info = h264Parser.parseH264Data(
				debugStreamOffset,
				mediaInputStream.getMagicBytesLength(),
				inputBuf,
				cachePictBoundInfoPrev
			);
		if (curFrameH264Info.isVclNalUnit) {
			cachePictBoundInfoPrev = curFrameH264Info.pictBoundInfo.clone();
		}

		infoQueue.add(curFrameH264Info);
	}

}

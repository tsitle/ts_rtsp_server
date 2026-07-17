package org.tsitle.rtsp_server.threads.dataprovider;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.VideoH264Info;
import org.tsitle.lib_xrtxp.avdata.VideoH264Parser;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PictureBoundaryInfo;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264PpsContext;
import org.tsitle.lib_xrtxp.avdata.subinfo.H264SpsContext;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberVideoH26xFromFile;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.util.HashMap;
import java.util.Map;

public final class ThreadDataProvH264FromFile extends ThreadDataProvFromFileBase<VideoH264Info> {

	private final @NonNull VideoH264Parser h264Parser;

	private @Nullable H264PictureBoundaryInfo cachePictBoundInfoPrev = null;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param avStreamIncoming Incoming A/V stream
	 * @param queueSize Size of the input queue
	 * @param debugRewindMediaFiles If true, the media file will be rewound after EOS is reached
	 */
	public ThreadDataProvH264FromFile(
				@NonNull LogMsgInterface logMsgInterface,
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
		this.frameGrabber = new FrameGrabberVideoH26xFromFile(logMsgInterface, avStreamIncoming);
		Map<@NonNull Integer, @NonNull H264SpsContext> mapSpsContext = new HashMap<>();
		Map<@NonNull Integer, @NonNull H264PpsContext> mapPpsContext = new HashMap<>();
		this.h264Parser = new VideoH264Parser(
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
	protected @NonNull VideoH264Info parseAndConvertData(@NonNull BufferExt inputBuf) throws AvInvalidCodecDataException {
		VideoH264Info curFrameH264Info = h264Parser.parseH264Data(
				debugStreamOffset,
				isMagicBytesLong(inputBuf) ?
						FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4.length
						: FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_3.length,
				inputBuf,
				cachePictBoundInfoPrev
			);
		if (curFrameH264Info.isVclNalUnit) {
			cachePictBoundInfoPrev = curFrameH264Info.pictBoundInfo.clone();
		}

		return curFrameH264Info;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static boolean isMagicBytesLong(@NonNull BufferExt inputBuf) {
		return (inputBuf.getUsed() >= FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4.length &&
				inputBuf.get(0) == FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4[0] &&
				inputBuf.get(1) == FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4[1] &&
				inputBuf.get(2) == FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4[2] &&
				inputBuf.get(3) == FrameGrabberVideoH26xFromFile.H26X_FRAME_START_MAGICBYTES_4[3]);
	}

}

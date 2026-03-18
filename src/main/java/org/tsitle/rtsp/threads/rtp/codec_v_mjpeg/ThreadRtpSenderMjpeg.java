package org.tsitle.rtsp.threads.rtp.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avstreams.*;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketMjpeg;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvMjpegFromFile;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderMjpeg;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;
import org.tsitle.rtsp.avdata.VideoJpegInfo;

import java.util.Objects;

public final class ThreadRtpSenderMjpeg<
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>
		> extends ThreadRtpSenderBase<VideoJpegInfo, AVSTRIC, AVSTROG, ThreadDataProvBase<VideoJpegInfo, AVSTROG>> {

	private final ParamsThreadRtpSenderVideoCommon paramsVideoCommon;

	private final VideoJpegInfo curFrameJpegInfo = new VideoJpegInfo();
	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgVideoFrameBuf = new BufferExt();
	private final BufferExt cacheBufForFD = new BufferExt();

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param avStreamOutgoingType Class of the AvStreamOutgoing object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsMjpeg Thread-specific parameters
	 */
	public ThreadRtpSenderMjpeg(
				Class<AVSTRIC> avStreamIncomingType,
				Class<AVSTROG> avStreamOutgoingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderMjpeg paramsMjpeg
			) {
		super(
				avStreamIncomingType,
				avStreamOutgoingType,
				paramsCommon,
				RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_JPEG),
				RtpPacketType.V_JPEG
			);

		//
		this.rtpTicksPerFrame = (long)((double)RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(RtpPacketType.V_JPEG) /
				Objects.requireNonNull(paramsCommon).getAvFramesPerSecond());

		//
		paramsVideoCommon.validate();
		this.paramsVideoCommon = paramsVideoCommon.clone();
		paramsMjpeg.validate();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receives a notification about the current congestion level.
	 * @param congestionLevel Congestion level (range 0..4)
	 */
	@Override
	public synchronized void notifyCongestionLevelChange(int congestionLevel) {
		if (threadDataProv != null) {
			threadDataProv.notifyCongestionLevelChange(congestionLevel);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull ThreadDataProvBase<VideoJpegInfo, AVSTROG> newThreadDataProv() {
		if (avStreamOutgoingType != VideoStreamOutgoingMjpegFromFile.class) {
			throw new RuntimeException("avStreamOutgoingType must be VideoStreamOutgoingMjpegFromFile");
		}
		ThreadDataProvMjpegFromFile resObj = new ThreadDataProvMjpegFromFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsVideoCommon,
				Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
				(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),
				paramsCommon.getDebugRewindMediaFiles()
			);
		@SuppressWarnings("unchecked")
		ThreadDataProvBase<VideoJpegInfo, AVSTROG> typedProvider = (ThreadDataProvBase<VideoJpegInfo, AVSTROG>)resObj;
		return typedProvider;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(
				cacheOrgVideoFrameBuf,
				cacheBufForFD,
				curFrameJpegInfo
			);
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		return isLastFragment;
	}

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		prepareRtpPacketDataForFragment(curFragmentData);
		return new RtpPacketMjpeg(
				cacheParamsBase,
				curFragmentData.fragmentOffset(),
				curFrameJpegInfo,
				cacheRtpInnerPayloadBuf
			);
	}

}

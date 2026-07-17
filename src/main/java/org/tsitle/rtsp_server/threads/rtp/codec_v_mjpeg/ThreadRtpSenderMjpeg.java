package org.tsitle.rtsp_server.threads.rtp.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketMjpeg;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvMjpegFromFile;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvMjpegFromMq;
import org.tsitle.rtsp_server.threads.rtp.*;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderMjpeg;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.lib_xrtxp.avdata.VideoJpegInfo;

import java.util.Objects;

public final class ThreadRtpSenderMjpeg<
			AVSTRIC extends AvStreamIncomingBase,
			FGAV extends FrameGrabberAvBase<AVSTRIC>
		> extends ThreadRtpSenderBase<VideoJpegInfo, AVSTRIC, FGAV, ThreadDataProvBase<VideoJpegInfo, FGAV>> {

	@SuppressWarnings("FieldCanBeLocal")
	private final ParamsThreadRtpSenderVideoCommon paramsVideoCommon;

	private final VideoJpegInfo curFrameJpegInfo = new VideoJpegInfo();
	private @Nullable RtpPacketMjpeg cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param frameGrabberAvType Class of the FrameGrabberAv object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsMjpeg Thread-specific parameters
	 */
	public ThreadRtpSenderMjpeg(
				Class<AVSTRIC> avStreamIncomingType,
				Class<FGAV> frameGrabberAvType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderMjpeg paramsMjpeg
			) {
		super(
				avStreamIncomingType,
				frameGrabberAvType,
				paramsCommon,
				RtpPacketType.V_MJPEG.getVideoCodecRtpClockrate(),
				RtpPacketType.V_MJPEG
			);

		//
		this.rtpTicksPerFrame = (long)((double)RtpPacketType.V_MJPEG.getVideoCodecRtpClockrate() /
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
	protected @NonNull ThreadDataProvBase<VideoJpegInfo, FGAV> newThreadDataProv() {
		if (frameGrabberAvType == FrameGrabberVideoMjpegFromFile.class) {
			ThreadDataProvMjpegFromFile resObj = new ThreadDataProvMjpegFromFile(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoJpegInfo, FGAV> typedProvider = (ThreadDataProvBase<VideoJpegInfo, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberVideoMjpegFromMq.class) {
			ThreadDataProvMjpegFromMq resObj = new ThreadDataProvMjpegFromMq(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					Objects.requireNonNull((AvStreamIncomingFromMq)avStreamIncomingObj)
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoJpegInfo, FGAV> typedProvider = (ThreadDataProvBase<VideoJpegInfo, FGAV>)resObj;
			return typedProvider;
		}
		// @TODO add FrameGrabberVideoMjpegFromDemuxMs
		throw new RuntimeException("frameGrabberAvType must be FrameGrabberVideoMjpegFromXxx");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameJpegInfo);
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		return isLastFragment;
	}

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		prepareRtpPacketDataForFragment(curFragmentData);
		if (cacheRtpInnerPayloadBufView == null) {
			throw new IllegalStateException("cacheRtpInnerPayloadBufView == null");
		}
		if (cachePlainPacket == null) {
			cachePlainPacket = new RtpPacketMjpeg(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFrameJpegInfo,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFrameJpegInfo,
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

}

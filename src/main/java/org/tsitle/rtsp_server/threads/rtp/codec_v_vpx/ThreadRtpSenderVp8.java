package org.tsitle.rtsp_server.threads.rtp.codec_v_vpx;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Info;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketVp8;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingBase;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvBase;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.rtsp_server.avstreams.codec_v_vpx.FrameGrabberVideoVp8FromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_v_vpx.FrameGrabberVideoVp8FromEsMq;
import org.tsitle.rtsp_server.threads.dataprovider_es.ThreadDataProvBase;
import org.tsitle.rtsp_server.threads.dataprovider_es.codec_v_vpx.ThreadDataProvVp8FromDemuxMs;
import org.tsitle.rtsp_server.threads.dataprovider_es.codec_v_vpx.ThreadDataProvVp8FromFile;
import org.tsitle.rtsp_server.threads.dataprovider_es.codec_v_vpx.ThreadDataProvVp8FromMq;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVp8;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

public final class ThreadRtpSenderVp8<
			AVSTRIC extends AvStreamIncomingBase,
			FGAV extends FrameGrabberAvBase<AVSTRIC>
		> extends ThreadRtpSenderBase<VideoVp8Info, AVSTRIC, FGAV, ThreadDataProvBase<VideoVp8Info, FGAV>> {

	private final VideoVp8Info curFrameVp8Info = new VideoVp8Info();
	private @Nullable RtpPacketVp8 cachePlainPacket = null;

	private double lastFps;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param frameGrabberAvType Class of the FrameGrabberAv object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsVp8 Thread-specific parameters
	 */
	public ThreadRtpSenderVp8(
				Class<AVSTRIC> avStreamIncomingType,
				Class<FGAV> frameGrabberAvType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderVp8 paramsVp8
			) {
		super(
				avStreamIncomingType,
				frameGrabberAvType,
				paramsCommon,
				RtpPacketType.V_VP8.getVideoCodecRtpClockrate(),
				RtpPacketType.V_VP8
			);

		//
		this.lastFps = paramsCommon.getAvFramesPerSecond();
		this.rtpTicksPerFrame = computeRtpTicksPerFrame(this.lastFps);

		//
		paramsVideoCommon.validate();
		paramsVp8.validate();
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
	protected @NonNull ThreadDataProvBase<VideoVp8Info, FGAV> newThreadDataProv() {
		if (frameGrabberAvType == FrameGrabberVideoVp8FromEsFile.class) {
			ThreadDataProvVp8FromFile resObj = new ThreadDataProvVp8FromFile(
					paramsCommon,
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoVp8Info, FGAV> typedProvider = (ThreadDataProvBase<VideoVp8Info, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberVideoVp8FromEsMq.class) {
			ThreadDataProvVp8FromMq resObj = new ThreadDataProvVp8FromMq(
					paramsCommon
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoVp8Info, FGAV> typedProvider = (ThreadDataProvBase<VideoVp8Info, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberAvFromDemuxMs.class) {
			ThreadDataProvVp8FromDemuxMs resObj = new ThreadDataProvVp8FromDemuxMs(
					paramsCommon
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoVp8Info, FGAV> typedProvider = (ThreadDataProvBase<VideoVp8Info, FGAV>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("invalid frameGrabberAvType");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameVp8Info);
	}

	@Override
	protected int cbFragmentSizeAdjust(int fragmentSize) {
		return defaultFragmentSizeAdjust(fragmentSize);
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
		//
		double curFps = (threadDataProv == null ? -1.0 : threadDataProv.getVideoFps().orElse(-1.0));
		if (curFps > 0.001 && Double.compare(lastFps, curFps) != 0) {
			nextRtpTicksPerFrame = computeRtpTicksPerFrame(curFps);
			lastFps = curFps;
		}
		//
		if (cachePlainPacket == null) {
			cachePlainPacket = new RtpPacketVp8(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFragmentData.isLastFragment(),
					curFrameVp8Info,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private long computeRtpTicksPerFrame(double fps) {
		return (long)((double)rtpPacketType.getVideoCodecRtpClockrate() / fps);
	}

}

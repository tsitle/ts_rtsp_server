package org.tsitle.rtsp_server.threads.rtp.codec_v_vpx;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.*;
import org.tsitle.lib_xrtxp.avdata.codec_v_vpx.VideoVp8Info;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketVp8;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.lib_dataprov.threads_es.codec_v_vpx.ThreadDataProvEsVp8FromDemuxMs;
import org.tsitle.lib_dataprov.threads_es.codec_v_vpx.ThreadDataProvEsVp8FromFile;
import org.tsitle.lib_dataprov.threads_es.codec_v_vpx.ThreadDataProvEsVp8FromMq;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpVp8;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpVideoCommon;

public final class ThreadRtpSenderVp8<AVSTRIC extends AvStreamIncomingBase>
		extends ThreadRtpSenderBase<VideoVp8Info, AVSTRIC, ThreadDataProvEsBase<VideoVp8Info, AVSTRIC>> {

	private final VideoVp8Info curFrameVp8Info = new VideoVp8Info();
	private @Nullable RtpPacketVp8 cachePlainPacket = null;

	private double lastFps;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsVp8 Thread-specific parameters
	 */
	public ThreadRtpSenderVp8(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadDpVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadDpVp8 paramsVp8
			) {
		super(
				avStreamIncomingType,
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
	protected @NonNull ThreadDataProvEsBase<VideoVp8Info, AVSTRIC> newThreadDataProv() {
		if (avStreamIncomingType == AvStreamIncomingFromEsFile.class) {
			ThreadDataProvEsVp8FromFile resObj = new ThreadDataProvEsVp8FromFile(
					paramsCommon.copyToThreadDpCommon(),
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvEsBase<VideoVp8Info, AVSTRIC> typedProvider = (ThreadDataProvEsBase<VideoVp8Info, AVSTRIC>)resObj;
			return typedProvider;
		}
		if (avStreamIncomingType == AvStreamIncomingFromEsMq.class) {
			ThreadDataProvEsVp8FromMq resObj = new ThreadDataProvEsVp8FromMq(
					paramsCommon.copyToThreadDpCommon()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvEsBase<VideoVp8Info, AVSTRIC> typedProvider = (ThreadDataProvEsBase<VideoVp8Info, AVSTRIC>)resObj;
			return typedProvider;
		}
		if (avStreamIncomingType == AvStreamIncomingFromDemuxMs.class) {
			ThreadDataProvEsVp8FromDemuxMs resObj = new ThreadDataProvEsVp8FromDemuxMs(
					paramsCommon.copyToThreadDpCommon()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvEsBase<VideoVp8Info, AVSTRIC> typedProvider = (ThreadDataProvEsBase<VideoVp8Info, AVSTRIC>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("invalid avStreamIncomingType");
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

package org.tsitle.rtsp_server.threads.rtp.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingBase;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvBase;
import org.tsitle.lib_dataprov.avstreams.FrameGrabberAvFromDemuxMs;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketMjpeg;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_dataprov.avstreams.codec_v_mjpeg.FrameGrabberVideoMjpegFromEsFile;
import org.tsitle.lib_dataprov.avstreams.codec_v_mjpeg.FrameGrabberVideoMjpegFromEsMq;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvBase;
import org.tsitle.lib_dataprov.threads_es.codec_v_mjpeg.ThreadDataProvMjpegFromDemuxMs;
import org.tsitle.lib_dataprov.threads_es.codec_v_mjpeg.ThreadDataProvMjpegFromFile;
import org.tsitle.lib_dataprov.threads_es.codec_v_mjpeg.ThreadDataProvMjpegFromMq;
import org.tsitle.rtsp_server.threads.rtp.*;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpMjpeg;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpVideoCommon;
import org.tsitle.lib_xrtxp.avdata.codec_v_mjpeg.VideoJpegInfo;

public final class ThreadRtpSenderMjpeg<
			AVSTRIC extends AvStreamIncomingBase,
			FGAV extends FrameGrabberAvBase<AVSTRIC>
		> extends ThreadRtpSenderBase<VideoJpegInfo, AVSTRIC, FGAV, ThreadDataProvBase<VideoJpegInfo, FGAV>> {

	private final VideoJpegInfo curFrameJpegInfo = new VideoJpegInfo();
	private @Nullable RtpPacketMjpeg cachePlainPacket = null;

	private double lastFps;

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
				@NonNull ParamsThreadDpVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadDpMjpeg paramsMjpeg
			) {
		super(
				avStreamIncomingType,
				frameGrabberAvType,
				paramsCommon,
				RtpPacketType.V_MJPEG.getVideoCodecRtpClockrate(),
				RtpPacketType.V_MJPEG
			);

		//
		this.lastFps = paramsCommon.getAvFramesPerSecond();
		this.rtpTicksPerFrame = computeRtpTicksPerFrame(this.lastFps);

		//
		paramsVideoCommon.validate();
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
		if (frameGrabberAvType == FrameGrabberVideoMjpegFromEsFile.class) {
			ThreadDataProvMjpegFromFile resObj = new ThreadDataProvMjpegFromFile(
					paramsCommon.copyToThreadDpCommon(),
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoJpegInfo, FGAV> typedProvider = (ThreadDataProvBase<VideoJpegInfo, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberVideoMjpegFromEsMq.class) {
			ThreadDataProvMjpegFromMq resObj = new ThreadDataProvMjpegFromMq(
					paramsCommon.copyToThreadDpCommon()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoJpegInfo, FGAV> typedProvider = (ThreadDataProvBase<VideoJpegInfo, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberAvFromDemuxMs.class) {
			ThreadDataProvMjpegFromDemuxMs resObj = new ThreadDataProvMjpegFromDemuxMs(
					paramsCommon.copyToThreadDpCommon()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoJpegInfo, FGAV> typedProvider = (ThreadDataProvBase<VideoJpegInfo, FGAV>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("invalid frameGrabberAvType");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameJpegInfo);
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

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private long computeRtpTicksPerFrame(double fps) {
		return (long)((double)rtpPacketType.getVideoCodecRtpClockrate() / fps);
	}

}

package org.tsitle.rtsp_server.threads.rtp.codec_v_mjpeg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketMjpeg;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.lib_dataprov.threads_es.codec_v_mjpeg.ThreadDataProvEsMjpegFromDmxFc;
import org.tsitle.lib_dataprov.threads_es.codec_v_mjpeg.ThreadDataProvEsMjpegFromRawFile;
import org.tsitle.lib_dataprov.threads_es.codec_v_mjpeg.ThreadDataProvEsMjpegFromMq;
import org.tsitle.rtsp_server.threads.rtp.*;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpMjpeg;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpVideoCommon;
import org.tsitle.lib_xrtxp.avdata.codec_v_mjpeg.VideoJpegInfo;

public final class ThreadRtpSenderMjpeg<AVSTRIC extends AvStreamIncomingBase>
		extends ThreadRtpSenderBase<VideoJpegInfo, AVSTRIC, ThreadDataProvEsBase<VideoJpegInfo>> {

	private final VideoJpegInfo curFrameJpegInfo = new VideoJpegInfo();
	private @Nullable RtpPacketMjpeg cachePlainPacket = null;

	private double lastFps;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsMjpeg Thread-specific parameters
	 */
	public ThreadRtpSenderMjpeg(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadDpVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadDpMjpeg paramsMjpeg
			) {
		super(
				avStreamIncomingType,
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
	protected @NonNull ThreadDataProvEsBase<VideoJpegInfo> newThreadDataProv() {
		if (avStreamIncomingType == AvStreamIncomingFromEsRawFile.class) {
			return new ThreadDataProvEsMjpegFromRawFile(
					paramsCommon.copyToThreadDpCommon(),
					10
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromEsMq.class) {
			return new ThreadDataProvEsMjpegFromMq(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromDmxFc.class) {
			return new ThreadDataProvEsMjpegFromDmxFc(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		throw new RuntimeException("invalid avStreamIncomingType");
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

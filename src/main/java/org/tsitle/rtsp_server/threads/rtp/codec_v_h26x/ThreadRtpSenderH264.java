package org.tsitle.rtsp_server.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.*;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.VideoH264Info;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketH264;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.lib_dataprov.threads_es.codec_v_h26x.ThreadDataProvEsH264FromDemuxMs;
import org.tsitle.lib_dataprov.threads_es.codec_v_h26x.ThreadDataProvEsH264FromFile;
import org.tsitle.lib_dataprov.threads_es.codec_v_h26x.ThreadDataProvEsH264FromMq;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpH264;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpVideoCommon;

public final class ThreadRtpSenderH264<AVSTRIC extends AvStreamIncomingBase>
		extends ThreadRtpSenderH26xBase<VideoH264Info, AVSTRIC, ThreadDataProvEsBase<VideoH264Info, AVSTRIC>> {

	private @Nullable RtpPacketH264 cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH264 Thread-specific parameters
	 */
	public ThreadRtpSenderH264(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadDpVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadDpH264 paramsH264
			) {
		super(
				avStreamIncomingType,
				paramsCommon,
				paramsVideoCommon,
				RtpPacketType.V_H264
			);

		//
		paramsH264.validate();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull ThreadDataProvEsBase<VideoH264Info, AVSTRIC> newThreadDataProv() {
		if (avStreamIncomingType == AvStreamIncomingFromEsFile.class) {
			ThreadDataProvEsH264FromFile resObj = new ThreadDataProvEsH264FromFile(
					paramsCommon.copyToThreadDpCommon(),
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvEsBase<VideoH264Info, AVSTRIC> typedProvider = (ThreadDataProvEsBase<VideoH264Info, AVSTRIC>)resObj;
			return typedProvider;
		}
		if (avStreamIncomingType == AvStreamIncomingFromEsMq.class) {
			ThreadDataProvEsH264FromMq resObj = new ThreadDataProvEsH264FromMq(
					paramsCommon.copyToThreadDpCommon()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvEsBase<VideoH264Info, AVSTRIC> typedProvider = (ThreadDataProvEsBase<VideoH264Info, AVSTRIC>)resObj;
			return typedProvider;
		}
		if (avStreamIncomingType == AvStreamIncomingFromDemuxMs.class) {
			ThreadDataProvEsH264FromDemuxMs resObj = new ThreadDataProvEsH264FromDemuxMs(
					paramsCommon.copyToThreadDpCommon()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvEsBase<VideoH264Info, AVSTRIC> typedProvider = (ThreadDataProvEsBase<VideoH264Info, AVSTRIC>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("invalid avStreamIncomingType");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		if (globalAuLastOutputNudH26xInfoPtr == null) {
			throw new IllegalStateException("globalAuLastOutputNudH26xInfoPtr == null");
		}
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
		/*System.out.println("frame " + curFragmentData.frameData().rtpFrameNr +
				", isLastFragment=" + curFragmentData.isLastFragment() +
				", isLastOfAU=" + cacheParamsBase.doSetMarker);*/
		if (cachePlainPacket == null) {
			cachePlainPacket = new RtpPacketH264(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFragmentData.isLastFragment(),
					globalAuLastOutputNudH26xInfoPtr,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFragmentData.isLastFragment(),
					globalAuLastOutputNudH26xInfoPtr,
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull H26xNalUnitData<VideoH264Info> createNud() {
		H26xNalUnitData<VideoH264Info> resObj = new H26xNalUnitData<>();
		resObj.h26xInfo = new VideoH264Info();
		return resObj;
	}

	@Override
	protected @NonNull VideoH264Info createCodecInfo() {
		return new VideoH264Info();
	}

	@Override
	protected @NonNull String debugNudTypeToString(byte nudTypeBy) {
		return VideoH264Info.NalUnitType.of(nudTypeBy).toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean isNalUnitNonVclSei(VideoH264Info nalInfo) {
		return (nalInfo.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_SEI);
	}

	@Override
	protected boolean isNalUnitLeadingNonVcl(VideoH264Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case VideoH264Info.NalUnitType.NVCL_SPS, VideoH264Info.NalUnitType.NVCL_PPS,
					 VideoH264Info.NalUnitType.NVCL_AUD, VideoH264Info.NalUnitType.NVCL_SEI -> true;
				default -> false;
			};
	}

	@Override
	protected boolean isNalUnitTrailingNonVcl(VideoH264Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case VideoH264Info.NalUnitType.NVCL_EOS, VideoH264Info.NalUnitType.NVCL_EOB, VideoH264Info.NalUnitType.NVCL_FD -> true;
				default -> false;
			};
	}

}

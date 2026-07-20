package org.tsitle.rtsp_server.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketH265;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvH265FromDemuxMs;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvH265FromFile;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvH265FromMq;
import org.tsitle.rtsp_server.threads.rtp.*;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderH265;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.lib_xrtxp.avdata.VideoH265Info;

public final class ThreadRtpSenderH265<
			AVSTRIC extends AvStreamIncomingBase,
			FGAV extends FrameGrabberAvBase<AVSTRIC>
		> extends ThreadRtpSenderH26xBase<VideoH265Info, AVSTRIC, FGAV, ThreadDataProvBase<VideoH265Info, FGAV>> {

	private @Nullable RtpPacketH265 cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param frameGrabberAvType Class of the FrameGrabberAv object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH265 Thread-specific parameters
	 */
	public ThreadRtpSenderH265(
				Class<AVSTRIC> avStreamIncomingType,
				Class<FGAV> frameGrabberAvType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderH265 paramsH265
			) {
		super(
				avStreamIncomingType,
				frameGrabberAvType,
				paramsCommon,
				paramsVideoCommon,
				RtpPacketType.V_H265
			);

		//
		paramsH265.validate();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull ThreadDataProvBase<VideoH265Info, FGAV> newThreadDataProv() {
		if (frameGrabberAvType == FrameGrabberVideoH26xFromEsFile.class) {
			ThreadDataProvH265FromFile resObj = new ThreadDataProvH265FromFile(
					paramsCommon,
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoH265Info, FGAV> typedProvider = (ThreadDataProvBase<VideoH265Info, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberVideoH26xFromEsMq.class) {
			ThreadDataProvH265FromMq resObj = new ThreadDataProvH265FromMq(
					paramsCommon
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoH265Info, FGAV> typedProvider = (ThreadDataProvBase<VideoH265Info, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberAvFromDemuxMs.class) {
			ThreadDataProvH265FromDemuxMs resObj = new ThreadDataProvH265FromDemuxMs(
					paramsCommon
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoH265Info, FGAV> typedProvider = (ThreadDataProvBase<VideoH265Info, FGAV>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("invalid frameGrabberAvType");
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
		/*System.out.println("frame " + curFragmentData.frameData().rtpFrameNr +
				", isLastFragment=" + curFragmentData.isLastFragment() +
				", isLastOfAU=" + cacheParamsBase.doSetMarker);*/
		if (cachePlainPacket == null) {
			cachePlainPacket = new RtpPacketH265(
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
	protected @NonNull H26xNalUnitData<VideoH265Info> createNud() {
		H26xNalUnitData<VideoH265Info> resObj = new H26xNalUnitData<>();
		resObj.h26xInfo = new VideoH265Info();
		return resObj;
	}

	@Override
	protected @NonNull VideoH265Info createCodecInfo() {
		return new VideoH265Info();
	}

	@Override
	protected @NonNull String debugNudTypeToString(byte nudTypeBy) {
		return VideoH265Info.NalUnitType.of(nudTypeBy).toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean isNalUnitNonVclSei(VideoH265Info nalInfo) {
		return (nalInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_SEI_PREFIX ||
				nalInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_SEI_SUFFIX);
	}

	@Override
	protected boolean isNalUnitLeadingNonVcl(VideoH265Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case NVCL_VPS, NVCL_SPS, NVCL_PPS, NVCL_AUD, NVCL_SEI_PREFIX -> true;
				default -> false;
			};
	}

	@Override
	protected boolean isNalUnitTrailingNonVcl(VideoH265Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case NVCL_SEI_SUFFIX, NVCL_FD, NVCL_EOS, NVCL_EOB -> true;
				default -> false;
			};
	}

}

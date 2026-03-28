package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avdata.*;
import org.tsitle.rtsp.avstreams.*;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketH264;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvH264FromFile;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvH264FromMq;
import org.tsitle.rtsp.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH264;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

import java.util.Objects;

public final class ThreadRtpSenderH264<
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>
		> extends ThreadRtpSenderH26xBase<VideoH264Info, AVSTRIC, AVSTROG, ThreadDataProvBase<VideoH264Info, AVSTROG>> {

	private @Nullable RtpPacketH264 cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param avStreamOutgoingType Class of the AvStreamOutgoing object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH264 Thread-specific parameters
	 */
	public ThreadRtpSenderH264(
				Class<AVSTRIC> avStreamIncomingType,
				Class<AVSTROG> avStreamOutgoingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderH264 paramsH264
			) {
		super(
				avStreamIncomingType,
				avStreamOutgoingType,
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
	protected @NonNull ThreadDataProvBase<VideoH264Info, AVSTROG> newThreadDataProv() {
		if (avStreamOutgoingType == VideoStreamOutgoingH26xFromFile.class) {
			ThreadDataProvH264FromFile resObj = new ThreadDataProvH264FromFile(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					paramsVideoCommon,
					Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
					(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoH264Info, AVSTROG> typedProvider = (ThreadDataProvBase<VideoH264Info, AVSTROG>)resObj;
			return typedProvider;
		}
		if (avStreamOutgoingType == VideoStreamOutgoingH26xFromMq.class) {
			ThreadDataProvH264FromMq resObj = new ThreadDataProvH264FromMq(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					paramsVideoCommon,
					Objects.requireNonNull((AvStreamIncomingFromMq)avStreamIncomingObj)
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoH264Info, AVSTROG> typedProvider = (ThreadDataProvBase<VideoH264Info, AVSTROG>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("avStreamOutgoingType must be VideoStreamOutgoingH26xFromXxx");
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
		if (! paramsCommon.getIsRtxpEncryptionEnabled()) {
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

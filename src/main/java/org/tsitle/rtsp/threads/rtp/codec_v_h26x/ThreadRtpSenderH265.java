package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.avstreams.*;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketH265;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvH265FromFile;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvH265FromMq;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH265;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.rtsp.avdata.VideoH265Info;

import java.util.Objects;

public final class ThreadRtpSenderH265<
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>
		> extends ThreadRtpSenderH26xBase<VideoH265Info, AVSTRIC, AVSTROG, ThreadDataProvBase<VideoH265Info, AVSTROG>> {

	private @Nullable RtpPacketH265 cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param avStreamOutgoingType Class of the AvStreamOutgoing object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH265 Thread-specific parameters
	 */
	public ThreadRtpSenderH265(
				Class<AVSTRIC> avStreamIncomingType,
				Class<AVSTROG> avStreamOutgoingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderH265 paramsH265
			) {
		super(
				avStreamIncomingType,
				avStreamOutgoingType,
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
	protected @NonNull ThreadDataProvBase<VideoH265Info, AVSTROG> newThreadDataProv() {
		if (avStreamOutgoingType == VideoStreamOutgoingH26xFromFile.class) {
			ThreadDataProvH265FromFile resObj = new ThreadDataProvH265FromFile(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					paramsVideoCommon,
					Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
					(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoH265Info, AVSTROG> typedProvider = (ThreadDataProvBase<VideoH265Info, AVSTROG>)resObj;
			return typedProvider;
		}
		if (avStreamOutgoingType == VideoStreamOutgoingH26xFromMq.class) {
			ThreadDataProvH265FromMq resObj = new ThreadDataProvH265FromMq(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					paramsVideoCommon,
					Objects.requireNonNull((AvStreamIncomingFromMq)avStreamIncomingObj)
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<VideoH265Info, AVSTROG> typedProvider = (ThreadDataProvBase<VideoH265Info, AVSTROG>)resObj;
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
		if (! paramsCommon.getIsRtxpEncryptionEnabled()) {
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

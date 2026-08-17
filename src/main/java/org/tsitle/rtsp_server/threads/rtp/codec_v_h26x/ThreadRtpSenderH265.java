package org.tsitle.rtsp_server.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketH265;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.lib_dataprov.threads_es.codec_v_h26x.ThreadDataProvEsH265FromDemuxMs;
import org.tsitle.lib_dataprov.threads_es.codec_v_h26x.ThreadDataProvEsH265FromRawFile;
import org.tsitle.lib_dataprov.threads_es.codec_v_h26x.ThreadDataProvEsH265FromMq;
import org.tsitle.rtsp_server.threads.rtp.*;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpH265;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpVideoCommon;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.VideoH265Info;

public final class ThreadRtpSenderH265<AVSTRIC extends AvStreamIncomingBase>
		extends ThreadRtpSenderH26xBase<VideoH265Info, AVSTRIC, ThreadDataProvEsBase<VideoH265Info>> {

	private @Nullable RtpPacketH265 cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH265 Thread-specific parameters
	 */
	public ThreadRtpSenderH265(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadDpVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadDpH265 paramsH265
			) {
		super(
				avStreamIncomingType,
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
	protected @NonNull ThreadDataProvEsBase<VideoH265Info> newThreadDataProv() {
		if (avStreamIncomingType == AvStreamIncomingFromEsRawFile.class) {
			return new ThreadDataProvEsH265FromRawFile(
					paramsCommon.copyToThreadDpCommon(),
					10
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromEsMq.class) {
			return new ThreadDataProvEsH265FromMq(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromDemuxMs.class) {
			return new ThreadDataProvEsH265FromDemuxMs(
					paramsCommon.copyToThreadDpCommon()
				);
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

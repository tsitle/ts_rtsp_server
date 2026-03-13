package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketH265;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvH265;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH265;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;
import org.tsitle.rtsp.avdata.VideoH265Info;

import java.util.Objects;

public final class ThreadRtpSenderH265 extends ThreadRtpSenderH26xBase<VideoH265Info, ThreadDataProvH265> {

	/**
	 * Constructor.
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH265 Thread-specific parameters
	 */
	public ThreadRtpSenderH265(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderH265 paramsH265
			) {
		super(
				paramsCommon,
				paramsVideoCommon,
				RtpPacketType.V_H265
			);

		//
		paramsH265.validate();

		//
		this.cacheH26xInfo = new VideoH265Info();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull ThreadDataProvH265 newThreadDataProv() {
		return new ThreadDataProvH265(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsVideoCommon,
				Objects.requireNonNull(avStreamIncoming),
				//(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),  @TODO
				5,
				paramsCommon.getDebugRewindMediaFiles()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbRtpPacketPayloadSupplier()";

		if (globalCurNudPtr == null) {
			throw new IllegalStateException(FNC_NAME + ": globalCurNudPtr == null");
		}
		if (globalCurNudPtr.h26xInfo == null) {
			throw new IllegalStateException(FNC_NAME + ": globalCurNudPtr.h26xInfo == null");
		}
		prepareRtpPacketDataForFragment(curFragmentData);
		/*System.out.println("frame " + curFragmentData.frameData().rtpFrameNr +
				", isLastFragment=" + curFragmentData.isLastFragment() +
				", isLastOfAU=" + cacheParamsBase.doSetMarker);*/
		return new RtpPacketH265(
				cacheParamsBase,
				curFragmentData.fragmentOffset(),
				curFragmentData.isLastFragment(),
				globalCurNudPtr.h26xInfo,
				cacheRtpInnerPayloadBuf
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean isNonVclSei(VideoH265Info nalInfo) {
		return (nalInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_SEI_PREFIX ||
				nalInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_SEI_SUFFIX);
	}

	@Override
	protected boolean isLeadingNonVcl(VideoH265Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case NVCL_VPS, NVCL_SPS, NVCL_PPS, NVCL_AUD, NVCL_SEI_PREFIX -> true;
				default -> false;
			};
	}

	@Override
	protected boolean isTrailingNonVcl(VideoH265Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case NVCL_SEI_SUFFIX, NVCL_FD, NVCL_EOS, NVCL_EOB -> true;
				default -> false;
			};
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected VideoH265Info getCloneOfH26xInfo(@NonNull VideoH265Info src) {
		return src.clone();
	}

}

package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.*;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketH264;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvH264;
import org.tsitle.rtsp.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderH264;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderVideoCommon;

public final class ThreadRtpSenderH264 extends ThreadRtpSenderH26xBase<VideoH264Info, ThreadDataProvH264> {

	/**
	 * Constructor.
	 * @param paramsCommon Common thread parameters
	 * @param paramsVideoCommon Common Video thread parameters
	 * @param paramsH264 Thread-specific parameters
	 */
	public ThreadRtpSenderH264(
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderVideoCommon paramsVideoCommon,
				@NonNull ParamsThreadRtpSenderH264 paramsH264
			) {
		super(
				paramsCommon,
				paramsVideoCommon,
				RtpPacketType.V_H264
			);

		//
		paramsH264.validate();

		//
		this.cacheH26xInfo = new VideoH264Info();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull ThreadDataProvH264 newThreadDataProv() {
		return new ThreadDataProvH264(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsVideoCommon,
				(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),
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
		return new RtpPacketH264(
				cacheParamsBase,
				curFragmentData.fragmentOffset(),
				curFragmentData.isLastFragment(),
				globalCurNudPtr.h26xInfo,
				cacheRtpInnerPayloadBuf
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected boolean isLeadingNonVcl(VideoH264Info nalInfo) {
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
	protected boolean isTrailingNonVcl(VideoH264Info nalInfo) {
		if (nalInfo.isVclNalUnit) {
			return false;
		}

		return switch (nalInfo.nalUnitTypeEn) {
				case VideoH264Info.NalUnitType.NVCL_EOS, VideoH264Info.NalUnitType.NVCL_EOB, VideoH264Info.NalUnitType.NVCL_FD -> true;
				default -> false;
			};
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected VideoH264Info getCloneOfH26xInfo(@NonNull VideoH264Info src) {
		return src.clone();
	}

}

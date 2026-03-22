package org.tsitle.rtsp.threads.rtp.codec_v_h26x;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avstreams.*;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.packets.rtp.RtpEncryptedPacket;
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

		//
		this.cacheH26xInfo = new VideoH265Info();
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
		RtpPacketH265 plainPacket = new RtpPacketH265(
				cacheParamsBase,
				curFragmentData.fragmentOffset(),
				curFragmentData.isLastFragment(),
				globalCurNudPtr.h26xInfo,
				cacheRtpInnerPayloadBuf
			);
		if (! paramsCommon.getIsRtpEncryptionEnabled()) {
			return plainPacket;
		}

		//
		RtpPacketContainerBase encryptedPacket;
		try {
			encryptedPacket = new RtpEncryptedPacket(
					RtpPacketType.V_H265,
					plainPacket,
					paramsCommon.getSrtpContext().orElseThrow()
				);
		} catch (SrtpSecurityException e) {
			final String errMsg = "SrtpSecurityException caught: " + e.getMessage();
			logError(FNC_NAME, errMsg);
			throw new IllegalStateException(FNC_NAME + ": " + errMsg);
		}
		return encryptedPacket;
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

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected VideoH265Info getCloneOfH26xInfo(@NonNull VideoH265Info src) {
		return src.clone();
	}

}

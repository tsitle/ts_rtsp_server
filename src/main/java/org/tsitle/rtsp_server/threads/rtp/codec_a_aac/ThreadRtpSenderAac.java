package org.tsitle.rtsp_server.threads.rtp.codec_a_aac;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAudioAacFromFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingBase;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp_server.avstreams.FrameGrabberAvBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketAac;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvAacFromFile;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAac;

import java.util.Objects;

public final class ThreadRtpSenderAac<
			AVSTRIC extends AvStreamIncomingBase,
			FGAV extends FrameGrabberAvBase<AVSTRIC>
		> extends ThreadRtpSenderBase<AudioAacInfo, AVSTRIC, FGAV, ThreadDataProvBase<AudioAacInfo, FGAV>> {

	private final ParamsThreadRtpSenderAudioCommon paramsAudioCommon;
	private final ParamsThreadRtpSenderAac paramsAac;

	private final AudioAacInfo curFrameAacInfo = new AudioAacInfo();
	private @Nullable RtpPacketAac cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param frameGrabberAvType Class of the FrameGrabberAv object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsAac Thread-specific parameters
	 */
	public ThreadRtpSenderAac(
				Class<AVSTRIC> avStreamIncomingType,
				Class<FGAV> frameGrabberAvType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderAac paramsAac
			) {
		super(
				avStreamIncomingType,
				frameGrabberAvType,
				paramsCommon,
				Objects.requireNonNull(paramsAudioCommon).getAudioSamplerateHz(),
				RtpPacketType.A_AAC
			);

		//
		this.rtpTicksPerFrame = paramsAudioCommon.getRtpAudioSpf();

		//
		paramsAudioCommon.validate();
		this.paramsAudioCommon = paramsAudioCommon.clone();
		paramsAac.validate();
		this.paramsAac = paramsAac.clone();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public synchronized void notifyCongestionLevelChange(int congestionLevel) {
		if (threadDataProv != null) {
			threadDataProv.notifyCongestionLevelChange(congestionLevel);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull ThreadDataProvBase<AudioAacInfo, FGAV> newThreadDataProv() {
		if (frameGrabberAvType != FrameGrabberAudioAacFromFile.class) {
			throw new RuntimeException("frameGrabberAvType must be FrameGrabberAudioAacFromXxx");
		}
		ThreadDataProvAacFromFile resObj = new ThreadDataProvAacFromFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsAudioCommon,
				paramsAac,
				Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
				10,
				paramsCommon.getDebugRewindMediaFiles()
			);
		@SuppressWarnings("unchecked")
		ThreadDataProvBase<AudioAacInfo, FGAV> typedProvider = (ThreadDataProvBase<AudioAacInfo, FGAV>)resObj;
		return typedProvider;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameAacInfo);
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		/*
		 * Only set the marker bit to 1 if this is the last fragment of the AAC frame.
		 * See https://datatracker.ietf.org/doc/html/rfc3640#section-3.2.3.1
		 */
		return isLastFragment;
	}

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		prepareRtpPacketDataForFragment(curFragmentData);
		if (cacheRtpInnerPayloadBufView == null) {
			throw new IllegalStateException("cacheRtpInnerPayloadBufView == null");
		}
		if (cachePlainPacket == null) {
			cachePlainPacket = new RtpPacketAac(
					cacheParamsBase,
					(byte)curFragmentData.fragmentIndex(),
					curFrameAacInfo,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					(byte)curFragmentData.fragmentIndex(),
					curFrameAacInfo,
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

}

package org.tsitle.rtsp_server.threads.rtp.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.AudioPcmInfo;
import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketPcm;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvPcmFromFile;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvPcmFromMq;
import org.tsitle.rtsp_server.threads.rtp.*;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderPcm;

import java.util.Objects;

public final class ThreadRtpSenderPcm<
			AVSTRIC extends AvStreamIncomingBase,
			FGAV extends FrameGrabberAvBase<AVSTRIC>
		> extends ThreadRtpSenderBase<AudioPcmInfo, AVSTRIC, FGAV, ThreadDataProvBase<AudioPcmInfo, FGAV>> {

	private final ParamsThreadRtpSenderAudioCommon paramsAudioCommon;
	private final ParamsThreadRtpSenderPcm paramsPcm;

	/** Number of audio channels */
	public int audioChannelCount;

	/** Bits per sample (8 or 16) */
	public int audioBitsPerSample;
	/** RTP Payload type */
	private final RtpPacketType rtpPayloadType;

	private final AudioPcmInfo curFramePcmInfo = new AudioPcmInfo();
	private @Nullable RtpPacketPcm cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param frameGrabberAvType Class of the FrameGrabberAv object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 */
	public ThreadRtpSenderPcm(
				Class<AVSTRIC> avStreamIncomingType,
				Class<FGAV> frameGrabberAvType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm
			) {
		super(
				avStreamIncomingType,
				frameGrabberAvType,
				paramsCommon,
				Objects.requireNonNull(paramsAudioCommon).getAudioSamplerateHz(),
				Objects.requireNonNull(paramsPcm).getAudioCodec()
			);

		//
		this.rtpTicksPerFrame = paramsAudioCommon.getRtpAudioSpf();

		//
		paramsAudioCommon.validate();
		this.paramsAudioCommon = paramsAudioCommon.clone();
		paramsPcm.validate();
		this.paramsPcm = paramsPcm.clone();

		//
		this.audioChannelCount = paramsPcm.getAudioChannelCount();
		this.audioBitsPerSample = paramsPcm.getAudioBitsPerSample();
		this.rtpPayloadType = paramsPcm.getAudioCodec();
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
	protected @NonNull ThreadDataProvBase<AudioPcmInfo, FGAV> newThreadDataProv() {
		if (frameGrabberAvType == FrameGrabberAudioPcmFromFile.class) {
			ThreadDataProvPcmFromFile resObj = new ThreadDataProvPcmFromFile(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					paramsAudioCommon,
					paramsPcm,
					Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<AudioPcmInfo, FGAV> typedProvider = (ThreadDataProvBase<AudioPcmInfo, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberAudioPcmFromMq.class) {
			ThreadDataProvPcmFromMq resObj = new ThreadDataProvPcmFromMq(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					paramsAudioCommon,
					paramsPcm,
					Objects.requireNonNull((AvStreamIncomingFromMq)avStreamIncomingObj)
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<AudioPcmInfo, FGAV> typedProvider = (ThreadDataProvBase<AudioPcmInfo, FGAV>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("frameGrabberAvType must be FrameGrabberAudioPcmFromXxx");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFramePcmInfo);
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		/*
		 * For audio (without noise suppression) the marker bit is always set to 0.
		 * See https://datatracker.ietf.org/doc/html/rfc3551#section-4.1
		 */
		return false;
	}

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		prepareRtpPacketDataForFragment(curFragmentData);
		if (cacheRtpInnerPayloadBufView == null) {
			throw new IllegalStateException("cacheRtpInnerPayloadBufView == null");
		}
		if (cachePlainPacket == null) {
			cachePlainPacket = new RtpPacketPcm(
					cacheParamsBase,
					rtpPayloadType,
					curFragmentData.fragmentOffset(),
					curFramePcmInfo,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

}

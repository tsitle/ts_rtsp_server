package org.tsitle.rtsp.threads.rtp.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.AudioPcmInfo;
import org.tsitle.rtsp.avstreams.*;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.SrtpSecurityException;
import org.tsitle.rtsp.packets.rtp.RtpEncryptedPacket;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketPcm;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvPcmFromFile;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvPcmFromMq;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderPcm;

import java.util.Objects;

public final class ThreadRtpSenderPcm<
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>
		> extends ThreadRtpSenderBase<AudioPcmInfo, AVSTRIC, AVSTROG, ThreadDataProvBase<AudioPcmInfo, AVSTROG>> {

	private final ParamsThreadRtpSenderAudioCommon paramsAudioCommon;
	private final ParamsThreadRtpSenderPcm paramsPcm;

	/** Number of audio channels */
	public int audioChannelCount;

	/** Bits per sample (8 or 16) */
	public int audioBitsPerSample;
	/** RTP Payload type */
	private final RtpPacketType rtpPayloadType;

	private final AudioPcmInfo curFramePcmInfo = new AudioPcmInfo();
	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgAudioFrameBuf = new BufferExt();
	private final BufferExt cacheBufForFD = new BufferExt();

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param avStreamOutgoingType Class of the AvStreamOutgoing object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 */
	public ThreadRtpSenderPcm(
				Class<AVSTRIC> avStreamIncomingType,
				Class<AVSTROG> avStreamOutgoingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderPcm paramsPcm
			) {
		super(
				avStreamIncomingType,
				avStreamOutgoingType,
				paramsCommon,
				Objects.requireNonNull(paramsPcm).getAudioSampleRateHz(),
				Objects.requireNonNull(paramsPcm).getAudioCodec()
			);

		//
		this.rtpTicksPerFrame = paramsPcm.getRtpAudioSpf();

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
	protected @NonNull ThreadDataProvBase<AudioPcmInfo, AVSTROG> newThreadDataProv() {
		if (avStreamOutgoingType == AudioStreamOutgoingPcmFromFile.class) {
			ThreadDataProvPcmFromFile resObj = new ThreadDataProvPcmFromFile(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					paramsAudioCommon,
					paramsPcm,
					Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
					(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<AudioPcmInfo, AVSTROG> typedProvider = (ThreadDataProvBase<AudioPcmInfo, AVSTROG>)resObj;
			return typedProvider;
		}
		if (avStreamOutgoingType == AudioStreamOutgoingPcmFromMq.class) {
			ThreadDataProvPcmFromMq resObj = new ThreadDataProvPcmFromMq(
					paramsCommon.getLogMsgInterface().orElseThrow(),
					paramsAudioCommon,
					paramsPcm,
					Objects.requireNonNull((AvStreamIncomingFromMq)avStreamIncomingObj)
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<AudioPcmInfo, AVSTROG> typedProvider = (ThreadDataProvBase<AudioPcmInfo, AVSTROG>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("avStreamOutgoingType must be AudioStreamOutgoingPcmFromXxx");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(
				cacheOrgAudioFrameBuf,
				cacheBufForFD,
				curFramePcmInfo
			);
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
		final String FNC_NAME = getClass().getSimpleName() + ".cbRtpPacketPayloadSupplier()";

		prepareRtpPacketDataForFragment(curFragmentData);
		RtpPacketPcm plainPacket = new RtpPacketPcm(
				cacheParamsBase,
				rtpPayloadType,
				curFragmentData.fragmentOffset(),
				curFramePcmInfo,
				cacheRtpInnerPayloadBuf
			);
		if (! paramsCommon.getIsRtxpEncryptionEnabled()) {
			return plainPacket;
		}

		//
		RtpPacketContainerBase encryptedPacket;
		try {
			encryptedPacket = new RtpEncryptedPacket(
					plainPacket.getPayloadType(),
					plainPacket,
					paramsCommon.getSrtxpContext().orElseThrow()
				);
		} catch (SrtpSecurityException e) {
			final String errMsg = "SrtpSecurityException caught: " + e.getMessage();
			logError(FNC_NAME, errMsg);
			throw new IllegalStateException(FNC_NAME + ": " + errMsg);
		}
		return encryptedPacket;
	}

}

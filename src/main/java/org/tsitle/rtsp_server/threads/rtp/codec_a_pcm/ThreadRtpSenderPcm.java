package org.tsitle.rtsp_server.threads.rtp.codec_a_pcm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.*;
import org.tsitle.lib_xrtxp.avdata.codec_a_pcm.AudioPcmInfo;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketPcm;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.lib_dataprov.threads_es.codec_a_pcm.ThreadDataProvEsPcmFromDmxFc;
import org.tsitle.lib_dataprov.threads_es.codec_a_pcm.ThreadDataProvEsPcmFromRawFile;
import org.tsitle.lib_dataprov.threads_es.codec_a_pcm.ThreadDataProvEsPcmFromMq;
import org.tsitle.rtsp_server.threads.rtp.*;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpPcm;

import java.util.Objects;

public final class ThreadRtpSenderPcm<AVSTRIC extends AvStreamIncomingBase>
		extends ThreadRtpSenderBase<AudioPcmInfo, AVSTRIC, ThreadDataProvEsBase<AudioPcmInfo>> {

	private final ParamsThreadDpAudioCommon paramsAudioCommon;
	private final ParamsThreadDpPcm paramsPcm;

	/** Number of audio channels */
	public int audioChannelCount;

	/** Bits per sample (8 or 16) */
	public int audioBitsPerSample;
	/** RTP Payload type */
	private final RtpPacketType rtpPayloadType;

	private final AudioPcmInfo curFramePcmInfo = new AudioPcmInfo();
	private @Nullable RtpPacketPcm cachePlainPacket = null;

	private int lastSpciad = -1;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsPcm Thread-specific parameters
	 */
	public ThreadRtpSenderPcm(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadDpAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadDpPcm paramsPcm
			) {
		super(
				avStreamIncomingType,
				paramsCommon,
				Objects.requireNonNull(paramsAudioCommon).getAudioSamplerate().getSrHz(),
				Objects.requireNonNull(paramsPcm).getAudioCodec()
			);

		//
		this.rtpTicksPerFrame = paramsAudioCommon.getAudioSpf();
		if (this.rtpTicksPerFrame < 1L) {
			this.rtpTicksPerFrame = 1L;  // it is necessary to determine this for each PCM frame (or at least once)
		}

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
	protected @NonNull ThreadDataProvEsBase<AudioPcmInfo> newThreadDataProv() {
		if (avStreamIncomingType == AvStreamIncomingFromEsRawFile.class) {
			return new ThreadDataProvEsPcmFromRawFile(
					paramsCommon.copyToThreadDpCommon(),
					paramsAudioCommon,
					paramsPcm,
					10
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromEsMq.class) {
			return new ThreadDataProvEsPcmFromMq(
					paramsCommon.copyToThreadDpCommon(),
					paramsAudioCommon,
					paramsPcm
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromDmxFc.class) {
			return new ThreadDataProvEsPcmFromDmxFc(
					paramsCommon.copyToThreadDpCommon(),
					paramsAudioCommon,
					paramsPcm
				);
		}
		throw new RuntimeException("invalid avStreamIncomingType");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFramePcmInfo);
	}

	@Override
	protected int cbFragmentSizeAdjust(int fragmentSize) {
		if (curFramePcmInfo.samplesPerChannelInAudioData < 1) {
			return fragmentSize;
		}
		int headerSz = RtpPacketContainerBase.RTP_CONT_HEADER_SIZE + RtpPacketPcm.INNER_HEADER_SIZE;
		int fragSzForSamples = fragmentSize - headerSz;
		if (fragSzForSamples < 1) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".cbFragmentSizeAdjust(): " +
					"fragmentSize <= headerSz");
		}

		/*
		 * We need to ensure that all fragmented RTP packets always contain a complete set of samples per channel.
		 */
		int bytesPerSampleAndChannels = (curFramePcmInfo.bitsPerSample / 8) * curFramePcmInfo.channels;
		if (fragSzForSamples < bytesPerSampleAndChannels) {
			throw new IllegalArgumentException(getClass().getSimpleName() + ".cbFragmentSizeAdjust(): " +
					"fragmentSize < headerSz+samplesForAllChannels");
		}
		int remainder = (fragSzForSamples % bytesPerSampleAndChannels);

		// cut off any incomplete samples
		return fragmentSize - remainder;
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
		//
		if (lastSpciad != curFramePcmInfo.samplesPerChannelInAudioData) {
			/*
			 * For PCM, the frame duration should be constant, but we need to adjust the sleep time
			 * and the [rtpTicksPerFrame] at least once
			 */
			lastSpciad = curFramePcmInfo.samplesPerChannelInAudioData;
			final double tmpFrameDurSecs = ((double)lastSpciad / (double)curFramePcmInfo.samplerate.getSrHz());
			nextFpsForAdaptiveScheduler = (1.0 / tmpFrameDurSecs);

			nextRtpTicksPerFrame = lastSpciad;
		}
		//
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

package org.tsitle.rtsp_server.threads.rtp.codec_a_opus;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.*;
import org.tsitle.lib_xrtxp.avdata.codec_a_opus.AudioOpusInfo;
import org.tsitle.lib_xrtxp.common.types.SampleRateEnum;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketOpus;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.lib_dataprov.threads_es.codec_a_opus.ThreadDataProvEsOpusFromDemuxMs;
import org.tsitle.lib_dataprov.threads_es.codec_a_opus.ThreadDataProvEsOpusFromRawFile;
import org.tsitle.lib_dataprov.threads_es.codec_a_opus.ThreadDataProvEsOpusFromMq;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpOpus;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.util.Objects;

public final class ThreadRtpSenderOpus<AVSTRIC extends AvStreamIncomingBase>
		extends ThreadRtpSenderBase<AudioOpusInfo, AVSTRIC, ThreadDataProvEsBase<AudioOpusInfo>> {

	private final ParamsThreadDpOpus paramsOpus;

	private final AudioOpusInfo curFrameOpusInfo = new AudioOpusInfo();
	private @Nullable RtpPacketOpus cachePlainPacket = null;

	private int lastSpciad = -1;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsOpus Thread-specific parameters
	 */
	public ThreadRtpSenderOpus(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadDpAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadDpOpus paramsOpus
			) {
		super(
				avStreamIncomingType,
				paramsCommon,
				Objects.requireNonNull(paramsAudioCommon).getAudioSamplerate().getSrHz(),
				RtpPacketType.A_OPUS
			);

		//
		this.rtpTicksPerFrame = paramsAudioCommon.getAudioSpf();
		if (this.rtpTicksPerFrame < 1L) {
			this.rtpTicksPerFrame = 1L;  // it is necessary to determine this for each Opus frame (or at least once)
		}

		//
		paramsAudioCommon.validate();
		paramsOpus.validate();
		this.paramsOpus = paramsOpus.clone();
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
	protected @NonNull ThreadDataProvEsBase<AudioOpusInfo> newThreadDataProv() {
		if (avStreamIncomingType == AvStreamIncomingFromEsRawFile.class) {
			return new ThreadDataProvEsOpusFromRawFile(
					paramsCommon.copyToThreadDpCommon(),
					paramsOpus,
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromEsMq.class) {
			return new ThreadDataProvEsOpusFromMq(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromDemuxMs.class) {
			return new ThreadDataProvEsOpusFromDemuxMs(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		throw new RuntimeException("invalid avStreamIncomingType");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameOpusInfo);
	}

	@Override
	protected int cbFragmentSizeAdjust(int fragmentSize) {
		return defaultFragmentSizeAdjust(fragmentSize);
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		/*
		 * Only set the marker bit to 1 if this is the last fragment of the Opus frame.
		 * See https://datatracker.ietf.org/doc/html/rfc7587#section-4.1
		 */
		return isLastFragment;
	}

	@Override
	protected @NonNull RtpPacketContainerBase cbRtpPacketPayloadSupplier(@NonNull FrameFragmentData curFragmentData) {
		prepareRtpPacketDataForFragment(curFragmentData);
		if (cacheRtpInnerPayloadBufView == null) {
			throw new IllegalStateException("cacheRtpInnerPayloadBufView == null");
		}
		//
		if (lastSpciad != curFrameOpusInfo.samplesPerChannelInAudioData) {
			/*
			 * For Opus, the frame duration can vary from frame to frame, but we need to adjust the sleep time
			 * and the [rtpTicksPerFrame] at least once
			 */
			lastSpciad = curFrameOpusInfo.samplesPerChannelInAudioData;
			final double tmpFrameDurSecs = ((double)lastSpciad / (double) SampleRateEnum.SR_048000.getSrHz());
			nextFpsForAdaptiveScheduler = (1.0 / tmpFrameDurSecs);

			nextRtpTicksPerFrame = lastSpciad;
		}
		//
		if (cachePlainPacket == null) {
			cachePlainPacket = new RtpPacketOpus(
					cacheParamsBase,
					curFrameOpusInfo,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					curFrameOpusInfo,
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

}

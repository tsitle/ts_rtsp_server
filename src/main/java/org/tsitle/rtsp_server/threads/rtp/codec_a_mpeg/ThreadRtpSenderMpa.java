package org.tsitle.rtsp_server.threads.rtp.codec_a_mpeg;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingBase;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromDmxFc;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsRawFile;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAudioCommon;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpMpa;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.lib_dataprov.threads_es.codec_a_mpeg.ThreadDataProvEsMpaFromDmxFc;
import org.tsitle.lib_dataprov.threads_es.codec_a_mpeg.ThreadDataProvEsMpaFromMq;
import org.tsitle.lib_dataprov.threads_es.codec_a_mpeg.ThreadDataProvEsMpaFromRawFile;
import org.tsitle.lib_xrtxp.avdata.codec_a_mpeg.AudioMpegInfo;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketMpa;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.util.Objects;

public final class ThreadRtpSenderMpa<AVSTRIC extends AvStreamIncomingBase>
		extends ThreadRtpSenderBase<AudioMpegInfo, AVSTRIC, ThreadDataProvEsBase<AudioMpegInfo>> {

	private final ParamsThreadDpMpa paramsMpa;

	private final AudioMpegInfo curFrameMpaInfo = new AudioMpegInfo();
	private @Nullable RtpPacketMpa cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsMpa Thread-specific parameters
	 */
	public ThreadRtpSenderMpa(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadDpAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadDpMpa paramsMpa
			) {
		super(
				avStreamIncomingType,
				paramsCommon,
				Objects.requireNonNull(paramsAudioCommon).getAudioSamplerate().getSrHz(),
				RtpPacketType.A_MPEG
			);

		//
		this.rtpTicksPerFrame = paramsAudioCommon.getAudioSpf();

		//
		paramsAudioCommon.validate();
		paramsMpa.validate();
		this.paramsMpa = paramsMpa.clone();
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
	protected @NonNull ThreadDataProvEsBase<AudioMpegInfo> newThreadDataProv() {
		if (avStreamIncomingType == AvStreamIncomingFromEsRawFile.class) {
			return new ThreadDataProvEsMpaFromRawFile(
					paramsCommon.copyToThreadDpCommon(),
					paramsMpa,
					10
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromEsMq.class) {
			return new ThreadDataProvEsMpaFromMq(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromDmxFc.class) {
			return new ThreadDataProvEsMpaFromDmxFc(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		throw new RuntimeException("invalid avStreamIncomingType");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameMpaInfo);
	}

	@Override
	protected int cbFragmentSizeAdjust(int fragmentSize) {
		return defaultFragmentSizeAdjust(fragmentSize);
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
		if (cachePlainPacket == null) {
			cachePlainPacket = new RtpPacketMpa(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFrameMpaInfo,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFrameMpaInfo,
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

}

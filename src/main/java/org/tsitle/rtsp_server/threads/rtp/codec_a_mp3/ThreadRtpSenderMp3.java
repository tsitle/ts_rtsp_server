package org.tsitle.rtsp_server.threads.rtp.codec_a_mp3;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingBase;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromDemuxMs;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsMq;
import org.tsitle.lib_dataprov.avstreams.AvStreamIncomingFromEsRawFile;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpAudioCommon;
import org.tsitle.lib_dataprov.threadparams.ParamsThreadDpMp3;
import org.tsitle.lib_dataprov.threads_es.ThreadDataProvEsBase;
import org.tsitle.lib_dataprov.threads_es.codec_a_mp3.ThreadDataProvEsMp3FromDemuxMs;
import org.tsitle.lib_dataprov.threads_es.codec_a_mp3.ThreadDataProvEsMp3FromMq;
import org.tsitle.lib_dataprov.threads_es.codec_a_mp3.ThreadDataProvEsMp3FromRawFile;
import org.tsitle.lib_xrtxp.avdata.codec_a_mp3.AudioMp3Info;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.lib_xrtxp.packets.rtp.codecs.RtpPacketMp3;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.util.Objects;

public final class ThreadRtpSenderMp3<AVSTRIC extends AvStreamIncomingBase>
		extends ThreadRtpSenderBase<AudioMp3Info, AVSTRIC, ThreadDataProvEsBase<AudioMp3Info>> {

	private final ParamsThreadDpMp3 paramsMp3;

	private final AudioMp3Info curFrameMp3Info = new AudioMp3Info();
	private @Nullable RtpPacketMp3 cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsMp3 Thread-specific parameters
	 */
	public ThreadRtpSenderMp3(
				Class<AVSTRIC> avStreamIncomingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadDpAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadDpMp3 paramsMp3
			) {
		super(
				avStreamIncomingType,
				paramsCommon,
				Objects.requireNonNull(paramsAudioCommon).getAudioSamplerate().getSrHz(),
				RtpPacketType.A_MP3
			);

		//
		this.rtpTicksPerFrame = paramsAudioCommon.getAudioSpf();

		//
		paramsAudioCommon.validate();
		paramsMp3.validate();
		this.paramsMp3 = paramsMp3.clone();
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
	protected @NonNull ThreadDataProvEsBase<AudioMp3Info> newThreadDataProv() {
		if (avStreamIncomingType == AvStreamIncomingFromEsRawFile.class) {
			return new ThreadDataProvEsMp3FromRawFile(
					paramsCommon.copyToThreadDpCommon(),
					paramsMp3,
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromEsMq.class) {
			return new ThreadDataProvEsMp3FromMq(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		if (avStreamIncomingType == AvStreamIncomingFromDemuxMs.class) {
			return new ThreadDataProvEsMp3FromDemuxMs(
					paramsCommon.copyToThreadDpCommon()
				);
		}
		throw new RuntimeException("invalid avStreamIncomingType");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameMp3Info);
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
			cachePlainPacket = new RtpPacketMp3(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFrameMp3Info,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					curFragmentData.fragmentOffset(),
					curFrameMp3Info,
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

}

package org.tsitle.rtsp_server.threads.rtp.codec_a_ac3;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Info;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketAc3;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.avstreams.*;
import org.tsitle.rtsp_server.avstreams.codec_a_ac3.FrameGrabberAudioAc3FromEsFile;
import org.tsitle.rtsp_server.avstreams.codec_a_ac3.FrameGrabberAudioAc3FromEsMq;
import org.tsitle.rtsp_server.threads.dataprovider_es.codec_a_ac3.ThreadDataProvAc3FromDemuxMs;
import org.tsitle.rtsp_server.threads.dataprovider_es.codec_a_ac3.ThreadDataProvAc3FromFile;
import org.tsitle.rtsp_server.threads.dataprovider_es.codec_a_ac3.ThreadDataProvAc3FromMq;
import org.tsitle.rtsp_server.threads.dataprovider_es.ThreadDataProvBase;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAc3;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.util.Objects;

public final class ThreadRtpSenderAc3<
			AVSTRIC extends AvStreamIncomingBase,
			FGAV extends FrameGrabberAvBase<AVSTRIC>
		> extends ThreadRtpSenderBase<AudioAc3Info, AVSTRIC, FGAV, ThreadDataProvBase<AudioAc3Info, FGAV>> {

	private final AudioAc3Info curFrameAc3Info = new AudioAc3Info();
	private @Nullable RtpPacketAc3 cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param frameGrabberAvType Class of the FrameGrabberAv object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsAc3 Thread-specific parameters
	 */
	public ThreadRtpSenderAc3(
				Class<AVSTRIC> avStreamIncomingType,
				Class<FGAV> frameGrabberAvType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderAc3 paramsAc3
			) {
		super(
				avStreamIncomingType,
				frameGrabberAvType,
				paramsCommon,
				Objects.requireNonNull(paramsAudioCommon).getAudioSamplerate().getSrHz(),
				RtpPacketType.A_AC3
			);

		//
		this.rtpTicksPerFrame = paramsAudioCommon.getRtpAudioSpf();

		//
		paramsAudioCommon.validate();
		paramsAc3.validate();
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
	protected @NonNull ThreadDataProvBase<AudioAc3Info, FGAV> newThreadDataProv() {
		if (frameGrabberAvType == FrameGrabberAudioAc3FromEsFile.class) {
			ThreadDataProvAc3FromFile resObj = new ThreadDataProvAc3FromFile(
					paramsCommon,
					10,
					paramsCommon.getDebugRewindMediaFiles()
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<AudioAc3Info, FGAV> typedProvider = (ThreadDataProvBase<AudioAc3Info, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberAudioAc3FromEsMq.class) {
			ThreadDataProvAc3FromMq resObj = new ThreadDataProvAc3FromMq(
					paramsCommon
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<AudioAc3Info, FGAV> typedProvider = (ThreadDataProvBase<AudioAc3Info, FGAV>)resObj;
			return typedProvider;
		}
		if (frameGrabberAvType == FrameGrabberAvFromDemuxMs.class) {
			ThreadDataProvAc3FromDemuxMs resObj = new ThreadDataProvAc3FromDemuxMs(
					paramsCommon
				);
			@SuppressWarnings("unchecked")
			ThreadDataProvBase<AudioAc3Info, FGAV> typedProvider = (ThreadDataProvBase<AudioAc3Info, FGAV>)resObj;
			return typedProvider;
		}
		throw new RuntimeException("invalid frameGrabberAvType");
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameAc3Info);
	}

	@Override
	protected int cbFragmentSizeAdjust(int fragmentSize) {
		return defaultFragmentSizeAdjust(fragmentSize);
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		/*
		 * Only set the marker bit to 1 if this is the last fragment of the AC-3 frame.
		 * See https://datatracker.ietf.org/doc/html/rfc4184#section-4.1.1
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
			cachePlainPacket = new RtpPacketAc3(
					cacheParamsBase,
					curFragmentData.fragmentCount(),
					curFragmentData.isLastFragment(),
					curFrameAc3Info,
					cacheRtpInnerPayloadBufView
				);
		} else {
			cachePlainPacket.updatePacket(
					cacheParamsBase,
					curFragmentData.fragmentCount(),
					curFragmentData.isLastFragment(),
					cacheRtpInnerPayloadBufView
				);
		}
		if (! paramsCommon.getCryptoIsRtxpEncryptionEnabled()) {
			return cachePlainPacket;
		}
		return encryptRtpPacketPayload(cachePlainPacket);
	}

}

package org.tsitle.rtsp_server.threads.rtp.codec_a_ac3;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.avdata.AudioAc3Info;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketAc3;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp_server.avstreams.AudioStreamOutgoingAc3FromFile;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingBase;
import org.tsitle.rtsp_server.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp_server.avstreams.AvStreamOutgoingBase;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvAc3FromFile;
import org.tsitle.rtsp_server.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp_server.threads.rtp.FrameData;
import org.tsitle.rtsp_server.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp_server.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAc3;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp_server.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.util.Objects;

public final class ThreadRtpSenderAc3<
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>
		> extends ThreadRtpSenderBase<AudioAc3Info, AVSTRIC, AVSTROG, ThreadDataProvBase<AudioAc3Info, AVSTROG>> {

	private final ParamsThreadRtpSenderAudioCommon paramsAudioCommon;
	private final ParamsThreadRtpSenderAc3 paramsAc3;

	private final AudioAc3Info curFrameAc3Info = new AudioAc3Info();
	private @Nullable RtpPacketAc3 cachePlainPacket = null;

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param avStreamOutgoingType Class of the AvStreamOutgoing object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsAc3 Thread-specific parameters
	 */
	public ThreadRtpSenderAc3(
				Class<AVSTRIC> avStreamIncomingType,
				Class<AVSTROG> avStreamOutgoingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderAc3 paramsAc3
			) {
		super(
				avStreamIncomingType,
				avStreamOutgoingType,
				paramsCommon,
				Objects.requireNonNull(paramsAudioCommon).getAudioSamplerateHz(),
				RtpPacketType.A_AC3
			);

		//
		this.rtpTicksPerFrame = paramsAudioCommon.getRtpAudioSpf();

		//
		paramsAudioCommon.validate();
		this.paramsAudioCommon = paramsAudioCommon.clone();
		paramsAc3.validate();
		this.paramsAc3 = paramsAc3.clone();
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
	protected @NonNull ThreadDataProvBase<AudioAc3Info, AVSTROG> newThreadDataProv() {
		if (avStreamOutgoingType != AudioStreamOutgoingAc3FromFile.class) {
			throw new RuntimeException("avStreamOutgoingType must be AudioStreamOutgoingAc3FromFile");
		}
		ThreadDataProvAc3FromFile resObj = new ThreadDataProvAc3FromFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsAudioCommon,
				paramsAc3,
				Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
				10,
				paramsCommon.getDebugRewindMediaFiles()
			);
		@SuppressWarnings("unchecked")
		ThreadDataProvBase<AudioAc3Info, AVSTROG> typedProvider = (ThreadDataProvBase<AudioAc3Info, AVSTROG>)resObj;
		return typedProvider;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(curFrameAc3Info);
	}

	@Override
	protected @NonNull Boolean cbRtpPacketMarkerBitSupplier(int fragmentOffset, boolean isLastFragment) {
		/*
		 * Only set the marker bit to 1 if this is the last fragment of the AAC frame.
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

package org.tsitle.rtsp.threads.rtp.codec_a_aac;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.avdata.AudioAacInfo;
import org.tsitle.rtsp.avstreams.AudioStreamOutgoingAacFromFile;
import org.tsitle.rtsp.avstreams.AvStreamIncomingBase;
import org.tsitle.rtsp.avstreams.AvStreamIncomingFromFile;
import org.tsitle.rtsp.avstreams.AvStreamOutgoingBase;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.packets.rtp.RtpPacketContainerBase;
import org.tsitle.rtsp.packets.rtp.RtpPacketAac;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvAacFromFile;
import org.tsitle.rtsp.threads.dataprovider.ThreadDataProvBase;
import org.tsitle.rtsp.threads.rtp.FrameData;
import org.tsitle.rtsp.threads.rtp.FrameFragmentData;
import org.tsitle.rtsp.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAudioCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderAac;
import org.tsitle.rtsp.threads.rtsp.RtspConstants;

import java.util.Objects;

public final class ThreadRtpSenderAac<
			AVSTRIC extends AvStreamIncomingBase,
			AVSTROG extends AvStreamOutgoingBase<AVSTRIC>
		> extends ThreadRtpSenderBase<AudioAacInfo, AVSTRIC, AVSTROG, ThreadDataProvBase<AudioAacInfo, AVSTROG>> {

	private final ParamsThreadRtpSenderAudioCommon paramsAudioCommon;
	private final ParamsThreadRtpSenderAac paramsAac;

	private final AudioAacInfo curFrameAacInfo = new AudioAacInfo();
	/** Buffer used to store the current frame from the input stream */
	private final BufferExt cacheOrgAudioFrameBuf = new BufferExt();
	private final BufferExt cacheBufForFD = new BufferExt();

	/**
	 * Constructor.
	 * @param avStreamIncomingType Class of the AvStreamIncoming object
	 * @param avStreamOutgoingType Class of the AvStreamOutgoing object
	 * @param paramsCommon Common thread parameters
	 * @param paramsAudioCommon Common Audio thread parameters
	 * @param paramsAac Thread-specific parameters
	 */
	public ThreadRtpSenderAac(
				Class<AVSTRIC> avStreamIncomingType,
				Class<AVSTROG> avStreamOutgoingType,
				@NonNull ParamsThreadRtpSenderCommon paramsCommon,
				@NonNull ParamsThreadRtpSenderAudioCommon paramsAudioCommon,
				@NonNull ParamsThreadRtpSenderAac paramsAac
			) {
		super(
				avStreamIncomingType,
				avStreamOutgoingType,
				paramsCommon,
				Objects.requireNonNull(paramsAac).getAudioSampleRateHz(),
				RtpPacketType.A_AAC
			);

		//
		this.rtpTicksPerFrame = RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO;

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
	protected @NonNull ThreadDataProvBase<AudioAacInfo, AVSTROG> newThreadDataProv() {
		if (avStreamOutgoingType != AudioStreamOutgoingAacFromFile.class) {
			throw new RuntimeException("avStreamOutgoingType must be AudioStreamOutgoingAacFromFile");
		}
		ThreadDataProvAacFromFile resObj = new ThreadDataProvAacFromFile(
				paramsCommon.getLogMsgInterface().orElseThrow(),
				paramsAudioCommon,
				paramsAac,
				Objects.requireNonNull((AvStreamIncomingFromFile)avStreamIncomingObj),
				(int)((paramsCommon.getAvFramesPerSecond() + 0.5f) * 2.0),
				paramsCommon.getDebugRewindMediaFiles()
			);
		@SuppressWarnings("unchecked")
		ThreadDataProvBase<AudioAacInfo, AVSTROG> typedProvider = (ThreadDataProvBase<AudioAacInfo, AVSTROG>)resObj;
		return typedProvider;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	protected @NonNull FrameData cbFrameDataSupplier() {
		return defaultFrameDataSupplier(
				cacheOrgAudioFrameBuf,
				cacheBufForFD,
				curFrameAacInfo
			);
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
		return new RtpPacketAac(
				cacheParamsBase,
				(byte)curFragmentData.fragmentIndex(),
				curFrameAacInfo,
				cacheRtpInnerPayloadBuf
			);
	}

}

package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.ThreadPausableBase;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp.threads.rtp.builders.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;

import java.net.URI;
import java.util.*;

final class RtspChildThreadMng {

	static class ChildThreadsForOneStream {
		final @NonNull String subStreamId;
		final @NonNull String inputSourceId;
		final int streamSourceId;

		ThreadRtpSenderBase<?, ?, ?, ?> rtpThreadSender;

		ThreadRtcpSendRecv rtcpThreadSendRecv;
		int rtcpLastTargetCongestionLevel = -1;

		boolean srtxpInboundRekeyingInProgress = false;
		boolean srtxpOutboundRekeyingInProgress = false;

		ChildThreadsForOneStream(@NonNull String subStreamId, @NonNull String inputSourceId, int streamSourceId) {
			this.subStreamId = subStreamId;
			this.inputSourceId = inputSourceId;
			this.streamSourceId = streamSourceId;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;
	private final int clientConnectionNr;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final @NonNull RtspChildThreadsCallbackInterface rctcb;

	private final Map<@NonNull Integer, @NonNull ChildThreadsForOneStream> childThreadsForOneStreamMap = new HashMap<>();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param rtspConfig RTSP configuration
	 * @param clientConnectionNr Client connection number
	 * @param rtspSessionInfo RTSP session information
	 * @param rtxpTcpReadWrite RTxP TCP read/write interface
	 * @param rctcb Callback interface for RTSP child threads
	 */
	RtspChildThreadMng(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				int clientConnectionNr,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				@NonNull RtspChildThreadsCallbackInterface rctcb
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.clientConnectionNr = clientConnectionNr;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;
		this.rctcb = rctcb;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@NonNull Collection<@NonNull ChildThreadsForOneStream> getCtfosMapValues() {
		return childThreadsForOneStreamMap.values();
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	boolean ctfosMapContainsKey(int streamSourceId) {
		return childThreadsForOneStreamMap.containsKey(streamSourceId);
	}

	@NonNull ChildThreadsForOneStream getCtfosMapValue(int streamSourceId) {
		if (! childThreadsForOneStreamMap.containsKey(streamSourceId)) {
			throw new IllegalArgumentException("Stream Source ID not found in child threads map: " + streamSourceId);
		}
		return childThreadsForOneStreamMap.get(streamSourceId);
	}

	// -----------------------------------------------------------------------------------------------------------------

	void startChildThreads(String inputSourceId) {
		final String FNC_NAME = getClass().getSimpleName() + ".startChildThreads()";

		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspMessageType.PLAY)) {
			throw new IllegalStateException(FNC_NAME + ": No input source found (OBJ)");
		}
		if (! rtspSessionInfo.inputSourceUrlPerMtMap.containsKey(RtspMessageType.PLAY)) {
			throw new IllegalStateException(FNC_NAME + ": No input source found (URL)");
		}
		//
		String cnameHostname;
		try {
			String tmpIsUrl = rtspSessionInfo.inputSourceUrlPerMtMap.get(RtspMessageType.PLAY);
			URI tmpIsUri = HostnameHelper.convertRtspUrlIntoURI(tmpIsUrl);
			cnameHostname = tmpIsUri.getHost();
		} catch (RtspInvalidUriException e) {
			// this should never happen
			throw new RuntimeException(e);
		}
		//
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SdpSubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
			if (childThreadsForOneStreamMap.containsKey(tmpSsObj.getId())) {
				throw new IllegalStateException(FNC_NAME + ": Child thread for ss=" + tmpSsObj.getId() + " already exists");
			}
			//
			if (tmpSsObj.getIsSourceFromMq() && tmpSsObj.getCodec() == RtpPacketType.UNKNOWN) {
				logError(FNC_NAME, "ss=" + tmpSsObj.getId() + ": Source is a message queue, but codec is not set");
				continue;
			}
			//
			ChildThreadsForOneStream ctfos = new ChildThreadsForOneStream(
					tmpSubStreamId,
					inputSourceId,
					tmpSsObj.getId()
				);
			childThreadsForOneStreamMap.put(tmpSsObj.getId(), ctfos);

			startSendRtp_oneStream(ctfos, cnameHostname);
			startRtcp_oneStream(ctfos);
		}
	}

	void pauseOrStopChildThreads(boolean doPause) {
		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspMessageType.PLAY)) {
			return;
		}
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SdpSubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
			if (! childThreadsForOneStreamMap.containsKey(tmpSsObj.getId())) {
				continue;
			}
			ChildThreadsForOneStream ctfos = childThreadsForOneStreamMap.get(tmpSsObj.getId());
			if (ctfos.rtcpThreadSendRecv != null) {
				if (doPause) {
					ctfos.rtcpThreadSendRecv.pauseThread();
				} else {
					ctfos.rtcpThreadSendRecv.stopThread();  // blocks until the thread has actually stopped
				}
			}
			if (ctfos.rtpThreadSender != null) {
				if (doPause) {
					ctfos.rtpThreadSender.pauseThread();
				} else {
					ctfos.rtpThreadSender.stopThread();  // blocks until the thread has actually stopped
				}
			}
		}
	}

	void unpauseChildThreads() {
		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspMessageType.PLAY)) {
			return;
		}
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SdpSubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
			if (! childThreadsForOneStreamMap.containsKey(tmpSsObj.getId())) {
				continue;
			}
			ChildThreadsForOneStream ctfos = childThreadsForOneStreamMap.get(tmpSsObj.getId());
			if (ctfos.rtcpThreadSendRecv != null && ctfos.rtcpThreadSendRecv.isRunning()) {
				ctfos.rtcpThreadSendRecv.unpauseThread();
			}
			if (ctfos.rtpThreadSender != null && ctfos.rtpThreadSender.isPaused()) {
				ctfos.rtpThreadSender.unpauseThread();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static void stopChildThread(ThreadPausableBase thread) {
		if (thread != null) {
			if (thread.isPaused()) {
				thread.unpauseThread();
			}
			thread.stopThread();  // blocks until the thread has actually stopped
		}
	}

	private void startRtcp_oneStream(ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".startRtcp_oneStream()";

		stopChildThread(ctfos.rtcpThreadSendRecv);
		//
		RtspStaticSessionInfo.SetupSubStreamInfo tmpStreamInfo =
				RtspStaticSessionInfo.getSetupSubStreamOrThrow(FNC_NAME, ctfos.subStreamId);
		//
		BuilderThreadRtcp.Builder tmpBuilder = BuilderThreadRtcp.builder()
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.debugSessionId(rtspSessionInfo.rtspSessionId)
				.streamSourceId(Objects.requireNonNull(tmpStreamInfo.rtspStreamSource).getId())
				.rtspSsrcId(tmpStreamInfo.rtspSsrcId)
				.tpClientIpAddr(rtspSessionInfo.getClientIpAddr());
		if (tmpStreamInfo.tpIsUdp) {
			tmpBuilder
					.tpClientDestUdpPortRtcp(tmpStreamInfo.tpClientUdpPortRtcp)
					.tpSocketUdpRtcp(Objects.requireNonNull(tmpStreamInfo.tpServerUdpSocketRtcp));
		} else {
			tmpBuilder
					.tpClientDestTcpIf(this.rtxpTcpReadWrite)
					.tpClientDestTcpChannRtcp(tmpStreamInfo.tpClientTcpChannRtcp);
		}
		ctfos.rtcpThreadSendRecv = tmpBuilder
				.cryptoIsRtxpEncryptionEnabled(tmpStreamInfo.tpIsEncr)
				.cryptoKmdInboundRtcp(tmpStreamInfo.streamKmds.kmdInbound)
				.cryptoKmdOutboundRtcp(tmpStreamInfo.streamKmds.kmdOutbound)
				.cbNotifyRrPacketReceived(rctcb::cbRcvdRtcpRrPacket)
				.build();
		ctfos.rtcpThreadSendRecv.setName(
				"RTCP#c" + clientConnectionNr +
				"#sid" + rtspSessionInfo.rtspSessionId +
				"#ss" + tmpStreamInfo.rtspStreamSource.getId() +
				"#" + tmpStreamInfo.rtspStreamSource.getCodec().getValue()
			);
		ctfos.rtcpThreadSendRecv.setDaemon(false);
		ctfos.rtcpThreadSendRecv.start();

		// delete pointer to RTCP socket
		tmpStreamInfo.tpServerUdpSocketRtcp = null;
	}

	private <B extends BuilderThreadRtpSenderBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
			B buildThreadRtpSender(
					B builder,
					RtspStaticSessionInfo.SetupSubStreamInfo streamInfo,
					double avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		if (streamInfo.tpIsUdp) {
			builder
					.comTpClientDestUdpPortRtp(streamInfo.tpClientUdpPortRtp)
					.comTpSocketUdpRtp(Objects.requireNonNull(streamInfo.tpServerUdpSocketRtp));
		} else {
			builder
					.comTpClientDestTcpIf(this.rtxpTcpReadWrite)
					.comTpClientDestTcpChannRtp(streamInfo.tpClientTcpChannRtp);
		}
		return builder
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.comDebugSessionId(rtspSessionInfo.rtspSessionId)
				.comStreamSourceId(Objects.requireNonNull(streamInfo.rtspStreamSource).getId())
				.comRtspSsrcId(streamInfo.rtspSsrcId)
				.comTpClientIpAddr(rtspSessionInfo.getClientIpAddr())
				.comCryptoIsRtxpEncryptionEnabled(streamInfo.tpIsEncr)
				.comCryptoKmdOutboundRtp(streamInfo.streamKmds.kmdOutbound)
				.comDebugRewindMediaFiles(rtspConfig.getIsDebugRewindMediaFiles())
				.comIsStreamSourceFromFile(streamInfo.rtspStreamSource.getIsSourceFromFile())
				.comAvFps(avFps)
				.comRtpSeqNrT0(streamInfo.rtspRtpSeqNrT0)
				.comRtpTimestampT0(
						new ParamsThreadRtpSenderCommon.RtpTsT0(streamInfo.rtspRtpTimestampT0, streamInfo.rtspRtpGenTsT0Ns)
					)
				.comXsrcBlockEntry(xsrcBlock)
				.comCbRtcpAppendToOutgoingQueue(rctcb::cbSendRtcpPackets)
				.comCbNotifyThreadReady(rctcb::cbNotifyThreadReady)
				.comCbThreadMayStartPlayback(rctcb::cbThreadMayStartPlayback)
				.comAvStreamIncomingUri(streamInfo.rtspStreamSource.getInputUri());
	}

	private <B extends BuilderThreadRtpSenderVideoBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
			B buildThreadVideo(
					B builder,
					RtspStaticSessionInfo.SetupSubStreamInfo streamInfo,
					double avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		return buildThreadRtpSender(builder, streamInfo, avFps, xsrcBlock);
	}

	private <B extends BuilderThreadRtpSenderAudioBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
					B buildThreadAudio(
					B builder,
					RtspStaticSessionInfo.SetupSubStreamInfo streamInfo,
					@SuppressWarnings("SameParameterValue") double avFps,
					RtcpInnerXsrcBlock xsrcBlock,
					int samplesPerFrame
				) {
		Objects.requireNonNull(streamInfo.rtspStreamSource);
		return buildThreadRtpSender(builder, streamInfo, avFps, xsrcBlock)
				.audComRtpAudioSpf(samplesPerFrame)
				.audComSamplerateHz(streamInfo.rtspStreamSource.getAudioSamplerateHz());
	}

	private void startSendRtp_oneStream(ChildThreadsForOneStream ctfos, String cnameHostname) {
		final String FNC_NAME = getClass().getSimpleName() + ".startSendRtp_oneStream()";

		stopChildThread(ctfos.rtpThreadSender);
		//
		RtspStaticSessionInfo.SetupSubStreamInfo tmpStreamInfo =
				RtspStaticSessionInfo.getSetupSubStreamOrThrow(FNC_NAME, ctfos.subStreamId);
		//
		RtcpInnerXsrcBlock xsrcBlock = new RtcpInnerXsrcBlock(
				1,
				tmpStreamInfo.rtspSsrcId,
				List.of(
						new RtcpInnerXsrcBlock.BlockEntry(
								RtcpInnerXsrcBlock.BlockType.CNAME,
								rtspSessionInfo.rtspSessionId + "@" + cnameHostname
							)
					)
			);
		// sanity check
		Objects.requireNonNull(tmpStreamInfo.rtspStreamSource, FNC_NAME + ": rtspStreamSource is null");
		//
		switch (tmpStreamInfo.rtspStreamSource.getCodec()) {
			case A_AAC:
				final double tmpFrameDurAacSecs = ((double)tmpStreamInfo.rtspStreamSource.getAacSamplesPerFrame() /
						(double)tmpStreamInfo.rtspStreamSource.getAudioSamplerateHz());
				final double tmpVirtualFpsAac = (1.0 / tmpFrameDurAacSecs);
				BuilderThreadRtpSenderAac.Builder builderAac = buildThreadAudio(
						BuilderThreadRtpSenderAac.builder(),
						tmpStreamInfo,
						tmpVirtualFpsAac,
						xsrcBlock,
						tmpStreamInfo.rtspStreamSource.getAacSamplesPerFrame()
					);
				ctfos.rtpThreadSender = builderAac.build();
				break;
			case V_JPEG:
				BuilderThreadRtpSenderMjpeg.Builder builderMjpeg = buildThreadVideo(
						BuilderThreadRtpSenderMjpeg.builder(),
						tmpStreamInfo,
						tmpStreamInfo.rtspStreamSource.getVideoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = builderMjpeg.build();
				break;
			case V_H264:
				BuilderThreadRtpSenderH264.Builder builderH264 = buildThreadVideo(
						BuilderThreadRtpSenderH264.builder(),
						tmpStreamInfo,
						tmpStreamInfo.rtspStreamSource.getVideoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = builderH264.build();
				break;
			case V_H265:
				BuilderThreadRtpSenderH265.Builder builderH265 = buildThreadVideo(
						BuilderThreadRtpSenderH265.builder(),
						tmpStreamInfo,
						tmpStreamInfo.rtspStreamSource.getVideoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = builderH265.build();
				break;
			default:
				if (tmpStreamInfo.rtspStreamSource.getCodec().isPcmAudio()) {
					// the virtual FPS value only when the source is a file
					final double tmpVirtualFpsPcm = (1000.0 / (double)RtspConstants.RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS);
					//
					BuilderThreadRtpSenderPcm.Builder builderPcm = buildThreadAudio(
							BuilderThreadRtpSenderPcm.builder(),
							tmpStreamInfo,
							tmpVirtualFpsPcm,
							xsrcBlock,
							tmpStreamInfo.rtspStreamSource.getRtpAudioSamplesPerFrame(tmpVirtualFpsPcm)
						);
					ctfos.rtpThreadSender = builderPcm
							.audPcmChannelCount(tmpStreamInfo.rtspStreamSource.getAudioChannelCount())
							.audPcmBitsPerSample(tmpStreamInfo.rtspStreamSource.getCodec().getPcmAudioBitsPerSample().orElseThrow())
							.audPcmInputBigEndian(tmpStreamInfo.rtspStreamSource.getIsAudioBigEndian())
							.audPcmCodec(tmpStreamInfo.rtspStreamSource.getCodec())
							.build();
				} else {
					throw new IllegalStateException(FNC_NAME + ": Unsupported codec: " + tmpStreamInfo.rtspStreamSource.getCodec());
				}
		}
		ctfos.rtpThreadSender.setName(
				"RTP_#c" + clientConnectionNr +
				"#sid" + rtspSessionInfo.rtspSessionId +
				"#ss" + tmpStreamInfo.rtspStreamSource.getId() +
				"#" + tmpStreamInfo.rtspStreamSource.getCodec().getValue()
			);
		ctfos.rtpThreadSender.setDaemon(false);
		ctfos.rtpThreadSender.start();

		// delete pointer to RTP socket
		tmpStreamInfo.tpServerUdpSocketRtp = null;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private RtspStaticSessionInfo.@NonNull SdpSubStreamInfo getSubStreamInfo(@NonNull String subStreamId) {
		return RtspStaticSessionInfo.getSdpSubStream(rtspSessionInfo.getClientIpAddr(), subStreamId).orElseThrow();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

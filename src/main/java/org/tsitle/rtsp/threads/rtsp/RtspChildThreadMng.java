package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.ThreadPausableBase;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp.threads.rtp.RtpConstants;
import org.tsitle.rtsp.threads.rtp.ThreadRtpSenderBase;
import org.tsitle.rtsp.threads.rtp.builders.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspIdStreamSourceNotFoundException;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdInputSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdStreamSource;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfoForSubStream;

import java.net.URI;
import java.util.*;

final class RtspChildThreadMng {

	static class ChildThreadsForOneStream {
		final @NonNull RtspProtoIdSubStream idSubStream = new RtspProtoIdSubStream();
		final @NonNull RtspProtoIdInputSource idInputSource = new RtspProtoIdInputSource();
		final @NonNull RtspProtoIdStreamSource idStreamSource = new RtspProtoIdStreamSource();

		ThreadRtpSenderBase<?, ?, ?, ?> rtpThreadSender;

		ThreadRtcpSendRecv rtcpThreadSendRecv;
		int rtcpLastTargetCongestionLevel = -1;

		boolean srtxpInboundRekeyingInProgress = false;
		boolean srtxpOutboundRekeyingInProgress = false;

		ChildThreadsForOneStream(
					@NonNull RtspProtoIdSubStream idSubStream,
					@NonNull RtspProtoIdInputSource idInputSource,
					@NonNull RtspProtoIdStreamSource idStreamSource
				) {
			this.idSubStream.copyFrom(idSubStream);
			this.idSubStream.writeProtect();
			this.idInputSource.copyFrom(idInputSource);
			this.idInputSource.writeProtect();
			this.idStreamSource.copyFrom(idStreamSource);
			this.idStreamSource.writeProtect();
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
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;

	private final Map<@NonNull RtspProtoIdStreamSource, @NonNull ChildThreadsForOneStream> childThreadsForOneStreamMap = new HashMap<>();

	/** Input Source ID currently in use */
	private final @NonNull RtspProtoIdInputSource usedIdInputSource = new RtspProtoIdInputSource();

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
				@NonNull RtspChildThreadsCallbackInterface rctcb,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.clientConnectionNr = clientConnectionNr;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;
		this.rctcb = rctcb;
		this.availableStreamsInterface = availableStreamsInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@NonNull Collection<@NonNull ChildThreadsForOneStream> getCtfosMapValuesAll() {
		return childThreadsForOneStreamMap.values();
	}

	@NonNull Collection<@NonNull ChildThreadsForOneStream> getCtfosMapValuesOnlyRunning() {
		final Collection<@NonNull ChildThreadsForOneStream> resC = new ArrayList<>();
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : childThreadsForOneStreamMap.values()) {
			if (ctfos.rtpThreadSender == null || ! ctfos.rtpThreadSender.isRunning()) {
				continue;
			}
			if (ctfos.rtcpThreadSendRecv == null || ! ctfos.rtcpThreadSendRecv.isRunning()) {
				continue;
			}
			resC.add(ctfos);
		}
		return resC;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	boolean ctfosMapContainsKey(@NonNull RtspProtoIdStreamSource idStreamSource) {
		return childThreadsForOneStreamMap.containsKey(idStreamSource);
	}

	@NonNull ChildThreadsForOneStream getCtfosMapValue(@NonNull RtspProtoIdStreamSource idStreamSource) {
		if (! childThreadsForOneStreamMap.containsKey(idStreamSource)) {
			throw new IllegalArgumentException("Stream Source ID not found in child threads map: " + idStreamSource);
		}
		return childThreadsForOneStreamMap.get(idStreamSource);
	}

	// -----------------------------------------------------------------------------------------------------------------

	void startChildThreads(@NonNull RtspProtoIdInputSource idInputSource) {
		final String FNC_NAME = getClass().getSimpleName() + ".startChildThreads()";

		if (idInputSource.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": idInputSource must be set");
		}
		if (! usedIdInputSource.isEmpty()) {  // sanity check
			throw new IllegalStateException(FNC_NAME + ": An Input Source is already in use");
		}
		usedIdInputSource.copyFrom(idInputSource);
		usedIdInputSource.writeProtect();

		//
		String cnameHostname;
		try {
			String tmpIsUrl = rtspSessionInfo.getResourceUrlForMt_nonSetup(RtspMessageType.PLAY).orElseThrow().getUrlStr();
			URI tmpIsUri = HostnameHelper.convertRtspUrlIntoURI(tmpIsUrl);
			cnameHostname = tmpIsUri.getHost();
		} catch (HostnameHelperInvalidUriException e) {
			// this should never happen
			throw new RuntimeException(e);
		}
		//
		try {
			for (RtspProtoSetupInfoForSubStream tmpSiSs : rtspSessionInfo.descrSetupInfosStream.getSis()) {
				if (childThreadsForOneStreamMap.containsKey(tmpSiSs.rscUrlSubStream.idStreamSource)) {
					throw new IllegalStateException(FNC_NAME + ": Child thread for ss='" +
							tmpSiSs.rscUrlSubStream.idStreamSource.getIdStr() + "' already exists");
				}
				RtspProtoAvailableStreamsInterface.StreamSourceInfo tmpAvSsi =
						availableStreamsInterface.getStreamSourceInfo(tmpSiSs.rscUrlSubStream.idStreamSource);
				//
				if (tmpAvSsi.isSourceFromMq() && tmpAvSsi.codec() == RtpPacketType.UNKNOWN) {
					logError(FNC_NAME, "ss='" + tmpSiSs.rscUrlSubStream.idStreamSource.getIdStr() + "': " +
							"Source is a message queue, but codec is not set");
					continue;
				}
				//
				ChildThreadsForOneStream ctfos = new ChildThreadsForOneStream(
						tmpSiSs.rscUrlSubStream.idSubStream,
						usedIdInputSource,
						tmpSiSs.rscUrlSubStream.idStreamSource
					);
				childThreadsForOneStreamMap.put(tmpSiSs.rscUrlSubStream.idStreamSource, ctfos);

				startSendRtp_oneStream(ctfos, cnameHostname);
				startRtcp_oneStream(ctfos);
			}
		} catch (RtspIdStreamSourceNotFoundException e) {
			throw new IllegalStateException(FNC_NAME + ": Could not find SSI: " + e.getMessage());
		}
	}

	void pauseOrStopChildThreads(boolean doPause) {
		if (usedIdInputSource.isEmpty()) {  // sanity check
			return;
		}
		for (RtspProtoSetupInfoForSubStream tmpSiSs : rtspSessionInfo.descrSetupInfosStream.getSis()) {
			if (! childThreadsForOneStreamMap.containsKey(tmpSiSs.rscUrlSubStream.idStreamSource)) {
				continue;
			}
			ChildThreadsForOneStream ctfos = childThreadsForOneStreamMap.get(tmpSiSs.rscUrlSubStream.idStreamSource);
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
		if (usedIdInputSource.isEmpty()) {  // sanity check
			return;
		}
		for (RtspProtoSetupInfoForSubStream tmpSiSs : rtspSessionInfo.descrSetupInfosStream.getSis()) {
			if (! childThreadsForOneStreamMap.containsKey(tmpSiSs.rscUrlSubStream.idStreamSource)) {
				continue;
			}
			ChildThreadsForOneStream ctfos = childThreadsForOneStreamMap.get(tmpSiSs.rscUrlSubStream.idStreamSource);
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

	private @NonNull RtspProtoIpAddr getClientIpAddr() {
		if (rtspSessionInfo.clientIpAddr.isEmpty()) {
			throw new IllegalStateException("Client IP address is not set");
		}
		return rtspSessionInfo.clientIpAddr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void stopChildThread(@Nullable ThreadPausableBase thread) {
		if (thread != null) {
			if (thread.isPaused()) {
				thread.unpauseThread();
			}
			thread.stopThread();  // blocks until the thread has actually stopped
		}
	}

	private void startRtcp_oneStream(@NonNull ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".startRtcp_oneStream()";

		stopChildThread(ctfos.rtcpThreadSendRecv);
		//
		RtspProtoSetupInfoForSubStream tmpSiSs = rtspSessionInfo.descrSetupInfosStream
				.getSiBySubStreamId(ctfos.idSubStream).orElseThrow();
		//
		RtspProtoAvailableStreamsInterface.StreamSourceInfo tmpAvSsi;
		try {
			tmpAvSsi = availableStreamsInterface.getStreamSourceInfo(tmpSiSs.rscUrlSubStream.idStreamSource);
		} catch (RtspIdStreamSourceNotFoundException e) {
			throw new IllegalStateException(FNC_NAME + ": idStreamSource not found");
		}
		//
		BuilderThreadRtcp.Builder tmpBuilder = BuilderThreadRtcp.builder()
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.debugSessionId(rtspSessionInfo.idSession)
				.idStreamSource(tmpSiSs.rscUrlSubStream.idStreamSource)
				.rtspSsrcId(tmpSiSs.rtspSsrcId)
				.tpClientIpAddr(getClientIpAddr());
		if (tmpSiSs.subStreamTp.getIsUdp()) {
			tmpBuilder
					.tpClientDestUdpPortRtcp(tmpSiSs.subStreamTp.tpClientUdpPortRtcp)
					.tpSocketUdpRtcp(Objects.requireNonNull(tmpSiSs.tpServerUdpSocketRtcp));
		} else {
			tmpBuilder
					.tpClientDestTcpIf(rtxpTcpReadWrite)
					.tpClientDestTcpChannRtcp(tmpSiSs.subStreamTp.tpClientTcpChannRtcp);
		}
		ctfos.rtcpThreadSendRecv = tmpBuilder
				.cryptoIsRtxpEncryptionEnabled(tmpSiSs.subStreamTp.getIsEncr())
				.cryptoKmdInboundRtcp(tmpSiSs.kmdInboundCur.getKmd().orElse(null))
				.cryptoKmdOutboundRtcp(tmpSiSs.kmdOutbound.getKmd().orElse(null))
				.cbNotifyRrPacketReceived(rctcb::cbRcvdRtcpRrPacket)
				.build();
		ctfos.rtcpThreadSendRecv.setName(
				"RTCP#c" + clientConnectionNr +
				"#sid" + rtspSessionInfo.idSession.getIdStr() +
				"#ss" + tmpSiSs.rscUrlSubStream.idStreamSource.getIdStr() +
				"#" + tmpAvSsi.codec().getValue()
			);
		ctfos.rtcpThreadSendRecv.setDaemon(false);
		ctfos.rtcpThreadSendRecv.start();
	}

	private <B extends BuilderThreadRtpSenderBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
			B buildThreadRtpSender(
					@NonNull B builder,
					@NonNull RtspProtoSetupInfoForSubStream streamInfo,
					RtspProtoAvailableStreamsInterface.@NonNull StreamSourceInfo avSsi,
					double avFps,
					@NonNull RtcpInnerXsrcBlock xsrcBlock
				) {
		if (streamInfo.subStreamTp.getIsUdp()) {
			builder
					.comTpClientDestUdpPortRtp(streamInfo.subStreamTp.tpClientUdpPortRtp)
					.comTpSocketUdpRtp(Objects.requireNonNull(streamInfo.tpServerUdpSocketRtp));
		} else {
			builder
					.comTpClientDestTcpIf(rtxpTcpReadWrite)
					.comTpClientDestTcpChannRtp(streamInfo.subStreamTp.tpClientTcpChannRtp);
		}
		return builder
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.comDebugSessionId(rtspSessionInfo.idSession)
				.comIdStreamSource(streamInfo.rscUrlSubStream.idStreamSource)
				.comRtspSsrcId(streamInfo.rtspSsrcId)
				.comTpClientIpAddr(getClientIpAddr())
				.comCryptoIsRtxpEncryptionEnabled(streamInfo.subStreamTp.getIsEncr())
				.comCryptoKmdOutboundRtp(streamInfo.kmdOutbound.getKmd().orElse(null))
				.comDebugRewindMediaFiles(rtspConfig.getIsDebugRewindMediaFiles())
				.comIsStreamSourceFromFile(avSsi.isSourceFromFile())
				.comAvFps(avFps)
				.comRtpSeqNrT0(streamInfo.rtspRtpSeqNrT0)
				.comRtpTimestampT0(
						new ParamsThreadRtpSenderCommon.RtpTsT0(streamInfo.rtspRtpTimestampT0, streamInfo.rtspRtpGenTsT0Ns)
					)
				.comXsrcBlockEntry(xsrcBlock)
				.comCbRtcpAppendToOutgoingQueue(rctcb::cbSendRtcpPackets)
				.comCbNotifyThreadReady(rctcb::cbNotifyThreadReady)
				.comCbThreadMayStartPlayback(rctcb::cbThreadMayStartPlayback)
				.comAvStreamIncomingUri(avSsi.inputUri());
	}

	private <B extends BuilderThreadRtpSenderVideoBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
			B buildThreadVideo(
					@NonNull B builder,
					@NonNull RtspProtoSetupInfoForSubStream streamInfo,
					RtspProtoAvailableStreamsInterface.@NonNull StreamSourceInfo avSsi,
					double avFps,
					@NonNull RtcpInnerXsrcBlock xsrcBlock
				) {
		return buildThreadRtpSender(builder, streamInfo, avSsi, avFps, xsrcBlock);
	}

	private <B extends BuilderThreadRtpSenderAudioBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
			B buildThreadAudio(
					@NonNull B builder,
					@NonNull RtspProtoSetupInfoForSubStream streamInfo,
					RtspProtoAvailableStreamsInterface.@NonNull StreamSourceInfo avSsi,
					@SuppressWarnings("SameParameterValue") double avFps,
					@NonNull RtcpInnerXsrcBlock xsrcBlock,
					int samplesPerFrame
				) {
		return buildThreadRtpSender(builder, streamInfo, avSsi, avFps, xsrcBlock)
				.audComRtpAudioSpf(samplesPerFrame)
				.audComSamplerateHz(avSsi.audioSampleRateHz());
	}

	private void startSendRtp_oneStream(@NonNull ChildThreadsForOneStream ctfos, @NonNull String cnameHostname)
			throws RtspIdStreamSourceNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".startSendRtp_oneStream()";

		stopChildThread(ctfos.rtpThreadSender);
		//
		RtspProtoSetupInfoForSubStream tmpSiSs = rtspSessionInfo.descrSetupInfosStream
				.getSiBySubStreamId(ctfos.idSubStream).orElseThrow();
		//
		RtcpInnerXsrcBlock xsrcBlock = new RtcpInnerXsrcBlock(
				1,
				tmpSiSs.rtspSsrcId,
				List.of(
						new RtcpInnerXsrcBlock.BlockEntry(
								RtcpInnerXsrcBlock.BlockType.CNAME,
								rtspSessionInfo.idSession.getIdStr() + "@" + cnameHostname
							)
					)
			);
		// sanity check
		if (tmpSiSs.rscUrlSubStream.idStreamSource.isEmpty()) {
			throw new IllegalStateException(FNC_NAME + ": idStreamSource is empty");
		}
		//
		RtspProtoAvailableStreamsInterface.StreamSourceInfo tmpAvSsi;
		try {
			tmpAvSsi = availableStreamsInterface.getStreamSourceInfo(tmpSiSs.rscUrlSubStream.idStreamSource);
		} catch (RtspIdStreamSourceNotFoundException e) {
			throw new IllegalStateException(FNC_NAME + ": idStreamSource not found");
		}
		//
		switch (tmpAvSsi.codec()) {
			case A_AAC:
				final double tmpFrameDurAacSecs = ((double)tmpAvSsi.audioAacSpf() / (double)tmpAvSsi.audioSampleRateHz());
				final double tmpVirtualFpsAac = (1.0 / tmpFrameDurAacSecs);
				BuilderThreadRtpSenderAac.Builder builderAac = buildThreadAudio(
						BuilderThreadRtpSenderAac.builder(),
						tmpSiSs,
						tmpAvSsi,
						tmpVirtualFpsAac,
						xsrcBlock,
						tmpAvSsi.audioAacSpf()
					);
				ctfos.rtpThreadSender = builderAac.build();
				break;
			case V_JPEG:
				BuilderThreadRtpSenderMjpeg.Builder builderMjpeg = buildThreadVideo(
						BuilderThreadRtpSenderMjpeg.builder(),
						tmpSiSs,
						tmpAvSsi,
						tmpAvSsi.videoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = builderMjpeg.build();
				break;
			case V_H264:
				BuilderThreadRtpSenderH264.Builder builderH264 = buildThreadVideo(
						BuilderThreadRtpSenderH264.builder(),
						tmpSiSs,
						tmpAvSsi,
						tmpAvSsi.videoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = builderH264.build();
				break;
			case V_H265:
				BuilderThreadRtpSenderH265.Builder builderH265 = buildThreadVideo(
						BuilderThreadRtpSenderH265.builder(),
						tmpSiSs,
						tmpAvSsi,
						tmpAvSsi.videoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = builderH265.build();
				break;
			default:
				if (tmpAvSsi.codec().isPcmAudio()) {
					// the virtual FPS value only when the source is a file
					final double tmpVirtualFpsPcm = (1000.0 / (double) RtpConstants.RTP_SEND_INTERVAL_PCM_AUDIO_FROM_FILE_MS);
					//
					BuilderThreadRtpSenderPcm.Builder builderPcm = buildThreadAudio(
							BuilderThreadRtpSenderPcm.builder(),
							tmpSiSs,
							tmpAvSsi,
							tmpVirtualFpsPcm,
							xsrcBlock,
							availableStreamsInterface.getStreamSourceRtpAudioSamplesPerFrame(
									tmpSiSs.rscUrlSubStream.idStreamSource,
									tmpVirtualFpsPcm
								)
						);
					ctfos.rtpThreadSender = builderPcm
							.audPcmChannelCount(tmpAvSsi.audioChannelCount())
							.audPcmBitsPerSample(tmpAvSsi.codec().getPcmAudioBitsPerSample().orElseThrow())
							.audPcmInputBigEndian(tmpAvSsi.isAudioBigEndian())
							.audPcmCodec(tmpAvSsi.codec())
							.build();
				} else {
					throw new IllegalStateException(FNC_NAME + ": Unsupported codec: " + tmpAvSsi.codec());
				}
		}
		ctfos.rtpThreadSender.setName(
				"RTP_#c" + clientConnectionNr +
				"#sid" + rtspSessionInfo.idSession.getIdStr() +
				"#ss" + tmpSiSs.rscUrlSubStream.idStreamSource.getIdStr() +
				"#" + tmpAvSsi.codec().getValue()
			);
		ctfos.rtpThreadSender.setDaemon(false);
		ctfos.rtpThreadSender.start();
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

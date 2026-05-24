package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.CancelToken;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.*;
import org.tsitle.rtsp.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.builders.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class ThreadRtspServer extends RunnableBase {

	private static class ChildThreadsForOneStream {
		final @NonNull String subStreamId;
		final @NonNull String inputSourceId;
		final int streamSourceId;

		ThreadRtpSenderBase<?, ?, ?, ?> rtpThreadSender;

		ThreadRtcpSendRecv rtcpThreadSendRecv;
		int rtcpLastTargetCongestionLevel = -1;

		ChildThreadsForOneStream(@NonNull String subStreamId, @NonNull String inputSourceId, int streamSourceId) {
			this.subStreamId = subStreamId;
			this.inputSourceId = inputSourceId;
			this.streamSourceId = streamSourceId;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** RTSP session timeout tolerance in seconds. Sometimes even compliant clients fail to send a keep-alive message in time. */
	private static final int SESSION_TIMEOUT_TOLERANCE_SEC = 15;

	private final String threadName;
	private final int clientConnectionNr;

	/** Client IP address */
	private final InetAddress clientIpAddr;

	private final RtspConfig rtspConfig;
	private final RtxpTcpReadWrite rtxpTcpReadWrite;
	private final RtspSessionInfo rtspSessionInfo = new RtspSessionInfo();
	private final RtspRequestParser rtspRequestParser;
	private final RtspResponseBuilder rtspResponseBuilder;

	private @Nullable Instant rtspTimeoutLastRequ = null;

	private final Map<Integer, ChildThreadsForOneStream> childThreadsForOneStreamMap = new HashMap<>();
	private final Map<Integer, ChildThreadsForOneStream> childThreadsPerSsrcMap = new HashMap<>();

	private final RtspRequAuthSvc rtspRequAuthSvc;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param rtspConfig RTSP configuration
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 * @param isRtspsConnection True if the RTSP connection is over TLS/SSL
	 */
	public ThreadRtspServer(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspConfig rtspConfig,
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp,
				boolean isRtspsConnection
			) {
		super(logMsgInterface, cancelToken);

		//
		this.threadName = "RTSP#c" + clientConnectionNr;
		this.clientConnectionNr = clientConnectionNr;
		this.clientIpAddr = rtspSocketTcp.getInetAddress();

		this.rtspConfig = rtspConfig;
		this.rtxpTcpReadWrite = new RtxpTcpReadWrite(logMsgInterface, rtspSocketTcp, rtspConfig.getIsDebugPrintRtspSent());

		//
		this.rtspSessionInfo.clientIpAddr = clientIpAddr;
		this.rtspSessionInfo.isRtspsConnection = isRtspsConnection;

		//
		this.rtspRequestParser = new RtspRequestParser(logMsgInterface, this.rtxpTcpReadWrite, rtspConfig, rtspSessionInfo);
		this.rtspResponseBuilder = new RtspResponseBuilder(logMsgInterface, this.rtxpTcpReadWrite, rtspConfig, rtspSessionInfo);

		//
		this.rtspRequAuthSvc = new RtspRequAuthSvc(logMsgInterface, rtspConfig, rtspSessionInfo);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		Thread.currentThread().setName(threadName);

		//
		isRunning.set(true);
		logInfo(FNC_NAME, String.format("Serving RTSP%s to %s:%d",
				rtspSessionInfo.isRtspsConnection ? "S" : "",
				clientIpAddr.getHostAddress(), rtxpTcpReadWrite.getSocketRemotePort()));

		//
		rtspTimeoutLastRequ = Instant.now();

		//
		try {
			int loopCounter = 0;
			while (! (hasBeenRequestedToStop() || rtxpTcpReadWrite.isSocketClosed())) {
				if (! mainLoop(++loopCounter)) {
					break;
				}
			}
		} catch (TcpSocketClosedException e) {
			logDebug(FNC_NAME, "TcpSocketClosedException: " + e.getMessage());
		} catch (TcpSocketIoException e) {
			logDebug(FNC_NAME, "TcpSocketIoException: " + e.getMessage());
		} catch (UdpSocketIoException e) {
			logError(FNC_NAME, "UdpSocketIoException: " + e.getMessage());
		} catch (InterruptedException e2) {
			logError(FNC_NAME, "InterruptedException");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception: " + e.getMessage());
		} finally {
			logInfo(FNC_NAME, String.format("Closing RTSP%s for %s:%d",
					rtspSessionInfo.isRtspsConnection ? "S" : "",
					clientIpAddr.getHostAddress(), rtxpTcpReadWrite.getSocketRemotePort()));
			//
			logDebug(FNC_NAME, "stopping thread");
			// stop sending/receiving RTP/RTCP packets
			pauseOrStopChildThreads(false);
			// close RTSP client socket and stream reader/writer
			rtxpTcpReadWrite.closeSocket();
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
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
		RtspStaticSessionInfo.StreamInfo tmpStreamInfo =
				RtspStaticSessionInfo.getStreamInfoOrThrow(FNC_NAME, ctfos.subStreamId);
		//
		BuilderThreadRtcp.Builder tmpBuilder = BuilderThreadRtcp.builder()
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.debugSessionId(rtspSessionInfo.rtspSessionId)
				.streamSourceId(Objects.requireNonNull(tmpStreamInfo.rtspStreamSource).getId())
				.rtspSsrcId(tmpStreamInfo.rtspSsrcId)
				.tpClientIpAddr(clientIpAddr);
		if (tmpStreamInfo.tpIsUdp) {
			tmpBuilder
					.tpClientDestUdpPortRtcp(tmpStreamInfo.tpClientDestUdpPortRtcp)
					.tpSocketUdpRtcp(Objects.requireNonNull(tmpStreamInfo.tpServerUdpSocketRtcp));
		} else {
			tmpBuilder
					.tpClientDestTcpIf(this.rtxpTcpReadWrite)
					.tpClientDestTcpChannRtcp(tmpStreamInfo.tpClientDestTcpChannRtcp);
		}
		ctfos.rtcpThreadSendRecv = tmpBuilder
				.cryptoIsRtxpEncryptionEnabled(tmpStreamInfo.tpIsEncr)
				.cryptoKmdInboundRtcp(Objects.requireNonNull(tmpStreamInfo.streamKmds.kmdInbound))
				.cryptoKmdOutboundRtcp(Objects.requireNonNull(tmpStreamInfo.streamKmds.kmdOutbound))
				.cbNotifyRrPacketReceived(this::cbRcvdRtcpRrPacket)
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
					RtspStaticSessionInfo.StreamInfo streamInfo,
					double avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		if (streamInfo.tpIsUdp) {
			builder
					.comTpClientDestUdpPortRtp(streamInfo.tpClientDestUdpPortRtp)
					.comTpSocketUdpRtp(Objects.requireNonNull(streamInfo.tpServerSrcUdpSocketRtp));
		} else {
			builder
					.comTpClientDestTcpIf(this.rtxpTcpReadWrite)
					.comTpClientDestTcpChannRtp(streamInfo.tpClientDestTcpChannRtp);
		}
		return builder
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.comDebugSessionId(rtspSessionInfo.rtspSessionId)
				.comStreamSourceId(Objects.requireNonNull(streamInfo.rtspStreamSource).getId())
				.comRtspSsrcId(streamInfo.rtspSsrcId)
				.comTpClientIpAddr(clientIpAddr)
				.comCryptoIsRtxpEncryptionEnabled(streamInfo.tpIsEncr)
				.comCryptoKmdOutboundRtp(Objects.requireNonNull(streamInfo.streamKmds.kmdOutbound))
				.comDebugRewindMediaFiles(rtspConfig.getIsDebugRewindMediaFiles())
				.comIsStreamSourceFromFile(streamInfo.rtspStreamSource.getIsSourceFromFile())
				.comAvFps(avFps)
				.comRtpSeqNrT0(streamInfo.rtspRtpSeqNrT0)
				.comRtpTimestampT0(
						new ParamsThreadRtpSenderCommon.RtpTsT0(streamInfo.rtspRtpTimestampT0, streamInfo.rtspRtpGenTsT0Ns)
					)
				.comXsrcBlockEntry(xsrcBlock)
				.comCbRtcpAppendToOutgoingQueue(this::cbSendRtcpPackets)
				.comCbNotifyThreadReady(this::cbNotifyThreadReady)
				.comCbThreadMayStartPlayback(this::cbThreadMayStartPlayback)
				.comAvStreamIncomingUri(streamInfo.rtspStreamSource.getInputUri());
	}

	private <B extends BuilderThreadRtpSenderVideoBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
			B buildThreadVideo(
					B builder,
					RtspStaticSessionInfo.StreamInfo streamInfo,
					double avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		return buildThreadRtpSender(builder, streamInfo, avFps, xsrcBlock);
	}

	private <B extends BuilderThreadRtpSenderAudioBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
					B buildThreadAudio(
					B builder,
					RtspStaticSessionInfo.StreamInfo streamInfo,
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
		RtspStaticSessionInfo.StreamInfo tmpStreamInfo =
				RtspStaticSessionInfo.getStreamInfoOrThrow(FNC_NAME, ctfos.subStreamId);
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
		tmpStreamInfo.tpServerSrcUdpSocketRtp = null;
	}

	private void startChildThreads(String inputSourceId) {
		final String FNC_NAME = getClass().getSimpleName() + ".startChildThreads()";

		if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			throw new IllegalStateException(FNC_NAME + ": No input source found (OBJ)");
		}
		if (! rtspSessionInfo.inputSourceUrlPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			throw new IllegalStateException(FNC_NAME + ": No input source found (URL)");
		}
		//
		String cnameHostname;
		try {
			String tmpIsUrl = rtspSessionInfo.inputSourceUrlPerSmtMap.get(ServerMessageType.PLAY);
			URI tmpIsUri = HostnameHelper.convertRtspUrlIntoURI(tmpIsUrl);
			cnameHostname = tmpIsUri.getHost();
		} catch (RtspInvalidUriException e) {
			// this should never happen
			throw new RuntimeException(e);
		}
		//
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
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

	private void pauseOrStopChildThreads(boolean doPause) {
		if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			return;
		}
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
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

	private void unpauseChildThreads() {
		if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			return;
		}
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
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

	private synchronized void cbSendRtcpPackets(int ssrcId, BufferExt rtcpPacketsBuf) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbSendRtcpPackets()";

		ChildThreadsForOneStream ctfosToUse = null;
		if (childThreadsPerSsrcMap.containsKey(ssrcId)) {
			ctfosToUse = childThreadsPerSsrcMap.get(ssrcId);
		} else {
			if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
				throw new IllegalStateException(FNC_NAME + ": No input source found");
			}
			for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
				RtspStaticSessionInfo.SubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
				RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
				if (! childThreadsForOneStreamMap.containsKey(tmpSsObj.getId())) {
					continue;
				}
				RtspStaticSessionInfo.StreamInfo tmpStreamInfo =
						RtspStaticSessionInfo.getStreamInfoOrThrow(FNC_NAME, tmpSubStreamId);
				if (tmpStreamInfo.rtspSsrcId != ssrcId) {
					continue;
				}
				ctfosToUse = childThreadsForOneStreamMap.get(tmpSsObj.getId());
				childThreadsPerSsrcMap.put(ssrcId, ctfosToUse);
				break;
			}
		}
		if (ctfosToUse == null) {
			throw new IllegalStateException(FNC_NAME + ": No stream found for ssrcId: " + ssrcId);
		}
		if (ctfosToUse.rtcpThreadSendRecv != null &&
				! ctfosToUse.rtcpThreadSendRecv.hasBeenRequestedToStop() &&
				ctfosToUse.rtcpThreadSendRecv.isRunning()) {
			ctfosToUse.rtcpThreadSendRecv.appendToSendQueue(rtcpPacketsBuf);
		}
	}

	private synchronized void cbRcvdRtcpRrPacket(@NonNull Instant time) {
		rtxpTcpReadWrite.resetTcpActivityTimeoutTimer();
		rtspTimeoutLastRequ = Instant.now();
	}

	private synchronized void cbNotifyThreadReady(Integer streamSourceId) {
		rtspSessionInfo.threadReadyStates.put(streamSourceId, true);
	}

	private synchronized Boolean cbThreadMayStartPlayback() {
		if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			return false;
		}
		boolean areAllReady = true;
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
			if (! rtspSessionInfo.threadReadyStates.getOrDefault(tmpSsi.streamSourceId(), false)) {
				areAllReady = false;
				break;
			}
		}
		return areAllReady;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void updateCongestionLevel_oneStream(ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateCongestionLevel_oneStream()";

		if (ctfos.rtpThreadSender == null || ctfos.rtcpThreadSendRecv == null ||
				ctfos.rtpThreadSender.isPaused() || ctfos.rtcpThreadSendRecv.isPaused()) {
			return;
		}
		int currentTcl = ctfos.rtcpThreadSendRecv.getTargetCongestionLevel();
		if (currentTcl == ctfos.rtcpLastTargetCongestionLevel) {
			return;
		}
		if (ctfos.rtcpLastTargetCongestionLevel >= 0) {
			logDebug(FNC_NAME, "ss=" + ctfos.streamSourceId + ": Congestion level changed to: " + currentTcl);
			ctfos.rtpThreadSender.notifyCongestionLevelChange(currentTcl);
		}
		ctfos.rtcpLastTargetCongestionLevel = currentTcl;
	}

	private void updateCongestionLevel() {
		if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			return;
		}
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
			if (! childThreadsForOneStreamMap.containsKey(tmpSsObj.getId())) {
				continue;
			}
			ChildThreadsForOneStream ctfos = childThreadsForOneStreamMap.get(tmpSsObj.getId());
			updateCongestionLevel_oneStream(ctfos);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private Optional<RequestBasicInfo> getNextRequest()
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		RequestBasicInfo requestBasicInfo = rtspRequestParser.parseRequest();  // blocks for setSoTimeout() value
		if (! requestBasicInfo.isValid()) {
			logError(FNC_NAME, String.format("Received invalid RTSP request (rt=%s), rejecting it with code %s (CSeq=%d)",
					requestBasicInfo.serverMessageType,
					requestBasicInfo.statusCode, rtspSessionInfo.rtspSeqNrLastRcvd));
			rtspResponseBuilder.sendResponse(requestBasicInfo);
			return Optional.empty();
		}
		logDebug(FNC_NAME, String.format("Received %s request (CSeq=%d)",
				requestBasicInfo.serverMessageType, rtspSessionInfo.rtspSeqNrLastRcvd));
		return Optional.of(requestBasicInfo);
	}

	private boolean handleSuccessfulRequest(@NonNull RequestBasicInfo requestBasicInfo)
			throws TcpSocketClosedException, TcpSocketIoException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleSuccessfulRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		SessionState nextState = rtspSessionInfo.sessionState;

		// check whether the request is allowed in the current RTSP state
		checkRequestTypeVsState(requestBasicInfo);
		if (requestBasicInfo.statusCode != ServerResponseStatusCode.OK) {
			rtspResponseBuilder.sendResponse(requestBasicInfo);
			return false;
		}

		// check whether the client needs to be authenticated and if so, whether he actually is
		rtspRequAuthSvc.checkAuthorization(requestBasicInfo);
		if (requestBasicInfo.statusCode != ServerResponseStatusCode.OK) {
			rtspResponseBuilder.sendResponse(requestBasicInfo);
			// keep the connection open if only the authentication failed
			return (requestBasicInfo.statusCode == ServerResponseStatusCode.UNAUTHORIZED);
		}

		//
		rtspResponseBuilder.sendResponse(requestBasicInfo);

		//
		Objects.requireNonNull(
				requestBasicInfo.requestUrlInputOrStreamSource,
				FNC_NAME + ": requestBasicInfo.requestUrlInputOrStreamSource is null"
			);

		//
		switch (requestBasicInfo.serverMessageType) {
			case ServerMessageType.SETUP:
				rtspSessionInfo.isPlaybackPaused = false;
				//
				Objects.requireNonNull(
						requestBasicInfo.requestUrlInputOrStreamSource.subStreamId,
						FNC_NAME + ": requestBasicInfo.requestUrlInputOrStreamSource.subStreamId is null"
					);
				final String tmpSubStreamId = requestBasicInfo.requestUrlInputOrStreamSource.subStreamId;
				// sanity check
				if (! RtspStaticSessionInfo.existsStreamInfo(tmpSubStreamId)) {
					// this should never happen
					logError(FNC_NAME, "SETUP failed");
					return false;
				}
				RtspStaticSessionInfo.StreamInfo tmpStreamInfo =
						RtspStaticSessionInfo.getStreamInfoOrThrow(FNC_NAME, tmpSubStreamId);
				try {
					tmpStreamInfo.isTransportValid(
							rtspSessionInfo.isRtpRtcpEncryptionRequired,
							rtspSessionInfo.forceRtpRtcpEncryption,
							rtspSessionInfo.isRtspsConnection,
							rtspConfig.getIsDebugDisableTransportUdp()
						);
				} catch (Exception e) {
					// this should never happen
					logError(FNC_NAME, "SETUP failed: " + e.getMessage());
					return false;
				}
				nextState = SessionState.READY;
				break;
			case ServerMessageType.PLAY:
				final String tmpIsIdPlay = requestBasicInfo.requestUrlInputOrStreamSource.inputSourceId;
				logInfo(FNC_NAME, String.format(
						"%s playback for IS='%s' (w/%s SRTP, %s, w/%s SSL)",
						rtspSessionInfo.isPlaybackPaused ? "Resuming" : "Starting",
						tmpIsIdPlay,
						rtspSessionInfo.isTransportSrtpSrtcp ? "" : "o",
						rtspSessionInfo.isTransportUdp ? "UDP" : "TCP",
						rtspSessionInfo.isRtspsConnection ? "" : "o"));
				//
				rtxpTcpReadWrite.setIsRtpRtcpAllowed(! rtspSessionInfo.isTransportUdp);
				if (rtspSessionInfo.isTransportUdp) {
					rtxpTcpReadWrite.setTcpActivityTimeoutForRtspOnly();
				} else {
					rtxpTcpReadWrite.setTcpActivityTimeoutForRtxp();
				}
				if (rtspSessionInfo.isPlaybackPaused) {
					rtspSessionInfo.isPlaybackPaused = false;
					unpauseChildThreads();
				} else {
					startChildThreads(tmpIsIdPlay);
				}
				nextState = SessionState.PLAYING;
				break;
			case ServerMessageType.PAUSE:
				final String tmpIsIdPause = requestBasicInfo.requestUrlInputOrStreamSource.inputSourceId;
				logInfo(FNC_NAME, String.format("Pausing playback for IS='%s'", tmpIsIdPause));
				//
				rtxpTcpReadWrite.setTcpActivityTimeoutForRtspOnly();
				pauseOrStopChildThreads(true);
				nextState = SessionState.READY;
				rtspSessionInfo.isPlaybackPaused = true;
				break;
			case ServerMessageType.TEARDOWN:
				nextState = SessionState.INIT;
				rtspSessionInfo.isPlaybackPaused = false;
				break;
		}

		//
		if (rtspSessionInfo.sessionState != nextState) {
			rtspSessionInfo.sessionState = nextState;
			if (rtspSessionInfo.sessionState == SessionState.INIT) {
				return false;  // tear down the session
			}
			logDebug(FNC_NAME, "RTSP state is now " + nextState);
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void checkRequestTypeVsState(@NonNull RequestBasicInfo requestBasicInfo) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkRequestTypeVsState()";

		if (requestBasicInfo.serverMessageType == ServerMessageType.OPTIONS ||
				requestBasicInfo.serverMessageType == ServerMessageType.DESCRIBE ||
				requestBasicInfo.serverMessageType == ServerMessageType.SETUP) {
			return;
		}

		boolean wasOk = false;
		switch (rtspSessionInfo.sessionState) {
			case READY:
				if (requestBasicInfo.serverMessageType == ServerMessageType.PLAY ||
						requestBasicInfo.serverMessageType == ServerMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
			case PLAYING:
				if (requestBasicInfo.serverMessageType == ServerMessageType.PAUSE ||
						requestBasicInfo.serverMessageType == ServerMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
		}

		if (! wasOk) {
			requestBasicInfo.statusCode = ServerResponseStatusCode.BAD_REQUEST;
			logWarn(FNC_NAME, String.format("Request %s not valid for current RTSP state %s, rejecting it with code %s",
					requestBasicInfo.serverMessageType,
					rtspSessionInfo.sessionState, requestBasicInfo.statusCode));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private RtspStaticSessionInfo.@NonNull SubStreamInfo getSubStreamInfo(@NonNull String subStreamId) {
		Objects.requireNonNull(rtspSessionInfo.clientIpAddr, "rtspSessionInfo.clientIpAddr is null");

		return RtspStaticSessionInfo.getSubStreamInfo(rtspSessionInfo.clientIpAddr, subStreamId).orElseThrow();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop(final int loopCounter)
			throws TcpSocketClosedException, TcpSocketIoException, UdpSocketIoException, InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (rtspSessionInfo.isTransportUdp && rtspTimeoutLastRequ != null) {
			long tmpTimeDiff = Duration.between(rtspTimeoutLastRequ, Instant.now()).toSeconds();
			if (tmpTimeDiff > RtspConstants.RTSP_SESSION_TIMEOUT + SESSION_TIMEOUT_TOLERANCE_SEC) {
				logError(FNC_NAME, "RTSP session timeout after " + tmpTimeDiff + " seconds");
				// send a BYE packet per stream
				for (ChildThreadsForOneStream ctfos : childThreadsForOneStreamMap.values()) {
					if (ctfos.rtcpThreadSendRecv == null || ! ctfos.rtcpThreadSendRecv.isRunning()) {
						continue;
					}
					ctfos.rtcpThreadSendRecv.appendByePacketToSendQueue();
				}
				return false;
			}
		}

		//
		if (loopCounter % 50 == 0) {
			updateCongestionLevel();
		}

		//
		try {
			Optional<RequestBasicInfo> optRequestBasicInfo = getNextRequest();
			rtspTimeoutLastRequ = Instant.now();
			if (optRequestBasicInfo.isEmpty()) {
				return false;
			}
			return handleSuccessfulRequest(optRequestBasicInfo.get());
		} catch (InputStreamNotReadyException e1) {
			Thread.sleep(15);
			return true;
		}
	}

}

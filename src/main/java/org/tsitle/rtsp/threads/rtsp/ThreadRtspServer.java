package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.InputStreamNotReadyException;
import org.tsitle.rtsp.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.helpers.CancelToken;
import org.tsitle.rtsp.helpers.HashMd5Helper;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.packets.rtp.RtpPacketType;
import org.tsitle.rtsp.threads.*;
import org.tsitle.rtsp.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.builders.*;
import org.tsitle.rtsp.threads.rtp.params.ParamsThreadRtpSenderCommon;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class ThreadRtspServer extends RunnableBase {

	private static class ChildThreadsForOneStream {
		final int streamSourceId;
		final String inputSourceId;

		ThreadRtpSenderBase<?, ?, ?, ?> rtpThreadSender;

		ThreadRtcpSendRecv rtcpThreadSendRecv;
		int rtcpLastTargetCongestionLevel = -1;

		ChildThreadsForOneStream(int streamSourceId, String inputSourceId) {
			this.streamSourceId = streamSourceId;
			this.inputSourceId = inputSourceId;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final int SESSION_TIMEOUT_TOLERANCE_SEC = 15;

	private final String threadName;
	private final int clientConnectionNr;

	/** Client IP address */
	private final InetAddress clientIpAddr;

	private final RtspConfig rtspConfig;
	/** TCP socket used to send/receive RTSP messages */
	private final Socket rtspSocketTcp;
	private final BufferedReader rtspBufferedReader;
	private final BufferedWriter rtspBufferedWriter;
	private final RtspSessionInfo rtspSessionInfo = new RtspSessionInfo();
	private final RtspRequestParser rtspRequestParser;
	private final RtspResponseBuilder rtspResponseBuilder;

	private @Nullable Instant rtspTimeoutLastRequ = null;

	private final Map<Integer, ChildThreadsForOneStream> childThreadsForOneStreamMap = new HashMap<>();
	private final Map<Integer, ChildThreadsForOneStream> childThreadsPerSsrcMap = new HashMap<>();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param rtspConfig RTSP configuration
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 */
	public ThreadRtspServer(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspConfig rtspConfig,
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp
			) {
		super(logMsgInterface, cancelToken);

		//
		this.threadName = "RTSP#c" + clientConnectionNr;
		this.clientConnectionNr = clientConnectionNr;
		this.clientIpAddr = rtspSocketTcp.getInetAddress();

		this.rtspConfig = rtspConfig;
		this.rtspSocketTcp = rtspSocketTcp;

		//
		this.rtspSessionInfo.clientIpAddr = clientIpAddr;

		//
		this.rtspRequestParser = new RtspRequestParser(logMsgInterface, rtspConfig, rtspSessionInfo);
		this.rtspResponseBuilder = new RtspResponseBuilder(logMsgInterface, rtspConfig, rtspSessionInfo);

		// create input/output stream reader/writer
		try {
			this.rtspBufferedReader = new BufferedReader(new InputStreamReader(rtspSocketTcp.getInputStream()));
			this.rtspBufferedWriter = new BufferedWriter(new OutputStreamWriter(rtspSocketTcp.getOutputStream()));
		} catch (IOException e) {
			logError(getClass().getSimpleName(), "IOException: Error creating RTSP socket reader/writer: " +
					e.getMessage());
			throw new RuntimeException(e);
		}
		//
		initRtspRequestCallbacks();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		Thread.currentThread().setName(threadName);

		//
		isRunning.set(true);
		logInfo(FNC_NAME, String.format("Entering RTSP loop - %s:%d%n",
				clientIpAddr.getHostAddress(), rtspSocketTcp.getPort()));

		//
		rtspTimeoutLastRequ = Instant.now();

		//
		try {
			int loopCounter = 0;
			while (! (hasBeenRequestedToStop() || rtspSocketTcp.isClosed())) {
				if (! mainLoop(++loopCounter)) {
					break;
				}
			}
		} catch (TcpSocketClosedException e) {
			logError(FNC_NAME, "TcpSocketClosedException: " + e.getMessage());
		} catch (UdpSocketIoException e) {
			logError(FNC_NAME, "UdpSocketIoException: " + e.getMessage());
		} catch (SocketException e) {
			logError(FNC_NAME, "SocketException: " + e.getMessage());
		} catch (InterruptedException e2) {
			logError(FNC_NAME, "InterruptedException");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception: " + e.getMessage());
		} finally {
			logDebug(FNC_NAME, "stopping thread");
			// stop sending/receiving RTP/RTCP packets
			pauseOrStopChildThreads(false);
			// close RTSP client socket and stream reader/writer
			closeRtspClientSocket();
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void initRtspRequestCallbacks() {
		final String FNC_NAME = getClass().getSimpleName() + ".initRtspRequestCallbacks()";

		rtspRequestParser.setCbCanReadData(() -> {
				try {
					if (rtspBufferedReader == null) {
						return false;
					}
					return rtspBufferedReader.ready();
				} catch (IOException e) {
					logError(FNC_NAME, "IOException: Error reading from RTSP socket: " + e.getMessage());
					throw new RuntimeException(e);
				}
			});
		rtspRequestParser.setCbReadDataLine(() -> {
				try {
					if (rtspBufferedReader == null) {
						return Optional.empty();
					}
					return Optional.ofNullable(rtspBufferedReader.readLine());
				} catch (IOException e) {
					logError(FNC_NAME, "IOException: Error reading from RTSP socket: " + e.getMessage());
					throw new RuntimeException(e);
				}
			});

		rtspResponseBuilder.setCbWriteDataLines(x -> {
				try {
					if (rtspBufferedWriter == null) {
						return;
					}
					for (String line : x) {
						//logDebug(FNC_NAME, "**: " + line.strip());
						rtspBufferedWriter.write(line);
					}
					rtspBufferedWriter.flush();
				} catch (IOException e) {
					logError(FNC_NAME, "IOException: Error writing to RTSP socket: " + e.getMessage());
					throw new RuntimeException(e);
				}
			});
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void unpauseOrStopThread(ThreadPausableBase thread) {
		if (thread != null && thread.isPaused()) {
			thread.unpauseThread();
			return;
		}
		if (thread != null) {
			thread.stopThread();  // blocks until the thread has actually stopped
		}
	}

	private void startRtcp_oneStream(ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".startRtcp_oneStream()";

		unpauseOrStopThread(ctfos.rtcpThreadSendRecv);
		//
		RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.getStreamInfoOrThrow(FNC_NAME, ctfos.streamSourceId);
		//
		ctfos.rtcpThreadSendRecv = BuilderThreadRtcp.builder()
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.debugSessionId(rtspSessionInfo.rtspSessionId)
				.streamSourceId(Objects.requireNonNull(tmpStreamInfo.rtspStreamSource).getId())
				.clientIpAddr(clientIpAddr)
				.clientDestPortRtcp(tmpStreamInfo.tpClientDestPortRtcp)
				.rtcpSocketUdp(Objects.requireNonNull(tmpStreamInfo.tpServerSocketRtcp))
				.rtspSsrcId(tmpStreamInfo.rtspSsrcId)
				.comIsRtxpEncryptionEnabled(rtspSessionInfo.isRtxpEncryptionEnabled)
				.comSrtxpKmdInbound(Objects.requireNonNull(tmpStreamInfo.streamKmds.kmdInbound))
				.comSrtxpKmdOutbound(Objects.requireNonNull(tmpStreamInfo.streamKmds.kmdOutbound))
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
		tmpStreamInfo.tpServerSocketRtcp = null;
	}

	private <B extends BuilderThreadRtpSenderBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
			B buildThreadRtpSender(
					B builder,
					RtspSessionInfo.StreamInfo streamInfo,
					double avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		return builder
				.logMsgInterface(Objects.requireNonNull(logMsgInterface))
				.comDebugSessionId(rtspSessionInfo.rtspSessionId)
				.comDebugRewindMediaFiles(rtspConfig.getIsDebugRewindMediaFiles())
				.comStreamSourceId(Objects.requireNonNull(streamInfo.rtspStreamSource).getId())
				.comIsStreamSourceFromFile(streamInfo.rtspStreamSource.getIsSourceFromFile())
				.comClientIpAddr(clientIpAddr)
				.comClientDestPortRtp(streamInfo.tpClientDestPortRtp)
				.comRtpSocketUdp(Objects.requireNonNull(streamInfo.tpServerSrcSocketRtp))
				.comAvFps(avFps)
				.comRtpSeqNrT0(streamInfo.rtspRtpSeqNrT0)
				.comRtpTimestampT0(
						new ParamsThreadRtpSenderCommon.RtpTsT0(streamInfo.rtspRtpTimestampT0, streamInfo.rtspRtpGenTsT0Ns)
					)
				.comRtspSsrcId(streamInfo.rtspSsrcId)
				.comXsrcBlockEntry(xsrcBlock)
				.comCbRtcpAppendToOutgoingQueque(this::cbSendRtcpPackets)
				.comCbNotifyThreadReady(this::cbNotifyThreadReady)
				.comCbThreadMayStartPlayback(this::cbThreadMayStartPlayback)
				.comAvStreamIncomingUri(streamInfo.rtspStreamSource.getInputUri())
				.comIsRtxpEncryptionEnabled(rtspSessionInfo.isRtxpEncryptionEnabled)
				.comSrtxpKmdOutbound(Objects.requireNonNull(streamInfo.streamKmds.kmdOutbound));
	}

	private <B extends BuilderThreadRtpSenderVideoBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
			B buildThreadVideo(
					B builder,
					RtspSessionInfo.StreamInfo streamInfo,
					double avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		return buildThreadRtpSender(builder, streamInfo, avFps, xsrcBlock);
	}

	private <B extends BuilderThreadRtpSenderAudioBase<B, T>, T extends ThreadRtpSenderBase<?, ?, ?, ?>>
					B buildThreadAudio(
					B builder,
					RtspSessionInfo.StreamInfo streamInfo,
					@SuppressWarnings("SameParameterValue") double avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		return buildThreadRtpSender(builder, streamInfo, avFps, xsrcBlock);
	}

	private void startSendRtp_oneStream(ChildThreadsForOneStream ctfos, String cnameHostname) {
		final String FNC_NAME = getClass().getSimpleName() + ".startSendRtp_oneStream()";

		unpauseOrStopThread(ctfos.rtpThreadSender);
		//
		RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.getStreamInfoOrThrow(FNC_NAME, ctfos.streamSourceId);
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
		//
		Objects.requireNonNull(tmpStreamInfo.rtspStreamSource);
		switch (tmpStreamInfo.rtspStreamSource.getCodec()) {
			case A_AAC:
				final double tmpFrameDurAacSecs = ((double)RtspConstants.RTP_SAMPLES_PER_FRAME_AAC_LC_AUDIO /
						(double)tmpStreamInfo.rtspStreamSource.getAudioSampleRateHz());
				final double tmpVirtualFpsAac = (1.0 / tmpFrameDurAacSecs);
				BuilderThreadRtpSenderAac.Builder builderAac = buildThreadAudio(
						BuilderThreadRtpSenderAac.builder(),
						tmpStreamInfo,
						tmpVirtualFpsAac,
						xsrcBlock
					);
				ctfos.rtpThreadSender = builderAac
						.audAacSampleRateHz(tmpStreamInfo.rtspStreamSource.getAudioSampleRateHz())
						.build();
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
							xsrcBlock
						);
					ctfos.rtpThreadSender = builderPcm
							.audPcmRtpAudioSpf(tmpStreamInfo.rtspStreamSource.getRtpAudioSamplesPerFrame(tmpVirtualFpsPcm))
							.audPcmSampleRateHz(tmpStreamInfo.rtspStreamSource.getAudioSampleRateHz())
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
				"RTP_#" + clientConnectionNr +
				"#sid" + rtspSessionInfo.rtspSessionId +
				"#ss" + tmpStreamInfo.rtspStreamSource.getId() +
				"#" + tmpStreamInfo.rtspStreamSource.getCodec().getValue()
			);
		ctfos.rtpThreadSender.setDaemon(false);
		ctfos.rtpThreadSender.start();

		// delete pointer to RTP socket
		tmpStreamInfo.tpServerSrcSocketRtp = null;
	}

	private void startChildThreads(String inputSourceId) {
		final String FNC_NAME = getClass().getSimpleName() + ".startChildThreads()";

		if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			throw new IllegalStateException(FNC_NAME + ": No input source found (OBJ)");
		}
		if (! rtspSessionInfo.inputSourceUrlPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			throw new IllegalStateException(FNC_NAME + ": No input source found (URL)");
		}
		RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.get(ServerMessageType.PLAY);
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
		for (int tmpSsId : is.getStreamSourceIds()) {
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsId).orElseThrow();
			if (childThreadsForOneStreamMap.containsKey(tmpSsObj.getId())) {
				throw new IllegalStateException(FNC_NAME + ": Child threads already exist");
			}
			//
			if (tmpSsObj.getIsSourceFromMq() && tmpSsObj.getCodec() == RtpPacketType.UNKNOWN) {
				logError(FNC_NAME, "ss=" + tmpSsObj.getId() + ": Source is a message queue, but codec is not set");
				continue;
			}
			//
			ChildThreadsForOneStream ctfos = new ChildThreadsForOneStream(tmpSsObj.getId(), inputSourceId);
			childThreadsForOneStreamMap.put(tmpSsObj.getId(), ctfos);

			startSendRtp_oneStream(ctfos, cnameHostname);
			startRtcp_oneStream(ctfos);
		}
	}

	private void pauseOrStopChildThreads(boolean doPause) {
		if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			return;
		}
		RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.get(ServerMessageType.PLAY);
		for (int tmpSsId : is.getStreamSourceIds()) {
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsId).orElseThrow();
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

	private void closeRtspClientSocket() {
		try {
			rtspBufferedReader.close();
			rtspBufferedWriter.close();
			rtspSocketTcp.close();
		} catch (IOException e) {
			// ignore
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
			RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.get(ServerMessageType.PLAY);
			for (int tmpSsId : is.getStreamSourceIds()) {
				RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsId).orElseThrow();
				if (! childThreadsForOneStreamMap.containsKey(tmpSsObj.getId())) {
					continue;
				}
				RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.getStreamInfoOrThrow(FNC_NAME, tmpSsObj.getId());
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
			ctfosToUse.rtcpThreadSendRecv.appendToSendQueque(rtcpPacketsBuf);
		}
	}

	private synchronized void cbNotifyThreadReady(Integer streamSourceId) {
		rtspSessionInfo.threadReadyStates.put(streamSourceId, true);
	}

	private synchronized Boolean cbThreadMayStartPlayback() {
		if (! rtspSessionInfo.inputSourceObjPerSmtMap.containsKey(ServerMessageType.PLAY)) {
			return false;
		}
		RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.get(ServerMessageType.PLAY);
		boolean areAllReady = true;
		for (int tmpSsId : is.getStreamSourceIds()) {
			if (! rtspSessionInfo.threadReadyStates.getOrDefault(tmpSsId, false)) {
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
		RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.get(ServerMessageType.PLAY);
		for (int tmpSsId : is.getStreamSourceIds()) {
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsId).orElseThrow();
			if (! childThreadsForOneStreamMap.containsKey(tmpSsObj.getId())) {
				continue;
			}
			ChildThreadsForOneStream ctfos = childThreadsForOneStreamMap.get(tmpSsObj.getId());
			updateCongestionLevel_oneStream(ctfos);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private Optional<RequestBasicInfo> getNextRequest()
			throws TcpSocketClosedException, InputStreamNotReadyException, SocketException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextRequest()";

		if (rtspSocketTcp.isClosed()) {
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
			throws TcpSocketClosedException, SocketException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleSuccessfulRequest()";

		if (rtspSocketTcp.isClosed()) {
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
		checkAuthentification(requestBasicInfo);
		if (requestBasicInfo.statusCode != ServerResponseStatusCode.OK) {
			rtspResponseBuilder.sendResponse(requestBasicInfo);
			// keep the connection open if only the authentication failed
			return (requestBasicInfo.statusCode == ServerResponseStatusCode.UNAUTHORIZED);
		}

		//
		rtspResponseBuilder.sendResponse(requestBasicInfo);

		//
		switch (requestBasicInfo.serverMessageType) {
			case ServerMessageType.SETUP:
				final int tmpSsId = Objects.requireNonNull(requestBasicInfo.requestUrlInputOrStreamSource).streamSourceId;
				// sanity check
				if (! rtspSessionInfo.streamsMapSetup.containsKey(tmpSsId)) {
					// this should never happen
					logError(FNC_NAME, "SETUP failed");
					return false;
				}
				RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.streamsMapSetup.get(tmpSsId);
				if (tmpStreamInfo == null || ! tmpStreamInfo.isTransportValid(rtspSessionInfo.isRtxpEncryptionEnabled)) {
					// this should never happen
					logError(FNC_NAME, "SETUP failed");
					return false;
				}
				nextState = SessionState.READY;
				break;
			case ServerMessageType.PLAY:
				final String tmpIsId = Objects.requireNonNull(requestBasicInfo.requestUrlInputOrStreamSource).inputSourceId;
				logInfo(FNC_NAME, "Starting playback for is='" + tmpIsId + "'");
				startChildThreads(tmpIsId);
				nextState = SessionState.PLAYING;
				break;
			case ServerMessageType.PAUSE:
				pauseOrStopChildThreads(true);
				nextState = SessionState.READY;
				break;
			case ServerMessageType.TEARDOWN:
				nextState = SessionState.INIT;
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

	private void checkAuthentification(@NonNull RequestBasicInfo requestBasicInfo) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkAuthentification()";

		boolean wasOk = true;
		boolean wasAuthOk = true;
		switch (requestBasicInfo.serverMessageType) {
			case ServerMessageType.DESCRIBE:
			case ServerMessageType.SETUP:
			case ServerMessageType.PLAY:
			case ServerMessageType.PAUSE:
			case ServerMessageType.TEARDOWN:
				final String tmpIsId = Objects.requireNonNull(requestBasicInfo.requestUrlInputOrStreamSource).inputSourceId;
				final Optional<RtspInputSource> tmpOptInputSource = rtspConfig.getInputSourceObj(tmpIsId);
				if (tmpOptInputSource.isEmpty()) {
					wasOk = false;
				} else if (tmpOptInputSource.get().getNeedsAuthentication()) {
					wasAuthOk = checkAuthentification_sub(requestBasicInfo.serverMessageType);
				}
				break;
		}

		if (! wasOk) {
			requestBasicInfo.statusCode = ServerResponseStatusCode.BAD_REQUEST;
			logError(FNC_NAME, String.format(
					"Could not find InputSource, rejecting request with code %s", requestBasicInfo.statusCode));
		} else if (! wasAuthOk) {
			requestBasicInfo.statusCode = ServerResponseStatusCode.UNAUTHORIZED;
			logInfo(FNC_NAME, String.format(
					"Rejecting %s request with code %s",
					requestBasicInfo.serverMessageType, requestBasicInfo.statusCode));
		}
	}

	private boolean checkAuthentification_sub(@NonNull ServerMessageType serverMessageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkAuthentification_sub()";

		if (rtspSessionInfo.authInfo.authUser.isBlank() || rtspSessionInfo.authInfo.authRealmClient.isBlank() ||
				rtspSessionInfo.authInfo.authNonceClient.isBlank() || rtspSessionInfo.authInfo.authResp.isBlank()) {
			// fail silently since missing at least the username is normal for the first unauthorized request
			return false;
		}
		if (! rtspSessionInfo.authInfo.authRealmClient.equals(RtspConstants.RTSP_AUTH_REALM)) {
			logError(FNC_NAME, "Invalid realm");
			return false;
		}
		if (! rtspSessionInfo.authInfo.authNonceClient.equals(rtspSessionInfo.authInfo.authNonceServer)) {
			logError(FNC_NAME, "Invalid nonce");
			return false;
		}
		final Optional<String> tmpOptUserPw = rtspConfig.getUserPassword(rtspSessionInfo.authInfo.authUser);
		if (tmpOptUserPw.isEmpty()) {
			logError(FNC_NAME, "Invalid username");
			return false;
		}
		//
		final String expectedResponse = computeExpectedAuthResponse(serverMessageType.name(), tmpOptUserPw.get());
		if (! rtspSessionInfo.authInfo.authResp.equals(expectedResponse)) {
			logError(FNC_NAME, "Invalid challenge-response");
			return false;
		}
		//
		return true;
	}

	private @NonNull String computeExpectedAuthResponse(@NonNull String method, @NonNull String userPwPlain) {
		String tmpHa1 = HashMd5Helper.hashOfString(
				rtspSessionInfo.authInfo.authUser + ":" + RtspConstants.RTSP_AUTH_REALM + ":" + userPwPlain,
				false
			);
		String tmpHa2 = HashMd5Helper.hashOfString(
				method + ":" + rtspSessionInfo.authInfo.authUri,
				false
			);
		return HashMd5Helper.hashOfString(
				tmpHa1 + ":" + rtspSessionInfo.authInfo.authNonceServer + ":" + tmpHa2,
				false
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop(final int loopCounter)
			throws TcpSocketClosedException, SocketException, UdpSocketIoException, InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (rtspTimeoutLastRequ != null) {
			long tmpTimeDiff = Duration.between(rtspTimeoutLastRequ, Instant.now()).toSeconds();
			if (tmpTimeDiff > RtspConstants.RTSP_SESSION_TIMEOUT + SESSION_TIMEOUT_TOLERANCE_SEC) {
				logError(FNC_NAME, "RTSP session timeout after " + tmpTimeDiff + " seconds");
				// @TODO send BYE packet
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

package org.tsitle.rtsp.threads.rtsp;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.InputStreamNotReadyException;
import org.tsitle.rtsp.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.packets.rtcp.RtcpInnerXsrcBlock;
import org.tsitle.rtsp.threads.*;
import org.tsitle.rtsp.threads.rtcp.ThreadRtcpSendRecv;
import org.tsitle.rtsp.threads.rtp.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class ThreadRtspServer extends ThreadBase {

	private static class ChildThreadsForOneStream {
		final int streamSourceId;
		final String inputSourceId;

		ThreadRtpSenderBase rtpThreadSender;

		ThreadRtcpSendRecv rtcpThreadSendRecv;
		int rtcpLastTargetCongestionLevel = -1;

		ChildThreadsForOneStream(int streamSourceId, String inputSourceId) {
			this.streamSourceId = streamSourceId;
			this.inputSourceId = inputSourceId;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static final int SESSION_TIMEOUT_TOLERANCE_SEC = 10;

	private final RtspConfig rtspConfig;
	private final int clientConnectionNr;

	/** Client IP address */
	private final InetAddress clientIpAddr;

	/** TCP socket used to send/receive RTSP messages */
	private final Socket rtspSocketTcp;
	private final BufferedReader rtspBufferedReader;
	private final BufferedWriter rtspBufferedWriter;
	private final RtspSessionInfo rtspSessionInfo = new RtspSessionInfo();
	private final RtspRequestParser rtspRequestParser;
	private final RtspResponseBuilder rtspResponseBuilder;
	private final Map<Integer, ChildThreadsForOneStream> childThreadsForOneStreamMap = new HashMap<>();
	private final Map<Integer, ChildThreadsForOneStream> childThreadsPerSsrcMap = new HashMap<>();
	private Instant rtspTimeoutLastRequ = null;

	/**
	 * Constructor.
	 * @param rtspConfig RTSP configuration
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 */
	public ThreadRtspServer(RtspConfig rtspConfig, int clientConnectionNr, Socket rtspSocketTcp) {
		this.rtspConfig = rtspConfig;
		this.clientConnectionNr = clientConnectionNr;
		this.rtspSocketTcp = rtspSocketTcp;
		this.clientIpAddr = rtspSocketTcp.getInetAddress();

		//
		this.rtspSessionInfo.clientIpAddr = clientIpAddr;

		//
		this.rtspRequestParser = new RtspRequestParser(rtspConfig, rtspSessionInfo);
		this.rtspResponseBuilder = new RtspResponseBuilder(rtspConfig, rtspSessionInfo);

		// create input/output stream reader/writer
		try {
			this.rtspBufferedReader = new BufferedReader(new InputStreamReader(rtspSocketTcp.getInputStream()));
			this.rtspBufferedWriter = new BufferedWriter(new OutputStreamWriter(rtspSocketTcp.getOutputStream()));
		} catch (IOException e) {
			System.err.println(getClass().getSimpleName() + ": IOException: Error creating RTSP socket reader/writer: "
					+ e.getMessage());
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

		isRunning.set(true);
		System.out.format("%s: <#%d> Entering RTSP loop - %s:%d%n", FNC_NAME,
				clientConnectionNr, clientIpAddr.getHostAddress(), rtspSocketTcp.getPort());

		//
		rtspTimeoutLastRequ = Instant.now();

		//
		try {
			int loopCounter = 0;
			while (! (doStop.get() || rtspSocketTcp.isClosed())) {
				if (! mainLoop(++loopCounter)) {
					break;
				}
			}
		} catch (TcpSocketClosedException e) {
			System.err.println(FNC_NAME + ": TcpSocketClosedException: " + e.getMessage());
		} catch (UdpSocketIoException e) {
			System.err.println(FNC_NAME + ": UdpSocketIoException: " + e.getMessage());
		} catch (SocketException e) {
			System.err.println(FNC_NAME + ": SocketException: " + e.getMessage());
		} catch (FileNotFoundException e) {
			System.err.println(FNC_NAME + ": FileNotFoundException: " + e.getMessage());
		} catch (Exception e) {
			System.err.println(FNC_NAME + ": Exception: " + e.getMessage());
		} finally {
			// stop sending/receiving RTP/RTCP packets
			pauseOrStopChildThreads(false);
			// close RTSP client socket and stream reader/writer
			closeRtspClientSocket();
			//
			isRunning.set(false);
			System.out.format("%s: <#%d|%s> Thread ended%n", FNC_NAME, clientConnectionNr,
					(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	protected void stopThreadHook() {
		try {
			rtspSocketTcp.close();
		} catch (IOException e) {
			// ignore
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
					System.err.println(FNC_NAME + ": IOException: Error reading from RTSP socket: " + e.getMessage());
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
					System.err.println(FNC_NAME + ": IOException: Error reading from RTSP socket: " + e.getMessage());
					throw new RuntimeException(e);
				}
			});

		rtspResponseBuilder.setCbWriteDataLines(x -> {
				try {
					if (rtspBufferedWriter == null) {
						return;
					}
					for (String line : x) {
						//System.out.println(FNC_NAME + ": **: " + line.strip());
						rtspBufferedWriter.write(line);
					}
					rtspBufferedWriter.flush();
				} catch (IOException e) {
					System.err.println(FNC_NAME + ": IOException: Error writing to RTSP socket: " + e.getMessage());
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
			thread.stopThread();
		}
	}

	private void startRecvRtcp_oneStream(ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".startRecvRtcp_oneStream()";

		unpauseOrStopThread(ctfos.rtcpThreadSendRecv);
		//
		RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.getStreamInfoOrThrow(FNC_NAME, ctfos.streamSourceId);
		//
		ctfos.rtcpThreadSendRecv = BuilderThreadRtcp.builder()
				.debugSessionId(rtspSessionInfo.rtspSessionId)
				.debugStreamId(tmpStreamInfo.rtspStreamSource.getId())
				.clientIpAddr(clientIpAddr)
				.clientDestPortRtcp(tmpStreamInfo.tpClientDestPortRtcp)
				.rtcpSocketUdp(tmpStreamInfo.tpServerSocketRtcp)
				.rtspSsrcId(tmpStreamInfo.rtspSsrcId)
				.build();
		ctfos.rtcpThreadSendRecv.setDaemon(false);
		ctfos.rtcpThreadSendRecv.start();

		// delete pointer to RTCP socket
		tmpStreamInfo.tpServerSocketRtcp = null;
	}

	private <B extends BuilderThreadRtpSenderBase<B, T>, T extends ThreadRtpSenderBase>
			B buildThreadRtpSender(
					B builder,
					RtspSessionInfo.StreamInfo streamInfo,
					float avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		return builder
				.comDebugSessionId(rtspSessionInfo.rtspSessionId)
				.comDebugRewindMediaFiles(rtspConfig.getIsDebugRewindMediaFiles())
				.comStreamSourceId(streamInfo.rtspStreamSource.getId())
				.comClientIpAddr(clientIpAddr)
				.comClientDestPortRtp(streamInfo.tpClientDestPortRtp)
				.comRtpSocketUdp(streamInfo.tpServerSrcSocketRtp)
				.comAvFps(avFps)
				.comRtpSeqNrT0(streamInfo.rtspRtpSeqNrT0)
				.comRtpTimestampT0(streamInfo.rtspRtpTimestampT0)
				.comRtspSsrcId(streamInfo.rtspSsrcId)
				.comXsrcBlockEntry(xsrcBlock)
				.comCbRtcpAppendToOutgoingQueque(this::cbSendRtcpPackets)
				.comCbNotifyThreadReady(this::cbNotifyThreadReady)
				.comCbThreadMayStartPlayback(this::cbThreadMayStartPlayback);
	}

	private <B extends BuilderThreadRtpSenderVideoBase<B, T>, T extends ThreadRtpSenderBase>
			B buildThreadVideo(
					B builder,
					RtspSessionInfo.StreamInfo streamInfo,
					float avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		return buildThreadRtpSender(builder, streamInfo, avFps, xsrcBlock)
				.vidVideoFilePath(streamInfo.rtspStreamSource.getFilePath());
	}

	private <B extends BuilderThreadRtpSenderAudioBase<B, T>, T extends ThreadRtpSenderBase>
					B buildThreadAudio(
					B builder,
					RtspSessionInfo.StreamInfo streamInfo,
					@SuppressWarnings("SameParameterValue") float avFps,
					RtcpInnerXsrcBlock xsrcBlock
				) {
		return buildThreadRtpSender(builder, streamInfo, avFps, xsrcBlock)
				.audAudioFilePath(streamInfo.rtspStreamSource.getFilePath());
	}

	private void startSendRtp_oneStream(ChildThreadsForOneStream ctfos, String cnameHostname) throws FileNotFoundException {
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
		switch (tmpStreamInfo.rtspStreamSource.getCodec()) {
			case V_JPEG:
				BuilderThreadRtpSenderMjpeg.Builder builderMjpeg = buildThreadVideo(
						BuilderThreadRtpSenderMjpeg.builder(),
						tmpStreamInfo,
						tmpStreamInfo.rtspStreamSource.getVideoFps(),
						xsrcBlock
					);
				ctfos.rtpThreadSender = builderMjpeg.build();
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
					final int tmpVirtualFps = (int)(1000.0f / (float)RtspConstants.RTP_SEND_INTERVAL_AUDIO_MS);
					BuilderThreadRtpSenderPcm.Builder builderPcm = buildThreadAudio(
							BuilderThreadRtpSenderPcm.builder(),
							tmpStreamInfo,
							tmpVirtualFps,
							xsrcBlock
						);
					ctfos.rtpThreadSender = builderPcm
							.audPcmRtpAudioSpf(tmpStreamInfo.rtspStreamSource.getRtpAudioSamplesPerFrame(tmpVirtualFps))
							.audPcmSampleRateHz(tmpStreamInfo.rtspStreamSource.getAudioSampleRateHz())
							.audPcmChannelCount(tmpStreamInfo.rtspStreamSource.getAudioChannelCount())
							.audPcmBitsPerSample(tmpStreamInfo.rtspStreamSource.getCodec().getAudioBitsPerSample().orElseThrow())
							.audPcmInputBigEndian(tmpStreamInfo.rtspStreamSource.getIsAudioBigEndian())
							.audPcmCodec(tmpStreamInfo.rtspStreamSource.getCodec())
							.build();
				} else {
					throw new IllegalStateException(FNC_NAME + ": Unsupported codec: " + tmpStreamInfo.rtspStreamSource.getCodec());
				}
		}
		ctfos.rtpThreadSender.setDaemon(false);
		ctfos.rtpThreadSender.start();

		// delete pointer to RTP socket
		tmpStreamInfo.tpServerSrcSocketRtp = null;
	}

	private void startChildThreads(String inputSourceId) throws FileNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".startChildThreads()";

		RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.getOrDefault(ServerMessageType.PLAY, null);
		if (is == null) {
			throw new IllegalStateException(FNC_NAME + ": No input source found");
		}
		//
		String cnameHostname;
		try {
			String tmpIsUrl = rtspSessionInfo.inputSourceUrlPerSmtMap.getOrDefault(ServerMessageType.PLAY, null);
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
			ChildThreadsForOneStream ctfos = new ChildThreadsForOneStream(tmpSsObj.getId(), inputSourceId);
			childThreadsForOneStreamMap.put(tmpSsObj.getId(), ctfos);

			startSendRtp_oneStream(ctfos, cnameHostname);
			startRecvRtcp_oneStream(ctfos);
		}
	}

	private void pauseOrStopChildThreads(boolean doPause) {
		RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.getOrDefault(ServerMessageType.PLAY, null);
		if (is == null) {
			return;
		}
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
					ctfos.rtcpThreadSendRecv.stopThread();
				}
			}
			if (ctfos.rtpThreadSender != null) {
				if (doPause) {
					ctfos.rtpThreadSender.pauseThread();
				} else {
					ctfos.rtpThreadSender.stopThread();
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
			RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.getOrDefault(ServerMessageType.PLAY, null);
			if (is == null) {
				throw new IllegalStateException(FNC_NAME + ": No input source found");
			}
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
		if (ctfosToUse.rtcpThreadSendRecv != null && ctfosToUse.rtcpThreadSendRecv.isRunning()) {
			ctfosToUse.rtcpThreadSendRecv.appendToSendQueque(rtcpPacketsBuf);
		}
	}

	private synchronized void cbNotifyThreadReady(Integer streamSourceId) {
		rtspSessionInfo.threadReadyStates.put(streamSourceId, true);
	}

	private synchronized Boolean cbThreadMayStartPlayback() {
		RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.getOrDefault(ServerMessageType.PLAY, null);
		if (is == null) {
			return false;
		}
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
			System.out.format("%s: <#%d|%s> Congestion level changed to: %d%n",
					FNC_NAME, clientConnectionNr,
					(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId),
					currentTcl);
			ctfos.rtpThreadSender.notifyCongestionLevelChange(currentTcl);
		}
		ctfos.rtcpLastTargetCongestionLevel = currentTcl;
	}

	private void updateCongestionLevel() {
		RtspInputSource is = rtspSessionInfo.inputSourceObjPerSmtMap.getOrDefault(ServerMessageType.PLAY, null);
		if (is == null) {
			return;
		}
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
			System.err.format("%s: <#%d|%s> Received invalid RTSP request, rejecting it with code %s%n",
					FNC_NAME, clientConnectionNr,
					(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId),
					requestBasicInfo.statusCode);
			rtspResponseBuilder.sendResponse(requestBasicInfo);
			return Optional.empty();
		}
		System.out.format("%s: <#%d|%s> Received %s request%n",
				FNC_NAME, clientConnectionNr,
				(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId),
				requestBasicInfo.serverMessageType);
		return Optional.of(requestBasicInfo);
	}

	private boolean handleSuccessfulRequest(RequestBasicInfo requestBasicInfo)
			throws TcpSocketClosedException, SocketException, FileNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleSuccessfulRequest()";

		if (rtspSocketTcp.isClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		SessionState nextState = rtspSessionInfo.sessionState;

		// check whether the request is allowed in current RTSP state
		boolean wasOk = (requestBasicInfo.serverMessageType == ServerMessageType.OPTIONS ||
				requestBasicInfo.serverMessageType == ServerMessageType.DESCRIBE);

		switch (rtspSessionInfo.sessionState) {
			case INIT:
				if (requestBasicInfo.serverMessageType == ServerMessageType.SETUP) {  // SETUP is allowed in INIT and READY states
					wasOk = true;
				}
				break;
			case READY:
				if (requestBasicInfo.serverMessageType == ServerMessageType.SETUP ||  // SETUP is allowed in INIT and READY states
						requestBasicInfo.serverMessageType == ServerMessageType.PLAY ||
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
			System.err.format("%s: <#%d|%s> Request %s not valid for current RTSP state %s, rejecting it with code %s%n",
					FNC_NAME, clientConnectionNr,
					(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId),
					requestBasicInfo.serverMessageType,
					rtspSessionInfo.sessionState,
					requestBasicInfo.statusCode);
			rtspResponseBuilder.sendResponse(requestBasicInfo);
			return false;
		}

		//
		rtspResponseBuilder.sendResponse(requestBasicInfo);

		//
		switch (requestBasicInfo.serverMessageType) {
			case ServerMessageType.SETUP:
				// sanity check
				RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.streamsMapSetup.getOrDefault(
						requestBasicInfo.requestUrlInputOrStreamSource.streamSourceId,
						null
					);
				if (tmpStreamInfo == null || ! tmpStreamInfo.isTransportValid()) {
					// this should never happen
					System.err.format("%s: <#%d|%s> SETUP failed%n",
							FNC_NAME, clientConnectionNr,
							(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId));
					return false;
				}
				nextState = SessionState.READY;
				break;
			case ServerMessageType.PLAY:
				startChildThreads(requestBasicInfo.requestUrlInputOrStreamSource.inputSourceId);
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
			System.out.format("%s: <#%d|%s> RTSP state is now %s%n%n",
					FNC_NAME, clientConnectionNr,
					(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId),
					nextState);
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop(final int loopCounter)
			throws TcpSocketClosedException, SocketException, FileNotFoundException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		long tmpTimeDiff = Duration.between(rtspTimeoutLastRequ, Instant.now()).toSeconds();
		if (tmpTimeDiff > RtspConstants.RTSP_SESSION_TIMEOUT + SESSION_TIMEOUT_TOLERANCE_SEC) {
			System.err.println(FNC_NAME + ": RTSP session timeout after " + tmpTimeDiff + " seconds");
			return false;
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
		} catch (InputStreamNotReadyException e) {
			try {
				Thread.sleep(15);
				return true;
			} catch (InterruptedException ex) {
				return false;
			}
		}
	}

}

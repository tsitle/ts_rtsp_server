package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.CancelToken;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.*;
import org.tsitle.rtsp.threads.rtp.*;
import org.tsitle.rtsp.threads.rtp.builders.*;
import org.tsitle.rtsp.threads.rtsp.proto.*;

import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class ThreadRtspServer extends RunnableBase implements RtspChildThreadsCallbackInterface {

	/** RTSP session timeout tolerance in seconds. Sometimes even compliant clients fail to send a keep-alive message in time. */
	private static final int SESSION_TIMEOUT_TOLERANCE_SEC = 15;

	private final String threadName;

	private final RtspConfig rtspConfig;
	private final RtxpTcpReadWrite rtxpTcpReadWrite;
	private final RtspSessionInfo rtspSessionInfo = new RtspSessionInfo();
	private final RtspChildThreadMng rtspChildThreadMng;
	private final RtspProtoRequestInputSvc rtspProtoRequestInputSvc;
	private final RtspProtoRequestBuilder rtspProtoRequestBuilder;
	private final RtspProtoResponseOutputSvc rtspProtoResponseOutputSvc;
	private final RtspProtoResponseParser rtspProtoResponseParser;

	private @Nullable Instant rtspTimeoutLastRequ = null;

	private final Map<Integer, RtspChildThreadMng.ChildThreadsForOneStream> childThreadsPerSsrcMap = new HashMap<>();

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

		this.rtspConfig = rtspConfig;
		this.rtxpTcpReadWrite = new RtxpTcpReadWrite(rtspSocketTcp);

		//
		this.rtspSessionInfo.setClientIpAddr(rtspSocketTcp.getInetAddress());
		this.rtspSessionInfo.isRtspsConnection = isRtspsConnection;

		//
		this.rtspChildThreadMng = new RtspChildThreadMng(
				logMsgInterface,
				rtspConfig,
				clientConnectionNr,
				rtspSessionInfo,
				rtxpTcpReadWrite,
				this
			);

		//
		this.rtspProtoRequestInputSvc = new RtspProtoRequestInputSvc(
				logMsgInterface,
				rtspConfig,
				rtspSessionInfo,
				this.rtxpTcpReadWrite
			);
		this.rtspProtoResponseOutputSvc = new RtspProtoResponseOutputSvc(
				logMsgInterface,
				rtspConfig,
				rtspSessionInfo,
				this.rtxpTcpReadWrite
			);
		this.rtspProtoRequestBuilder = new RtspProtoRequestBuilder(logMsgInterface, this.rtxpTcpReadWrite, rtspSessionInfo);
		this.rtspProtoResponseParser = new RtspProtoResponseParser(
				true,
				logMsgInterface,
				this.rtxpTcpReadWrite,
				rtspConfig,
				rtspSessionInfo
			);
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
				rtspSessionInfo.getClientIpAddr().getHostAddress(), rtxpTcpReadWrite.getSocketRemotePort()));

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

			// send a BYE packet per stream to let the client know we are terminating the session
			for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValues()) {
				if (ctfos.rtcpThreadSendRecv == null || ! ctfos.rtcpThreadSendRecv.isRunning()) {
					continue;
				}
				ctfos.rtcpThreadSendRecv.appendByePacketToSendQueue();
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
			//e.printStackTrace();
			logError(FNC_NAME, "Exception: " + e.getMessage());
		} finally {
			logInfo(FNC_NAME, String.format("Closing RTSP%s for %s:%d",
					rtspSessionInfo.isRtspsConnection ? "S" : "",
					rtspSessionInfo.getClientIpAddr().getHostAddress(), rtxpTcpReadWrite.getSocketRemotePort()));
			//
			logDebug(FNC_NAME, "stopping thread");
			// stop sending/receiving RTP/RTCP packets
			rtspChildThreadMng.pauseOrStopChildThreads(false);
			// close RTSP client socket and stream reader/writer
			rtxpTcpReadWrite.closeSocket();
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public synchronized void cbSendRtcpPackets(int ssrcId, @NonNull BufferExt rtcpPacketsBuf) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbSendRtcpPackets()";

		RtspChildThreadMng.ChildThreadsForOneStream ctfosToUse = null;
		if (childThreadsPerSsrcMap.containsKey(ssrcId)) {
			ctfosToUse = childThreadsPerSsrcMap.get(ssrcId);
		} else {
			if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspProtoMessageType.PLAY)) {
				throw new IllegalStateException(FNC_NAME + ": No input source found");
			}
			for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
				RtspStaticSessionInfo.SubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
				RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
				if (! rtspChildThreadMng.ctfosMapContainsKey(tmpSsObj.getId())) {
					continue;
				}
				RtspStaticSessionInfo.StreamInfo tmpStreamInfo =
						RtspStaticSessionInfo.getStreamInfoOrThrow(FNC_NAME, tmpSubStreamId);
				if (tmpStreamInfo.rtspSsrcId != ssrcId) {
					continue;
				}
				ctfosToUse = rtspChildThreadMng.getCtfosMapValue(tmpSsObj.getId());
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

	public synchronized void cbRcvdRtcpRrPacket(@NonNull Instant time) {
		rtxpTcpReadWrite.resetTcpActivityTimeoutTimer();
		rtspTimeoutLastRequ = Instant.now();
	}

	public synchronized void cbNotifyThreadReady(@NonNull Integer streamSourceId) {
		rtspSessionInfo.threadReadyStates.put(streamSourceId, true);
	}

	public synchronized @NonNull Boolean cbThreadMayStartPlayback() {
		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspProtoMessageType.PLAY)) {
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
	// -----------------------------------------------------------------------------------------------------------------

	private void updateCongestionLevel_oneStream(RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos) {
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
		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspProtoMessageType.PLAY)) {
			return;
		}
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
			if (! rtspChildThreadMng.ctfosMapContainsKey(tmpSsObj.getId())) {
				continue;
			}
			RtspChildThreadMng.ChildThreadsForOneStream ctfos = rtspChildThreadMng.getCtfosMapValue(tmpSsObj.getId());
			updateCongestionLevel_oneStream(ctfos);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RequestBasicInfo getNextRequest()
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException, UdpSocketIoException {
		RequestBasicInfo resObj = rtspProtoRequestInputSvc.getNextRequest();

		rtspProtoResponseOutputSvc.sendResponse(resObj);
		return resObj;
	}

	private boolean handleSuccessfulRequest(@NonNull RequestBasicInfo requestBasicInfo) throws TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleSuccessfulRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		SessionState nextState = rtspSessionInfo.sessionState;

		//
		Objects.requireNonNull(
				requestBasicInfo.requestUrlInputOrStreamSource,
				FNC_NAME + ": requestBasicInfo.requestUrlInputOrStreamSource is null"
			);

		//
		switch (requestBasicInfo.messageType) {
			case RtspProtoMessageType.SETUP:
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
					return false;  // tear down the session
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
					return false;  // tear down the session
				}
				nextState = SessionState.READY;
				break;
			case RtspProtoMessageType.PLAY:
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
					rtspChildThreadMng.unpauseChildThreads();
				} else {
					rtspChildThreadMng.startChildThreads(tmpIsIdPlay);
				}
				nextState = SessionState.PLAYING;
				break;
			case RtspProtoMessageType.PAUSE:
				final String tmpIsIdPause = requestBasicInfo.requestUrlInputOrStreamSource.inputSourceId;
				logInfo(FNC_NAME, String.format("Pausing playback for IS='%s'", tmpIsIdPause));
				//
				rtxpTcpReadWrite.setTcpActivityTimeoutForRtspOnly();
				rtspChildThreadMng.pauseOrStopChildThreads(true);
				nextState = SessionState.READY;
				rtspSessionInfo.isPlaybackPaused = true;
				break;
			case RtspProtoMessageType.TEARDOWN:
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

	private RtspStaticSessionInfo.@NonNull SubStreamInfo getSubStreamInfo(@NonNull String subStreamId) {
		return RtspStaticSessionInfo.getSubStreamInfo(rtspSessionInfo.getClientIpAddr(), subStreamId).orElseThrow();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void srtxpRekeyInbound_oneStream(RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyInbound_oneStream()";

		RtspStaticSessionInfo.StreamInfo tmpStreamInfo = RtspStaticSessionInfo.getStreamInfoOrThrow(FNC_NAME, ctfos.subStreamId);

		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				rtspSessionInfo.getClientIpAddr(),
				ctfos.subStreamId,
				tmpStreamInfo.rtspSsrcId
			);
		//
		if (tmpStreamKmds.nextKmdInbound == null) {
			return;  // nothing to do
		}
		//
		final String logMsgPrefix = "ss=" + ctfos.streamSourceId + ": ";
		logInfo(FNC_NAME, logMsgPrefix + "SRTxP re-keying in progress");
		ctfos.rtcpThreadSendRecv.setNextSrtcpKmdInbound(tmpStreamKmds.nextKmdInbound);
		tmpStreamKmds.nextKmdInbound = null;
		ctfos.srtxpInboundRekeyingInProgress = true;
	}

	private void srtxpRekeyInbound() {
		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspProtoMessageType.PLAY)) {
			return;  // we're not ready yet
		}
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValues()) {
			if (ctfos.rtcpThreadSendRecv == null || ! ctfos.rtcpThreadSendRecv.isRunning()) {
				continue;
			}
			//
			if (ctfos.srtxpInboundRekeyingInProgress) {
				boolean tmpHasBeenCompleted = ctfos.rtcpThreadSendRecv.hasSrtcpInboundRekeyingBeenCompleted();
				if (tmpHasBeenCompleted) {
					ctfos.srtxpInboundRekeyingInProgress = false;
				}
				continue;
			}
			//
			srtxpRekeyInbound_oneStream(ctfos);
		}
	}

	private boolean srtxpRekeyOutbound_oneStream(RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos) throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound_oneStream()";

		final String logMsgPrefix = "ss=" + ctfos.streamSourceId + ": ";

		rtspProtoRequestBuilder.sendRequestOptions(ctfos.subStreamId);
		RtspProtoResponseParser.ResponseInfo tmpRi = rtspProtoResponseParser.parseResponse();
		if (tmpRi.statusCode != RtspProtoStatusCode.OK) {
			logWarn(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - client does not support OPTIONS request");
			return false;
		}

		//
		SrtxpKmd tmpNextKmdOutbound;
		try {
			tmpNextKmdOutbound = rtspProtoRequestBuilder.sendRequestSrtxpRekey(
					ctfos.subStreamId,
					tmpRi.supportedMessageTypes
				);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - client does not support pushing new MK");
			return false;
		}
		tmpRi = rtspProtoResponseParser.parseResponse();
		if (tmpRi.statusCode != RtspProtoStatusCode.OK) {
			logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed (" + tmpRi.statusCode + ")");
			return false;
		}

		//
		logInfo(FNC_NAME, logMsgPrefix + "SRTxP re-keying in progress");
		if (ctfos.rtcpThreadSendRecv != null && ctfos.rtcpThreadSendRecv.isRunning()) {
			ctfos.rtcpThreadSendRecv.setNextSrtcpKmdOutbound(tmpNextKmdOutbound);
		}
		if (ctfos.rtpThreadSender != null && ctfos.rtpThreadSender.isRunning()) {
			ctfos.rtpThreadSender.setNextSrtpKmdOutbound(tmpNextKmdOutbound);
		}
		ctfos.srtxpOutboundRekeyingInProgress = true;
		return true;
	}

	private boolean srtxpRekeyOutbound() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound()";

		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspProtoMessageType.PLAY)) {
			return true;  // we're not ready yet
		}
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValues()) {
			if (ctfos.rtpThreadSender == null || ! ctfos.rtpThreadSender.isRunning()) {
				continue;
			}
			//
			if (ctfos.srtxpOutboundRekeyingInProgress) {
				boolean tmpHasBeenCompleted;
				if (ctfos.rtcpThreadSendRecv != null && ctfos.rtcpThreadSendRecv.isRunning()) {
					tmpHasBeenCompleted = ctfos.rtcpThreadSendRecv.hasSrtcpOutboundRekeyingBeenCompleted();
				} else {
					tmpHasBeenCompleted = true;
				}
				tmpHasBeenCompleted = (tmpHasBeenCompleted && ctfos.rtpThreadSender.hasSrtpOutboundRekeyingBeenCompleted());
				if (tmpHasBeenCompleted) {
					ctfos.srtxpOutboundRekeyingInProgress = false;
				}
				continue;
			}
			//
			long tmpPktCount = ctfos.rtpThreadSender.getPacketCountOutbound();
			if ((long)((double)tmpPktCount * 0.9) < RtspConstants.SRTXP_REKEYING_INTERVAL_PACKETS_INT) {
				continue;
			}
			logDebug(FNC_NAME, String.format("ss=%d: 90%% of maximum outbound RTP packet count reached: %s (RTCP in %s / out %s)",
					ctfos.streamSourceId,
					Long.toUnsignedString(tmpPktCount),
					ctfos.rtcpThreadSendRecv != null ? Long.toUnsignedString(ctfos.rtcpThreadSendRecv.getPacketCountInbound()) : "-",
					ctfos.rtcpThreadSendRecv != null ? Long.toUnsignedString(ctfos.rtcpThreadSendRecv.getPacketCountOutbound()) : "-"));

			//
			if (! srtxpRekeyOutbound_oneStream(ctfos)) {
				return false;  // shutdown the session
			}
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop(final int loopCounter)
			throws TcpSocketClosedException, TcpSocketIoException, UdpSocketIoException, InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (rtspSessionInfo.isTransportUdp && rtspTimeoutLastRequ != null) {
			long tmpTimeDiff = Duration.between(rtspTimeoutLastRequ, Instant.now()).toSeconds();
			if (tmpTimeDiff > RtspConstants.RTSP_SESSION_TIMEOUT + SESSION_TIMEOUT_TOLERANCE_SEC) {
				logError(FNC_NAME, "RTSP session timeout after " + tmpTimeDiff + " seconds");
				return false;  // terminate session
			}
		}

		//
		if (loopCounter % 37 == 0) {
			updateCongestionLevel();
		} else if (loopCounter % 83 == 0 && rtspSessionInfo.isTransportSrtpSrtcp) {  // 83^=roughly once every 5s
			srtxpRekeyInbound();
		} else if (loopCounter % 89 == 0 && rtspSessionInfo.isTransportSrtpSrtcp) {
			if (! srtxpRekeyOutbound()) {
				return false;  // terminate session
			}
		}

		//
		try {
			RequestBasicInfo requestBasicInfo = getNextRequest();
			rtspTimeoutLastRequ = Instant.now();
			if (requestBasicInfo.statusCode != RtspProtoStatusCode.OK) {
				return true;
			}
			return handleSuccessfulRequest(requestBasicInfo);
		} catch (InputStreamNotReadyException e1) {
			Thread.sleep(15);
			return true;
		}
	}

}

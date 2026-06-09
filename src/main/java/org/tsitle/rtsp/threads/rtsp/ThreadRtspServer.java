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
import org.tsitle.rtsp.threads.rtp.RtpConstants;
import org.tsitle.rtsp.threads.rtsp.proto.*;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataRequest;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspResponseBasics;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspKeymgmtKmdsOutbound;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;

import java.net.InetAddress;
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
	private final RtspProtoRequestOutputSvc rtspProtoRequestOutputSvc;
	private final RtspProtoResponseInputSvc rtspProtoResponseInputSvc;
	private final RtspProtoResponseOutputSvc rtspProtoResponseOutputSvc;

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
				@NonNull String cfgServerNameAndVersion,
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
				this.rtxpTcpReadWrite,
				true
			);
		this.rtspProtoRequestOutputSvc = new RtspProtoRequestOutputSvc(
				logMsgInterface,
				rtspConfig,
				cfgServerNameAndVersion,
				rtspSessionInfo,
				this.rtxpTcpReadWrite,
				false
			);
		this.rtspProtoResponseInputSvc = new RtspProtoResponseInputSvc(
				logMsgInterface,
				rtspConfig,
				rtspSessionInfo,
				this.rtxpTcpReadWrite,
				true
			);
		this.rtspProtoResponseOutputSvc = new RtspProtoResponseOutputSvc(
				logMsgInterface,
				rtspConfig,
				cfgServerNameAndVersion,
				rtspSessionInfo,
				this.rtxpTcpReadWrite
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
				getClientIpAddr().getHostAddress(), rtxpTcpReadWrite.getSocketRemotePort()));

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
			for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesAll()) {
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
					getClientIpAddr().getHostAddress(), rtxpTcpReadWrite.getSocketRemotePort()));
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
			if (! rtspSessionInfo.existsInputSourceObjForMt_nonSetup(RtspMessageType.PLAY)) {  // sanity check
				throw new IllegalStateException(FNC_NAME + ": No Input Source found");
			}
			for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
				RtspStaticSessionInfo.SdpSubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
				RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
				if (! rtspChildThreadMng.ctfosMapContainsKey(tmpSsObj.getId())) {
					continue;
				}
				RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStream =
						RtspStaticSessionInfo.getSetupSubStreamOrThrow(FNC_NAME, tmpSubStreamId);
				if (tmpSetupSubStream.rtspSsrcId != ssrcId) {
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
		if (! rtspSessionInfo.existsInputSourceObjForMt_nonSetup(RtspMessageType.PLAY)) {  // sanity check
			return false;
		}
		boolean areAllReady = true;
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SdpSubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
			if (! rtspSessionInfo.threadReadyStates.getOrDefault(tmpSsi.streamSourceId(), false)) {
				areAllReady = false;
				break;
			}
		}
		return areAllReady;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull InetAddress getClientIpAddr() {
		return rtspSessionInfo.getClientIpAddr().orElseThrow(() -> new IllegalStateException("Client IP address is not set"));
	}

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
		if (! rtspSessionInfo.existsInputSourceObjForMt_nonSetup(RtspMessageType.PLAY)) {  // sanity check
			return;
		}
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.SdpSubStreamInfo tmpSsi = getSubStreamInfo(tmpSubStreamId);
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsi.streamSourceId()).orElseThrow();
			if (! rtspChildThreadMng.ctfosMapContainsKey(tmpSsObj.getId())) {
				continue;
			}
			RtspChildThreadMng.ChildThreadsForOneStream ctfos = rtspChildThreadMng.getCtfosMapValue(tmpSsObj.getId());
			updateCongestionLevel_oneStream(ctfos);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspRequestBasics receiveClientRequestAndRespond()
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException, UdpSocketIoException {
		RtspProtoDataRequest tmpDataRequ = new RtspProtoDataRequest();
		RtspRequestBasics resObj = rtspProtoRequestInputSvc.receiveRequest(tmpDataRequ);

		rtspProtoResponseOutputSvc.sendResponse(resObj, tmpDataRequ);
		return resObj;
	}

	private boolean handleSuccessfulRequest(@NonNull RtspRequestBasics rtspRequestBasics) throws TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleSuccessfulRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		RtspSessionState nextState = rtspSessionInfo.sessionState;

		//
		Objects.requireNonNull(
				rtspRequestBasics.requestUrlInputOrStreamSource,
				FNC_NAME + ": rtspRequestBasics.requestUrlInputOrStreamSource is null"
			);

		//
		switch (rtspRequestBasics.messageType) {
			case RtspMessageType.SETUP:
				rtspSessionInfo.isPlaybackPaused = false;
				//
				Objects.requireNonNull(
						rtspRequestBasics.requestUrlInputOrStreamSource.subStreamId,
						FNC_NAME + ": rtspRequestBasics subStreamId is null"
					);
				final String tmpSubStreamId = rtspRequestBasics.requestUrlInputOrStreamSource.subStreamId;
				// sanity check
				if (! RtspStaticSessionInfo.existsSetupSubStream(tmpSubStreamId)) {
					// this should never happen
					logError(FNC_NAME, "SETUP failed");
					return false;  // tear down the session
				}
				RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStream =
						RtspStaticSessionInfo.getSetupSubStreamOrThrow(FNC_NAME, tmpSubStreamId);
				try {
					tmpSetupSubStream.isTransportValid(
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
				nextState = RtspSessionState.READY;
				break;
			case RtspMessageType.PLAY:
				final String tmpIsIdPlay = rtspRequestBasics.requestUrlInputOrStreamSource.inputSourceId;
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
				nextState = RtspSessionState.PLAYING;
				break;
			case RtspMessageType.PAUSE:
				final String tmpIsIdPause = rtspRequestBasics.requestUrlInputOrStreamSource.inputSourceId;
				logInfo(FNC_NAME, String.format("Pausing playback for IS='%s'", tmpIsIdPause));
				//
				rtxpTcpReadWrite.setTcpActivityTimeoutForRtspOnly();
				rtspChildThreadMng.pauseOrStopChildThreads(true);
				nextState = RtspSessionState.READY;
				rtspSessionInfo.isPlaybackPaused = true;
				break;
			case RtspMessageType.TEARDOWN:
				nextState = RtspSessionState.INIT;
				rtspSessionInfo.isPlaybackPaused = false;
				break;
		}

		//
		if (rtspSessionInfo.sessionState != nextState) {
			rtspSessionInfo.sessionState = nextState;
			if (rtspSessionInfo.sessionState == RtspSessionState.INIT) {
				return false;  // tear down the session
			}
			logDebug(FNC_NAME, "RTSP state is now " + nextState);
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspStatusCode recvResponseFromClient(@NonNull RtspMessageType requestMessageType)
			throws TcpSocketClosedException, TcpSocketIoException {
		int timeoutCnt = 0;
		RtspResponseBasics respBasics = null;
		while (++timeoutCnt < 100) {
			try {
				respBasics = rtspProtoResponseInputSvc.receiveResponse(requestMessageType);
			} catch (InputStreamNotReadyException ignored) {
				// ignore
			}
		}
		if (respBasics == null) {
			throw new TcpSocketIoException("could not receive response");
		}
		return respBasics.statusCode;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private RtspStaticSessionInfo.@NonNull SdpSubStreamInfo getSubStreamInfo(@NonNull String subStreamId) {
		return RtspStaticSessionInfo.getSdpSubStream(getClientIpAddr(), subStreamId).orElseThrow();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void srtxpRekeyInbound_oneStream(RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos) {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyInbound_oneStream()";

		RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStream = RtspStaticSessionInfo.getSetupSubStreamOrThrow(
				FNC_NAME,
				ctfos.subStreamId
			);

		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				getClientIpAddr(),
				ctfos.subStreamId,
				tmpSetupSubStream.rtspSsrcId
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
		if (! rtspSessionInfo.existsInputSourceObjForMt_nonSetup(RtspMessageType.PLAY)) {
			return;  // we're not ready yet
		}
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
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

	private void srtxpRekeyOutbound_updateThreads(
				RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos,
				@NonNull SrtxpKmd kmd
			) {
		if (ctfos.rtcpThreadSendRecv != null && ctfos.rtcpThreadSendRecv.isRunning()) {
			ctfos.rtcpThreadSendRecv.setNextSrtcpKmdOutbound(kmd);
		}
		if (ctfos.rtpThreadSender != null && ctfos.rtpThreadSender.isRunning()) {
			ctfos.rtpThreadSender.setNextSrtpKmdOutbound(kmd);
		}
		ctfos.srtxpOutboundRekeyingInProgress = true;
	}

	private boolean srtxpRekeyOutbound_mikey_oneStream(
				RtspChildThreadMng.@NonNull ChildThreadsForOneStream ctfos,
				@NonNull RtspKeymgmtKmdsOutbound kmdsOutbound
			) throws TcpSocketIoException, TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound_mikey_oneStream()";

		final String logMsgPrefix = "ss=" + ctfos.streamSourceId + ": ";

		Optional<String> tmpOptRscUrl = rtspSessionInfo.getResourceUrlForMt_onlySetup(ctfos.subStreamId);
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - no Resource URL found for Sub-Stream ID: '" +
					ctfos.subStreamId + "'");
			return false;
		}
		final String resourceUrlForSs = tmpOptRscUrl.get();

		{
			RtspMessageType tmpMt = rtspProtoRequestOutputSvc.sendRequest_options(resourceUrlForSs);
			RtspStatusCode requStatCode = recvResponseFromClient(tmpMt);
			if (requStatCode != RtspStatusCode.OK) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - client does not support OPTIONS request");
				return false;
			}
		}

		//
		Optional<SrtxpKmd> tmpOptNextKmdOutbound = kmdsOutbound.getKmdForSubStream(ctfos.subStreamId);
		if (tmpOptNextKmdOutbound.isEmpty()) {
			logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - no KMD found for Sub-Stream ID: '" +
					ctfos.subStreamId + "'");
			return false;
		}
		final SrtxpKmd tmpNextKmdOutbound = tmpOptNextKmdOutbound.get();

		//
		{
			RtspMessageType tmpMt = rtspProtoRequestOutputSvc.sendRequest_srtxpRekeyOutboundMikey(
					resourceUrlForSs,
					ctfos.subStreamId,
					tmpNextKmdOutbound
				);
			RtspStatusCode requStatCode = recvResponseFromClient(tmpMt);
			if (requStatCode == RtspStatusCode.METHOD_NOT_ALLOWED) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed - client does not support pushing new MK");
				return false;
			}
			if (requStatCode != RtspStatusCode.OK) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed (" + requStatCode + ")");
				return false;
			}
		}

		//
		logInfo(FNC_NAME, logMsgPrefix + "SRTxP re-keying in progress");
		srtxpRekeyOutbound_updateThreads(ctfos, tmpNextKmdOutbound);
		return true;
	}

	private boolean srtxpRekeyOutbound_sdes(
				@NonNull RtspKeymgmtKmdsOutbound kmdsOutbound
			) throws TcpSocketIoException, TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound_sdes()";

		Optional<String> tmpOptRscUrl = rtspSessionInfo.getResourceUrlForMt_nonSetup(RtspMessageType.PLAY);
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, "SRTxP re-keying failed - no Resource URL found");
			return false;
		}
		final String resourceUrl = tmpOptRscUrl.get();

		{
			RtspMessageType tmpMt = rtspProtoRequestOutputSvc.sendRequest_options(resourceUrl);
			RtspStatusCode requStatCode = recvResponseFromClient(tmpMt);
			if (requStatCode != RtspStatusCode.OK) {
				logError(FNC_NAME, "SRTxP re-keying failed - client does not support OPTIONS request");
				return false;
			}
		}

		//
		{
			RtspMessageType tmpMt = rtspProtoRequestOutputSvc.sendRequest_srtxpRekeyOutboundSdes(resourceUrl, kmdsOutbound);
			RtspStatusCode requStatCode = recvResponseFromClient(tmpMt);
			if (requStatCode == RtspStatusCode.METHOD_NOT_ALLOWED) {
				logError(FNC_NAME, "SRTxP re-keying failed - client does not support pushing new MK");
				return false;
			}
			if (requStatCode != RtspStatusCode.OK) {
				logError(FNC_NAME, "SRTxP re-keying failed (" + requStatCode + ")");
				return false;
			}
		}

		//
		logInfo(FNC_NAME, "SRTxP re-keying in progress");
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
			Optional<SrtxpKmd> tmpOptNextKmdOutbound = kmdsOutbound.getKmdForSubStream(ctfos.subStreamId);
			if (tmpOptNextKmdOutbound.isEmpty()) {
				logError(FNC_NAME, "SRTxP re-keying failed - no KMD found for Sub-Stream ID: '" +
						ctfos.subStreamId + "'");
				return false;
			}
			srtxpRekeyOutbound_updateThreads(ctfos, tmpOptNextKmdOutbound.get());
		}
		return true;
	}

	private boolean srtxpRekeyOutbound() throws TcpSocketIoException, TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".srtxpRekeyOutbound()";

		if (! rtspSessionInfo.existsInputSourceObjForMt_nonSetup(RtspMessageType.PLAY)) {
			return true;  // we're not ready yet
		}

		// check sub-streams to see whether any of them need re-keying
		boolean needRekey = false;
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
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
				return true;  // we ignore any other sub-stream for now
			}
			//
			long tmpPktCount = ctfos.rtpThreadSender.getPacketCountOutbound();
			if ((long)((double)tmpPktCount * 0.9) < RtpConstants.SRTXP_REKEYING_INTERVAL_PACKETS_INT) {
				continue;
			}
			//
			final String logMsgPrefix = "ss=" + ctfos.streamSourceId + ": ";
			//
			logDebug(FNC_NAME, String.format("%s90%% of maximum outbound RTP packet count reached: %s (RTCP in %s / out %s)",
					logMsgPrefix,
					Long.toUnsignedString(tmpPktCount),
					ctfos.rtcpThreadSendRecv != null ? Long.toUnsignedString(ctfos.rtcpThreadSendRecv.getPacketCountInbound()) : "-",
					ctfos.rtcpThreadSendRecv != null ? Long.toUnsignedString(ctfos.rtcpThreadSendRecv.getPacketCountOutbound()) : "-"));
			needRekey = true;
		}

		if (! needRekey) {
			return true;
		}

		// generate new KMDs for all sub-streams
		boolean rekeyingProtoIsMikey = true;
		RtspKeymgmtKmdsOutbound kmdsOutbound = new RtspKeymgmtKmdsOutbound();
		int tmpSubStreamNr = 0;
		for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
			++tmpSubStreamNr;
			//
			final String logMsgPrefix = "ss=" + ctfos.streamSourceId + ": ";
			//
			SrtxpKmd tmpNextKmdOutbound;
			try {
				tmpNextKmdOutbound = rtspProtoRequestOutputSvc.generateNewKmdForRekeying(ctfos.subStreamId);
			} catch (RtspInvalidRequestException e) {
				logError(FNC_NAME, logMsgPrefix + "SRTxP re-keying failed: " + e.getMessage());
				return false;  // shutdown the session
			}
			if (tmpSubStreamNr == 1) {
				kmdsOutbound.setKmdForSubStream1(tmpNextKmdOutbound, ctfos.subStreamId);
			} else {
				kmdsOutbound.setKmdForSubStream2(tmpNextKmdOutbound, ctfos.subStreamId);
			}
			rekeyingProtoIsMikey = (rekeyingProtoIsMikey && ! tmpNextKmdOutbound.isForLegacySdes());
		}

		//
		if (rekeyingProtoIsMikey) {
			// send one request per sub-stream
			for (RtspChildThreadMng.ChildThreadsForOneStream ctfos : rtspChildThreadMng.getCtfosMapValuesOnlyRunning()) {
				if (! srtxpRekeyOutbound_mikey_oneStream(ctfos, kmdsOutbound)) {
					return false;  // shutdown the session
				}
			}
			return true;
		}
		// send one request for all sub-streams together
		return srtxpRekeyOutbound_sdes(kmdsOutbound);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop(final int loopCounter)
			throws TcpSocketClosedException, TcpSocketIoException, UdpSocketIoException, InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (rtspSessionInfo.isTransportUdp && rtspTimeoutLastRequ != null) {
			long tmpTimeDiff = Duration.between(rtspTimeoutLastRequ, Instant.now()).toSeconds();
			if (tmpTimeDiff > RtspProtoHighConstants.DEFAULT_RTSP_SESSION_TIMEOUT + SESSION_TIMEOUT_TOLERANCE_SEC) {
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
			RtspRequestBasics rtspRequestBasics = receiveClientRequestAndRespond();
			rtspTimeoutLastRequ = Instant.now();
			if (rtspRequestBasics.statusCode != RtspStatusCode.OK) {
				return true;
			}
			return handleSuccessfulRequest(rtspRequestBasics);
		} catch (InputStreamNotReadyException e1) {
			Thread.sleep(15);
			return true;
		}
	}

}

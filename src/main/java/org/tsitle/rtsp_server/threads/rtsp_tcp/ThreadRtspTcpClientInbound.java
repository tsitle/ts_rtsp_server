package org.tsitle.rtsp_server.threads.rtsp_tcp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.*;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.exceptions.*;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoPlaybackRange;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;
import org.tsitle.rtsp_server.availstreams.AsGetFileTagsInterface;
import org.tsitle.rtsp_server.config.RtspSrvConfigMain;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.*;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.rtsp.*;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.ThreadRtspPlay;

import java.net.Socket;
import java.time.Instant;
import java.util.*;

public final class ThreadRtspTcpClientInbound extends RunnableBase implements RtspChildThreadsCbRtxpTcpInterface {

	private enum MainLoopResult {
		OK,
		ERROR,
		TIMEOUT
	}

	private final @NonNull String threadName;

	private final @NonNull RtspProtoIpAddr fromCtorClientIpAddr = new RtspProtoIpAddr();
	private final boolean fromCtorIsRtspsConnection;

	private final @NonNull RtspSrvConfigMain rtspSrvConfig;
	private final @NonNull String cfgServerNameAndVersion;
	private final @NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull RtspPlayThreadMngInterface playThreadMngInterface;

	private final @NonNull RtspProtoPtrSessionInfo sessionInfoPtr = RtspProtoPtrSessionInfo.ofNewSi();
	private final @NonNull RtspProtoIdSession lastSessionId = RtspProtoIdSession.ofEmpty();

	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final @NonNull RtspProtoRequestInputSvc rtspProtoRequestInputSvc;
	private final @NonNull RtspProtoResponseOutputSvc rtspProtoResponseOutputSvc;
	private final @NonNull RtspParamGetterSetterSvc rtspParamGetterSetterSvc;

	private @Nullable ThreadRtspPlay threadRtspPlay = null;
	private @Nullable SrtxpRekeySvc srtxpRekeySvc = null;

	private final @NonNull RtspProtoDataCntGetSetParamKvs cachedSetParamValues = new RtspProtoDataCntGetSetParamKvs();

	/** Has the client requested PAUSE? */
	private boolean isPlaybackPaused = false;

	private final @NonNull CancelToken localCancelToken = new CancelToken();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param rtspSrvConfig RTSP server configuration
	 * @param cfgServerNameAndVersion RTSP server software name and version
	 * @param availableStreamsInterface Available streams instance (only required for responses from the server)
	 * @param globalSessionInfoInterface Global Session Info service
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 * @param isRtspsConnection True if the RTSP connection is over TLS/SSL
	 */
	public ThreadRtspTcpClientInbound(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspSrvConfigMain rtspSrvConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull AsGetFileTagsInterface asGetFileTagsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@NonNull RtspPlayThreadMngInterface playThreadMngInterface,
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp,
				boolean isRtspsConnection
			) {
		super(logMsgInterface, cancelToken);

		//
		this.threadName = "RTSP_CMNG#c" + clientConnectionNr;

		this.rtspSrvConfig = rtspSrvConfig;
		this.cfgServerNameAndVersion = cfgServerNameAndVersion;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
		this.availableStreamsInterface = availableStreamsInterface;
		this.playThreadMngInterface = playThreadMngInterface;

		this.rtxpTcpReadWrite = new RtxpTcpReadWrite(rtspSocketTcp);

		//
		this.fromCtorClientIpAddr.setIpAddr(rtspSocketTcp.getInetAddress());
		this.fromCtorClientIpAddr.writeProtect();
		this.fromCtorIsRtspsConnection = isRtspsConnection;

		//
		RtspProtoDataCntMessageTypes cfgSrvSuppIncomingMts = new RtspProtoDataCntMessageTypes();
		cfgSrvSuppIncomingMts.putAllMts(RtspServerConstants.SERVER_SUPPORTED_INCOMING_MESSAGE_TYPES);
		cfgSrvSuppIncomingMts.writeProtect();

		//
		RtspUserAuthSvc userAuthSvc = new RtspUserAuthSvc(
				logMsgInterface,
				rtspSrvConfig,
				availableStreamsInterface,
				globalSessionInfoInterface
			);

		//
		this.rtspParamGetterSetterSvc = new RtspParamGetterSetterSvc(asGetFileTagsInterface);

		//
		this.rtspProtoRequestInputSvc = new RtspProtoRequestInputSvc(
				logMsgInterface,
				true,
				rtspSrvConfig.getLogLevel(),
				cfgSrvSuppIncomingMts,
				RtspServerConstants.SERVER_SUPPORTED_FEATURES,
				Set.of(),
				rtspSrvConfig.getIsDebugPrintRtspRcvd(),
				rtspSrvConfig.getIsDebugDisableTransportUdp(),
				this.sessionInfoPtr,
				userAuthSvc,
				availableStreamsInterface,
				globalSessionInfoInterface,
				this.rtspParamGetterSetterSvc,
				this.rtxpTcpReadWrite
			);
		this.rtspProtoResponseOutputSvc = new RtspProtoResponseOutputSvc(
				logMsgInterface,
				false,
				cfgServerNameAndVersion,
				RtspParamGetterSetterSvc.CONTENT_LANGUAGE,
				cfgSrvSuppIncomingMts,
				RtspProtoHighConstants.DEFAULT_SUBSTREAM_ID_PREFIX,
				rtspSrvConfig.getIsDebugPrintRtspSdpSent(),
				rtspSrvConfig.getIsDebugPrintRtspSent(),
				rtspSrvConfig.getIsDebugDisableTransportUdp(),
				this.sessionInfoPtr,
				availableStreamsInterface,
				globalSessionInfoInterface,
				this.rtspParamGetterSetterSvc,
				(@NonNull String clientUserAgent) -> clientUserAgent.startsWith("Lavf"),  // FFplay doesn't support MIKEY
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
		try {
			sessionInfoPtr.ptr().setClientIpAddr(fromCtorClientIpAddr);
			sessionInfoPtr.ptr().setIsRtspsConnection(fromCtorIsRtspsConnection);
		} catch (RtspProtoSessionInfoException e) {
			logError(FNC_NAME, "RtspSessionInfoException: " + e.getMessage());
			return;
		}

		//
		isRunning.set(true);
		logInfo(FNC_NAME, String.format("Serving RTSP%s to %s:%d",
				sessionInfoPtr.ptr().getIsRtspsConnection() ? "S" : "",
				sessionInfoPtr.ptr().getClientIpAddr().getIpAddrStr().orElseThrow(), rtxpTcpReadWrite.getSocketRemotePort()));

		//
		try {
			int loopCounter = 0;
			MainLoopResult mlr = MainLoopResult.OK;
			while (! (hasBeenRequestedToStop() || rtxpTcpReadWrite.isSocketClosed() || localCancelToken.cancelled)) {
				if ((mlr = mainLoop(++loopCounter)) != MainLoopResult.OK) {
					break;
				}
			}
			// shut down the play thread if we are using TCP transport for RTP/RTCP
			if (! sessionInfoPtr.ptr().getIdSession().isEmpty() && threadRtspPlay != null &&
					(! sessionInfoPtr.ptr().getIsTransportUdp() || mlr == MainLoopResult.TIMEOUT)) {
				playThreadMngInterface.shutdownThreadBySessionId(sessionInfoPtr.ptr().getIdSession());
			}
		} catch (TcpSocketClosedException e) {
			logDebug(FNC_NAME, "TcpSocketClosedException caught");
		} catch (TcpSocketIoException e) {
			logDebug(FNC_NAME, "TcpSocketIoException caught: " + e.getMessage());
		} catch (TcpSocketActivityTimeoutException e) {
			logDebug(FNC_NAME, "TcpSocketActivityTimeoutException caught");
			if (! sessionInfoPtr.ptr().getIdSession().isEmpty()) {
				globalSessionInfoInterface.deleteSessionInfo(sessionInfoPtr.ptr().getIdSession());
			}
		} catch (UdpSocketIoException e) {
			logError(FNC_NAME, "UdpSocketIoException caught: " + e.getMessage());
		} catch (InterruptedException e2) {
			logError(FNC_NAME, "InterruptedException caught");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			//e.printStackTrace();
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			logInfo(FNC_NAME, String.format("Closing RTSP%s for %s:%d",
					sessionInfoPtr.ptr().getIsRtspsConnection() ? "S" : "",
					sessionInfoPtr.ptr().getClientIpAddr().getIpAddrStr().orElseThrow(), rtxpTcpReadWrite.getSocketRemotePort()));
			// close RTSP client socket and stream reader/writer
			rtxpTcpReadWrite.closeSocket();
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	public void stopThread() {
		localCancelToken.cancelled = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public boolean isTcpConnectionAlive() {
		return (! rtxpTcpReadWrite.isSocketClosed());
	}

	@Override
	public void cbSendRtpBinaryOverTcp(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}
		rtxpTcpReadWrite.writeRtpBinary(bufView, channNr);
	}

	@Override
	public boolean cbCanReadRtcpOverTcp(@NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}
		return rtxpTcpReadWrite.canReadRtcp(channNr);
	}

	@Override
	public boolean cbReadRtcpBinaryOverTcp(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException, TcpSocketActivityTimeoutException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}
		return rtxpTcpReadWrite.readRtcpBinary(buf, channNr);
	}

	@Override
	public void cbSendRtcpBinaryOverTcp(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}
		rtxpTcpReadWrite.writeRtcpBinary(bufView, channNr);
	}

	@Override
	public synchronized void cbNotifyRcvdRtcpRrPacketOverTcp(@NonNull Instant time) {
		rtxpTcpReadWrite.resetTcpActivityTimeoutTimer();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public boolean usesSessionId(@NonNull RtspProtoIdSession idSession) {
		return idSession.equals(sessionInfoPtr.ptr().getIdSession());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspRequestBasics receiveClientRequestAndRespond()
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException,
					RtspProtoTcpSocketNotReadyException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveClientRequestAndRespond()";

		cachedSetParamValues.clear();

		sessionInfoPtr.ptr().globalWriteLock();
		try {
			RtspRequestBasics resObj = rtspProtoRequestInputSvc.receiveRequestFromClient();
			//
			if (lastSessionId.isEmpty() && ! sessionInfoPtr.ptr().getIdSession().isEmpty()) {
				/*
				 * The Session Info pointer has changed because an existing Session Info object has been loaded.
				 */
				lastSessionId.copyFrom(sessionInfoPtr.ptr().getIdSession());
				//
				updateRtxpTcpRwSettings();
			}

			//
			try {
				if (resObj.statusCode == RtspProtoStatusCode.OK && resObj.messageType == RtspProtoMessageType.PLAY &&
						sessionInfoPtr.ptr().getClientPlaybackRangeValue().isPresent() &&
						threadRtspPlay != null) {
					RtspProtoPlaybackRange tmpPbRange = sessionInfoPtr.ptr().getClientPlaybackRangeValue().get();
					if (tmpPbRange.isAbsoluteTime()) {
						resObj.statusCode = RtspProtoStatusCode.INVALID_RANGE;
					} else if (! tmpPbRange.isEmpty()) {
						if (! threadRtspPlay.seekStream(tmpPbRange.getRelativeTimeStartSecs())) {
							resObj.statusCode = RtspProtoStatusCode.INVALID_RANGE;
						}
					}
				}
				//
				rtspProtoResponseOutputSvc.sendResponse(resObj);
			} catch (RtspProtoSendResponseFailedException e) {
				logError(FNC_NAME, "RtspProtoSendResponseFailedException caught: " + e.getMessage());
				return resObj;
			} catch (TcpSocketClosedException | TcpSocketIoException e) {
				if (resObj.messageType != RtspProtoMessageType.TEARDOWN) {  // VLC closes the socket before we can send a response
					throw e;
				}
			}

			//
			if (resObj.statusCode == RtspProtoStatusCode.OK && resObj.messageType == RtspProtoMessageType.SET_PARAMETER) {
				Optional<RtspProtoDataCntGetSetParamKvs> tmpOptKvs = sessionInfoPtr.ptr().getRhSetParamValues();
				tmpOptKvs.ifPresent(cachedSetParamValues::copyFrom);

				//
				if (! lastSessionId.isEmpty()) {
					playThreadMngInterface.updateThreadsSessionInfoBySessionId(lastSessionId, sessionInfoPtr.ptr());
				}
			}

			return resObj;
		} finally {
			sessionInfoPtr.ptr().globalWriteUnlock();
		}
	}

	private boolean handleSuccessfulRequest(@NonNull RtspRequestBasics rtspRequestBasics) throws TcpSocketClosedException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleSuccessfulRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		// sanity checks
		if (rtspRequestBasics.rscUrl.getUrlStr().isEmpty()) {
			throw new IllegalStateException(FNC_NAME + ": rtspRequestBasics.rscUrl.urlStr is empty");
		}
		if (rtspRequestBasics.rscUrl.idInputSource.isEmpty()) {
			throw new IllegalStateException(FNC_NAME + ": rtspRequestBasics.rscUrl.idInputSource is empty");
		}

		//
		final RtspProtoIdInputSource tmpIdIs = rtspRequestBasics.rscUrl.idInputSource;

		//
		switch (rtspRequestBasics.messageType) {
			case RtspProtoMessageType.SET_PARAMETER:
				rtspParamGetterSetterSvc.updateSessionId(sessionInfoPtr.ptr().getIdSession());
				for (Map.Entry<@NonNull String, @NonNull String> entry : cachedSetParamValues.getParamKvsEntrySet()) {
					try {
						rtspParamGetterSetterSvc.setRtspParameter(
								false,
								sessionInfoPtr.ptr().getIdSession(),
								cachedSetParamValues.getIdInputSource(),
								cachedSetParamValues.getIdSubStream(),
								cachedSetParamValues.getContentLang(),
								entry.getKey(),
								entry.getValue()
							);
					} catch (RtspProtoRtspParamUnknownException | RtspProtoRtspParamInvalidValueException e) {
						// this cannot happen because the parameters have already been validated
					}
				}
				cachedSetParamValues.clear();
				break;
			case RtspProtoMessageType.SETUP:
				isPlaybackPaused = false;
				//
				final RtspProtoIdSubStream tmpIdSubStream = rtspRequestBasics.rscUrl.idSubStream;
				// sanity checks
				if (tmpIdSubStream.isEmpty()) {
					throw new IllegalStateException(FNC_NAME + ": rtspRequestBasics.rscUrl.idSubStream is empty");
				}
				if (! sessionInfoPtr.ptr().getDescrSetupInfoHaveSetupForSubStreamId(tmpIdSubStream)) {
					// this should never happen
					logError(FNC_NAME, "SETUP failed");
					return false;  // tear down the session
				}
				break;
			case RtspProtoMessageType.PLAY:
				logInfo(FNC_NAME, String.format(
						"%s playback for IS='%s' (w/%s SRTP, %s, w/%s SSL)",
						isPlaybackPaused ? "Resuming" : "Starting",
						tmpIdIs.getIdStr().orElse("-unset-"),
						sessionInfoPtr.ptr().getIsTransportSrtpSrtcp() ? "" : "o",
						sessionInfoPtr.ptr().getIsTransportUdp() ? "UDP" : "TCP",
						sessionInfoPtr.ptr().getIsRtspsConnection() ? "" : "o"));
				//
				updateRtxpTcpRwSettings();
				//
				if (isPlaybackPaused) {
					isPlaybackPaused = false;
					if (threadRtspPlay != null) {
						threadRtspPlay.unpauseChildThreads();
					}
				} else {
					if (threadRtspPlay == null) {
						startOrGetThreadRtspPlay();
						if (threadRtspPlay == null) {
							throw new IllegalStateException(FNC_NAME + ": threadRtspPlay is null");
						}
					}
					threadRtspPlay.startChildThreads();
				}
				break;
			case RtspProtoMessageType.PAUSE:
				logInfo(FNC_NAME, String.format("Pausing playback for IS='%s'", tmpIdIs.getIdStr().orElse("-unset-")));
				//
				rtxpTcpReadWrite.setTcpActivityTimeoutForRtspOnly();
				if (threadRtspPlay != null) {
					threadRtspPlay.pauseOrStopChildThreads(true);
				}
				isPlaybackPaused = true;
				break;
			case RtspProtoMessageType.TEARDOWN:
				isPlaybackPaused = false;
				// stop sending/receiving RTP/RTCP packets
				if (threadRtspPlay != null) {
					logDebug(FNC_NAME, "stopping PLAY thread");
					threadRtspPlay = null;
					playThreadMngInterface.shutdownThreadBySessionId(sessionInfoPtr.ptr().getIdSession());
				}
				// delete the Session Info from the global storage
				RtspProtoIdSession tmpIdSessionBckp = sessionInfoPtr.ptr().getIdSession().clone();
				sessionInfoPtr.ptr().clearAfterTeardown();
				globalSessionInfoInterface.deleteSessionInfo(tmpIdSessionBckp);
				break;
		}

		//
		if (sessionInfoPtr.ptr().moveToNextSessionState(rtspRequestBasics.messageType)) {
			if (sessionInfoPtr.ptr().getSessionState() == RtspProtoSessionState.INIT) {
				return false;  // tear down the session
			}
			logDebug(FNC_NAME, "RTSP state is now " + sessionInfoPtr.ptr().getSessionState());
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull SrtxpRekeySvc buildSrtxpRekeySvc() {
		if (logMsgInterface == null) {
			throw new IllegalStateException("logMsgInterface is null");
		}
		if (threadRtspPlay == null) {
			throw new IllegalStateException("threadRtspPlay is null");
		}
		return new SrtxpRekeySvc(
				logMsgInterface,
				rtspSrvConfig,
				cfgServerNameAndVersion,
				RtspParamGetterSetterSvc.CONTENT_LANGUAGE,
				sessionInfoPtr,
				threadRtspPlay,
				rtxpTcpReadWrite,
				availableStreamsInterface,
				globalSessionInfoInterface
			);
	}

	private void startOrGetThreadRtspPlay() {
		final String FNC_NAME = getClass().getSimpleName() + ".startOrGetThreadRtspPlay()";

		if (logMsgInterface == null) {
			throw new IllegalStateException(FNC_NAME + ": logMsgInterface is null");
		}
		threadRtspPlay = playThreadMngInterface.startOrGetThreadRtspPlay(
				sessionInfoPtr.ptr(),
				availableStreamsInterface,
				this
			);
		if (threadRtspPlay == null) {
			logError(FNC_NAME, "could not start RTSP play thread");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void updateRtxpTcpRwSettings() {
		rtxpTcpReadWrite.setIsRtpRtcpAllowed(! sessionInfoPtr.ptr().getIsTransportUdp());
		if (sessionInfoPtr.ptr().getIsTransportUdp()) {
			rtxpTcpReadWrite.setTcpActivityTimeoutForRtspOnly();
		} else {
			rtxpTcpReadWrite.setTcpActivityTimeoutForRtxp();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull MainLoopResult mainLoop(final int loopCounter)
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException,
					UdpSocketIoException, InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (loopCounter % 11 == 0 && sessionInfoPtr.ptr().getIsTransportUdp()) {  // 11^=roughly once every 1s
			long tmpTimeDiff = sessionInfoPtr.ptr().getLastIncomingRequestTimeDeltaSeconds();
			if (tmpTimeDiff > RtspProtoHighConstants.DEFAULT_RTSP_SESSION_TIMEOUT +
					RtspProtoHighConstants.SESSION_TIMEOUT_TOLERANCE_SEC) {
				logWarn(FNC_NAME, "RTSP session sid=" + sessionInfoPtr.ptr().getIdSession().getIdStr().orElse("-unset-") +
						" timeout after " + tmpTimeDiff + " seconds");
				return MainLoopResult.TIMEOUT;  // terminate session
			}
		}

		//
		if (threadRtspPlay == null && sessionInfoPtr.ptr().getSessionState() == RtspProtoSessionState.PLAYING) {
			startOrGetThreadRtspPlay();
			if (threadRtspPlay == null) {
				throw new IllegalStateException(FNC_NAME + ": threadRtspPlay is null");
			}
		}

		//
		if (threadRtspPlay != null && sessionInfoPtr.ptr().getIsTransportSrtpSrtcp()) {
			if (srtxpRekeySvc == null) {
				srtxpRekeySvc = buildSrtxpRekeySvc();
			}
			if (loopCounter % 13 == 0) {  // 13^=roughly once every 1s
				srtxpRekeySvc.srtxpRekeyInbound();
			} else if (loopCounter % 89 == 0) {  // 89^=roughly once every 5s
				if (! srtxpRekeySvc.srtxpRekeyOutbound()) {
					return MainLoopResult.ERROR;  // terminate session
				}
			}
		}

		//
		try {
			RtspRequestBasics rtspRequestBasics = receiveClientRequestAndRespond();
			if (rtspRequestBasics.statusCode != RtspProtoStatusCode.OK) {
				return MainLoopResult.OK;
			}
			return (handleSuccessfulRequest(rtspRequestBasics) ? MainLoopResult.OK : MainLoopResult.ERROR);
		} catch (RtspProtoTcpSocketNotReadyException e1) {
			Thread.sleep(100 - 15);
			return MainLoopResult.OK;
		}
	}

}

package org.tsitle.rtsp_server.threads.rtsp_tcp_client_inbound;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.helpers.TimestampEpochNs;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamInvalidValueException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoRtspParamUnknownException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSendResponseFailedException;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoTcpChannelNr;
import org.tsitle.rtsp_server.config.RtspConfig;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.*;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamNotReadyException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.exceptions.UdpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.rtsp.*;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoSessionState;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;
import org.tsitle.rtsp_server.threads.rtsp_play.ChildThreadsForOneStream;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtcpFromRtpInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.RtspChildThreadsCbRtxpTcpInterface;
import org.tsitle.rtsp_server.threads.rtsp_play.ThreadRtspPlay;

import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class ThreadRtspTcpClientInbound extends RunnableBase implements RtspChildThreadsCbRtcpFromRtpInterface, RtspChildThreadsCbRtxpTcpInterface {

	/** RTSP session timeout tolerance in seconds. Sometimes even compliant clients fail to send a keep-alive message in time. */
	private static final int SESSION_TIMEOUT_TOLERANCE_SEC = 15;

	private final @NonNull String threadName;

	private final @NonNull RtspProtoIpAddr fromCtorClientIpAddr = new RtspProtoIpAddr();
	private final boolean fromCtorIsRtspsConnection;

	private final @NonNull CancelToken cancelToken;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull String cfgServerNameAndVersion;
	private final @NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc;
	private final @NonNull RtspStartPlayThreadInterface startPlayThreadInterface;
	private final int clientConnectionNr;

	private final @NonNull RtspProtoSessionInfo rtspSessionInfo = new RtspProtoSessionInfo();

	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final @NonNull RtspAvailableStreamsSvc availableStreamsSvc;
	private final @NonNull RtspProtoRequestInputSvc rtspProtoRequestInputSvc;
	private final @NonNull RtspProtoResponseOutputSvc rtspProtoResponseOutputSvc;
	private final @NonNull RtspParamGetterSetterSvc rtspParamGetterSetterSvc;

	private @Nullable Instant rtspTimeoutLastRequ = null;

	private @Nullable ThreadRtspPlay threadRtspPlay = null;
	private @Nullable SrtxpRekeySvc srtxpRekeySvc = null;

	private final RtspProtoDataCntGetSetParamKvs cachedSetParamValues = new RtspProtoDataCntGetSetParamKvs();

	private final Map<@NonNull RtspProtoIdXsrc, @NonNull ChildThreadsForOneStream> cacheChildThreadsPerSsrcMap = new HashMap<>();

	/** Has the client requested PAUSE? */
	private boolean isPlaybackPaused = false;

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param rtspConfig RTSP configuration
	 * @param cfgServerNameAndVersion RTSP server software name and version
	 * @param globalSessionInfoSvc Global Session Info service
	 * @param clientConnectionNr Client connection number
	 * @param rtspSocketTcp RTSP TCP socket for client communication
	 * @param isRtspsConnection True if the RTSP connection is over TLS/SSL
	 */
	public ThreadRtspTcpClientInbound(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull RtspConfig rtspConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspProtoGlobalSessionInfoSvc globalSessionInfoSvc,
				@NonNull RtspStartPlayThreadInterface startPlayThreadInterface,
				int clientConnectionNr,
				@NonNull Socket rtspSocketTcp,
				boolean isRtspsConnection
			) {
		super(logMsgInterface, cancelToken);

		//
		this.threadName = "RTSP_CMNG#c" + clientConnectionNr;

		this.cancelToken = cancelToken;
		this.rtspConfig = rtspConfig;
		this.cfgServerNameAndVersion = cfgServerNameAndVersion;
		this.globalSessionInfoSvc = globalSessionInfoSvc;
		this.startPlayThreadInterface = startPlayThreadInterface;
		this.clientConnectionNr = clientConnectionNr;

		this.rtxpTcpReadWrite = new RtxpTcpReadWrite(rtspSocketTcp);

		//
		this.fromCtorClientIpAddr.setIpAddr(rtspSocketTcp.getInetAddress());
		this.fromCtorClientIpAddr.writeProtect();
		this.fromCtorIsRtspsConnection = isRtspsConnection;

		//
		this.availableStreamsSvc = new RtspAvailableStreamsSvc(rtspConfig);

		//
		RtspProtoDataCntMessageTypes cfgSrvSuppIncomingMts = new RtspProtoDataCntMessageTypes();
		cfgSrvSuppIncomingMts.putAllMts(RtspServerConstants.SERVER_SUPPORTED_INCOMING_MESSAGE_TYPES);
		cfgSrvSuppIncomingMts.writeProtect();

		//
		RtspUserAuthSvc userAuthSvc = new RtspUserAuthSvc(
				logMsgInterface,
				rtspConfig,
				this.rtspSessionInfo,
				this.availableStreamsSvc,
				globalSessionInfoSvc
			);

		//
		this.rtspParamGetterSetterSvc = new RtspParamGetterSetterSvc();

		//
		this.rtspProtoRequestInputSvc = new RtspProtoRequestInputSvc(
				logMsgInterface,
				true,
				rtspConfig.getLogLevel(),
				cfgSrvSuppIncomingMts,
				RtspServerConstants.SERVER_SUPPORTED_FEATURES,
				Set.of(),
				rtspConfig.getIsDebugPrintRtspRcvd(),
				rtspConfig.getIsDebugDisableTransportUdp(),
				this.rtspSessionInfo,
				userAuthSvc,
				this.availableStreamsSvc,
				globalSessionInfoSvc,
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
				rtspConfig.getIsDebugPrintRtspSdpSent(),
				rtspConfig.getIsDebugPrintRtspSent(),
				rtspConfig.getIsDebugDisableTransportUdp(),
				this.rtspSessionInfo,
				this.availableStreamsSvc,
				globalSessionInfoSvc,
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
			rtspSessionInfo.setClientIpAddr(fromCtorClientIpAddr);
			rtspSessionInfo.setIsRtspsConnection(fromCtorIsRtspsConnection);
		} catch (RtspProtoSessionInfoException e) {
			logError(FNC_NAME, "RtspSessionInfoException: " + e.getMessage());
			return;
		}

		//
		isRunning.set(true);
		logInfo(FNC_NAME, String.format("Serving RTSP%s to %s:%d",
				rtspSessionInfo.getIsRtspsConnection() ? "S" : "",
				rtspSessionInfo.getClientIpAddr().getIpAddrStr().orElseThrow(), rtxpTcpReadWrite.getSocketRemotePort()));

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
			//e.printStackTrace();
			logError(FNC_NAME, "Exception: " + e.getMessage());
		} finally {
			logInfo(FNC_NAME, String.format("Closing RTSP%s for %s:%d",
					rtspSessionInfo.getIsRtspsConnection() ? "S" : "",
					rtspSessionInfo.getClientIpAddr().getIpAddrStr().orElseThrow(), rtxpTcpReadWrite.getSocketRemotePort()));
			//
			logDebug(FNC_NAME, "stopping thread");
			// stop sending/receiving RTP/RTCP packets
			if (threadRtspPlay != null) {
				threadRtspPlay.pauseOrStopChildThreads(false);
			}
			// close RTSP client socket and stream reader/writer
			rtxpTcpReadWrite.closeSocket();
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void cbSendRtpBinaryOverTcp(@NonNull BufferView bufView, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}
		rtxpTcpReadWrite.writeRtpBinary(bufView, channNr);
	}

	@Override
	public boolean cbCanReadRtcpOverTcp(@NonNull RtspProtoTcpChannelNr channNr) throws TcpSocketIoException, TcpSocketClosedException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}
		return rtxpTcpReadWrite.canReadRtcp(channNr);
	}

	@Override
	public boolean cbReadRtcpBinaryOverTcp(@NonNull BufferExt buf, @NonNull RtspProtoTcpChannelNr channNr)
			throws TcpSocketIoException, TcpSocketClosedException {
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
	public synchronized void cbSendRtcpPackets(@NonNull RtspProtoIdXsrc ssrcId, @NonNull BufferExt rtcpPacketsBuf) {
		final String FNC_NAME = getClass().getSimpleName() + ".cbSendRtcpPackets()";

		if (threadRtspPlay == null) {
			throw new IllegalStateException("threadRtspPlay is null");
		}

		ChildThreadsForOneStream ctfosToUse = null;
		if (cacheChildThreadsPerSsrcMap.containsKey(ssrcId)) {
			ctfosToUse = cacheChildThreadsPerSsrcMap.get(ssrcId);
		} else {
			for (RtspProtoRscUrl tmpRscUrl : rtspSessionInfo.getDescrSetupInfoRscUrls()) {
				if (! threadRtspPlay.ctfosMapContainsKey(tmpRscUrl.idSubStream)) {
					continue;
				}
				try {
					if (! rtspSessionInfo.getDescrSetupInfoSsrcOutboundBySubStreamsId(tmpRscUrl.idSubStream).equals(ssrcId)) {
						continue;
					}
				} catch (RtspProtoSessionInfoException e) {
					continue;
				}
				ctfosToUse = threadRtspPlay.getCtfosMapValue(tmpRscUrl.idSubStream);
				cacheChildThreadsPerSsrcMap.put(ssrcId.clone(), ctfosToUse);
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

	@Override
	public synchronized void cbRcvdRtcpRrPacket(@NonNull Instant time) {
		rtxpTcpReadWrite.resetTcpActivityTimeoutTimer();
		rtspTimeoutLastRequ = TimestampEpochNs.ofInstant(time).toInstant().orElseThrow();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspRequestBasics receiveClientRequestAndRespond()
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveClientRequestAndRespond()";

		cachedSetParamValues.clear();

		//
		RtspRequestBasics resObj = rtspProtoRequestInputSvc.receiveRequestFromClient(rtspSessionInfo.getClientIpAddr());

		try {
			rtspProtoResponseOutputSvc.sendResponse(resObj);
		} catch (RtspProtoSendResponseFailedException e) {
			logError(FNC_NAME, "RtspProtoSendResponseFailedException caught: " + e.getMessage());
			return resObj;
		}

		//
		if (resObj.statusCode == RtspProtoStatusCode.OK && resObj.messageType == RtspProtoMessageType.SET_PARAMETER) {
			Optional<RtspProtoDataCntGetSetParamKvs> tmpOptKvs = rtspSessionInfo.getRhSetParamValues();
			tmpOptKvs.ifPresent(cachedSetParamValues::copyFrom);
		}
		//
		return resObj;
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
				rtspParamGetterSetterSvc.updateSessionId(rtspSessionInfo.getIdSession());
				for (Map.Entry<@NonNull String, @NonNull String> entry : cachedSetParamValues.getParamKvsEntrySet()) {
					try {
						rtspParamGetterSetterSvc.setRtspParameter(
								false,
								rtspSessionInfo.getIdSession(),
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
				if (! rtspSessionInfo.getDescrSetupInfoHaveSetupForSubStreamId(tmpIdSubStream)) {
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
						rtspSessionInfo.getIsTransportSrtpSrtcp() ? "" : "o",
						rtspSessionInfo.getIsTransportUdp() ? "UDP" : "TCP",
						rtspSessionInfo.getIsRtspsConnection() ? "" : "o"));
				//
				rtxpTcpReadWrite.setIsRtpRtcpAllowed(! rtspSessionInfo.getIsTransportUdp());
				if (rtspSessionInfo.getIsTransportUdp()) {
					rtxpTcpReadWrite.setTcpActivityTimeoutForRtspOnly();
				} else {
					rtxpTcpReadWrite.setTcpActivityTimeoutForRtxp();
				}
				if (isPlaybackPaused) {
					isPlaybackPaused = false;
					if (threadRtspPlay == null) {
						throw new IllegalStateException("threadRtspPlay is null");
					}
					threadRtspPlay.unpauseChildThreads();
				} else {
					if (threadRtspPlay == null) {
						startThreadRtspPlay();
						if (threadRtspPlay == null) {
							throw new IllegalStateException("threadRtspPlay is null");
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
				//
				rtspSessionInfo.clearAfterTeardown();
				break;
		}

		//
		if (rtspSessionInfo.moveToNextSessionState(rtspRequestBasics.messageType)) {
			if (rtspSessionInfo.getSessionState() == RtspProtoSessionState.INIT) {
				return false;  // tear down the session
			}
			logDebug(FNC_NAME, "RTSP state is now " + rtspSessionInfo.getSessionState());
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
				rtspConfig,
				cfgServerNameAndVersion,
				RtspParamGetterSetterSvc.CONTENT_LANGUAGE,
				rtspSessionInfo,
				threadRtspPlay,
				rtxpTcpReadWrite,
				availableStreamsSvc,
				globalSessionInfoSvc
			);
	}

	private void startThreadRtspPlay() {
		if (logMsgInterface == null) {
			throw new IllegalStateException("logMsgInterface is null");
		}
		threadRtspPlay = startPlayThreadInterface.startThreadRtspPlay(
				logMsgInterface,
				cancelToken,
				rtspConfig,
				rtspSessionInfo,
				availableStreamsSvc,
				globalSessionInfoSvc,
				this,
				this,
				clientConnectionNr
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean mainLoop(final int loopCounter)
			throws TcpSocketClosedException, TcpSocketIoException, UdpSocketIoException, InterruptedException {
		final String FNC_NAME = getClass().getSimpleName() + ".mainLoop()";

		if (rtspSessionInfo.getIsTransportUdp() && rtspTimeoutLastRequ != null) {
			long tmpTimeDiff = Duration.between(rtspTimeoutLastRequ, Instant.now()).toSeconds();
			if (tmpTimeDiff > RtspProtoHighConstants.DEFAULT_RTSP_SESSION_TIMEOUT + SESSION_TIMEOUT_TOLERANCE_SEC) {
				logWarn(FNC_NAME, "RTSP session sid=" + rtspSessionInfo.getIdSession().getIdStr().orElse("-unset-") +
						" timeout after " + tmpTimeDiff + " seconds");
				return false;  // terminate session
			}
		}

		//
		if (threadRtspPlay != null && rtspSessionInfo.getIsTransportSrtpSrtcp()) {  // 83^=roughly once every 5s
			if (srtxpRekeySvc == null) {
				srtxpRekeySvc = buildSrtxpRekeySvc();
			}
			if (loopCounter % 83 == 0) {  // 83^=roughly once every 5s
				srtxpRekeySvc.srtxpRekeyInbound();
			} else if (loopCounter % 89 == 0) {
				if (! srtxpRekeySvc.srtxpRekeyOutbound()) {
					return false;  // terminate session
				}
			}
		}

		//
		try {
			RtspRequestBasics rtspRequestBasics = receiveClientRequestAndRespond();
			rtspTimeoutLastRequ = Instant.now();
			if (rtspRequestBasics.statusCode != RtspProtoStatusCode.OK) {
				return true;
			}
			return handleSuccessfulRequest(rtspRequestBasics);
		} catch (InputStreamNotReadyException e1) {
			Thread.sleep(100 - 15);
			return true;
		}
	}

}

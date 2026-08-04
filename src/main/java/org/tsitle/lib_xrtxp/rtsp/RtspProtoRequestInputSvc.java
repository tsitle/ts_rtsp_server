package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketActivityTimeoutException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoCannotFindIpFromRscUrlException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidRequestException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoTcpSocketNotReadyException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.request.RtspProtoHighRequestConsumer;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterSetterInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoUserAuthInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspConnectionPolicy;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.request.RtspProtoLowRequestConsumer;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpConsumer;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Service for receiving and processing RTSP requests over a TCP connection.
 */
public final class RtspProtoRequestInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isRequestFromClient;
	private final @NonNull RtspProtoPtrSessionInfo sessionInfoPtr;
	private final @Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final @Nullable RtspProtoRequAuthSvc requAuthSvc;
	private final RtspProtoLowMsgReader rtspProtoLowMsgReader;
	private final RtspProtoLowRequestConsumer rtspProtoLowRequestConsumer;
	private final RtspProtoHighRequestConsumer rtspProtoHighRequestConsumer;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message handling instance
	 * @param isRequestFromClient Is this a request sent by the client?
	 * @param cfgRtxpLogLevel RTxP log level
	 * @param cfgSupportedMessageTypes Supported message types (can but shouldn't be empty)
	 * @param cfgSupportedFeatures Features that the local host supports if it is not a proxy (can be empty)
	 * @param cfgProxySupportedFeatures Features that the local host - which is a proxy - supports (can be empty)
	 * @param cfgIsDebugPrintRtspRcvd Enable printing received RTSP lines for debugging?
	 * @param cfgIsDebugDisableTransportUdp Disable UDP transport for debugging?
	 * @param sessionInfoPtr Session Info pointer
	 * @param userAuthInterface User authentication instance (only required for requests from the client)
	 * @param availableStreamsInterface Available streams instance (only required for requests from the client)
	 * @param globalSessionInfoInterface Global session info instance (only required for requests from the client)
	 * @param parameterSetterInterface Parameter setter instance (can be null)
	 * @param rtxpTcpReadWrite RTxP TCP read/write instance
	 */
	public RtspProtoRequestInputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isRequestFromClient,
				@NonNull RtxpLogLevel cfgRtxpLogLevel,
				@NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes,
				@NonNull Set<@NonNull String> cfgSupportedFeatures,
				@NonNull Set<@NonNull String> cfgProxySupportedFeatures,
				boolean cfgIsDebugPrintRtspRcvd,
				boolean cfgIsDebugDisableTransportUdp,
				@NonNull RtspProtoPtrSessionInfo sessionInfoPtr,
				@Nullable RtspProtoUserAuthInterface userAuthInterface,
				@Nullable RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@Nullable RtspProtoParameterSetterInterface parameterSetterInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite
			) {
		if (isRequestFromClient && userAuthInterface == null) {
			throw new IllegalArgumentException("userAuthInterface must be set for requests from the client");
		}
		if (isRequestFromClient && availableStreamsInterface == null) {
			throw new IllegalArgumentException("availableStreamsInterface must be set for requests from the client");
		}
		if (isRequestFromClient && globalSessionInfoInterface == null) {
			throw new IllegalArgumentException("globalSessionInfoInterface must be set for requests from the client");
		}
		this.logMsgInterface = logMsgInterface;
		this.isRequestFromClient = isRequestFromClient;
		this.sessionInfoPtr = sessionInfoPtr;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;

		//
		RtspProtoSdpConsumer sdpConsumer = new RtspProtoSdpConsumer();

		//
		if (isRequestFromClient) {
			this.requAuthSvc = new RtspProtoRequAuthSvc(
					logMsgInterface,
					cfgRtxpLogLevel,
					userAuthInterface,
					availableStreamsInterface,
					globalSessionInfoInterface
				);
		} else {
			this.requAuthSvc = null;
		}

		//
		this.rtspProtoLowMsgReader = new RtspProtoLowMsgReader(
				logMsgInterface,
				this.rtxpTcpReadWrite,
				cfgIsDebugPrintRtspRcvd
			);
		this.rtspProtoLowRequestConsumer = new RtspProtoLowRequestConsumer(logMsgInterface);
		this.rtspProtoHighRequestConsumer = new RtspProtoHighRequestConsumer(
				logMsgInterface,
				isRequestFromClient,
				cfgSupportedMessageTypes,
				cfgSupportedFeatures,
				cfgProxySupportedFeatures,
				cfgIsDebugDisableTransportUdp,
				sdpConsumer,
				availableStreamsInterface,
				globalSessionInfoInterface,
				parameterSetterInterface
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receive a request from the client.
	 * @return Basic request information
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoTcpSocketNotReadyException If the input stream is not ready
	 */
	public @NonNull RtspRequestBasics receiveRequestFromClient()
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException,
					RtspProtoTcpSocketNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveRequestFromClient()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException(FNC_NAME + ": Requests can only be received from the server");
		}
		if (sessionInfoPtr.ptr().getClientIpAddr().isEmpty()) {
			throw new IllegalArgumentException("Client IP address is empty");
		}

		return internalReceiveRequest(FNC_NAME, sessionInfoPtr.ptr().getClientIpAddr());
	}

	/**
	 * Receive a request from the server.
	 * @return Basic request information
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws RtspProtoTcpSocketNotReadyException If the input stream is not ready
	 */
	public @NonNull RtspRequestBasics receiveRequestFromServer()
			throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException,
					RtspProtoTcpSocketNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveRequestFromServer()";

		if (isRequestFromClient) {
			throw new IllegalArgumentException(FNC_NAME + ": Requests can only be received from the client");
		}

		RtspProtoIpAddr tmpClientIpAddr = RtspProtoIpAddr.ofLoopback();

		return internalReceiveRequest(FNC_NAME, tmpClientIpAddr);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspRequestBasics internalReceiveRequest(
				@NonNull String fncName,
				@NonNull RtspProtoIpAddr clientIpAddr
			) throws TcpSocketClosedException, TcpSocketIoException, TcpSocketActivityTimeoutException,
					RtspProtoTcpSocketNotReadyException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		RtspProtoDataRequest outputDataRequ = new RtspProtoDataRequest();

		// read the raw request from the TCP socket
		RtspProtoLowMsgRaw lowInputRaw = rtspProtoLowMsgReader.readMessage();  // blocks for setSoTimeout() value
		if (! lowInputRaw.readSuccess) {
			RtspRequestBasics resObj = RtspRequestBasics.createUnknown();
			logWarn(fncName, String.format("Receiving RTSP request message failed, rejecting it with code %s",
					resObj.statusCode));
			return resObj;
		}

		// parse the raw request
		RtspProtoHighMsgStructuredRequest msgStructured = rtspProtoLowRequestConsumer.parseMessage(lowInputRaw);
		if (msgStructured.messageType == RtspProtoMessageType.UNKNOWN) {
			RtspRequestBasics resObj = RtspRequestBasics.createUnknown();
			logWarn(fncName, String.format("Received invalid RTSP request message, rejecting it with code %s",
					resObj.statusCode));
			return resObj;
		}
		if (msgStructured.statusCode != RtspProtoStatusCode.OK) {
			RtspRequestBasics resObj = RtspRequestBasics.createKnownWithError(msgStructured.messageType, msgStructured.statusCode);
			logWarn(fncName, String.format("Received invalid RTSP request message (rt=%s), rejecting it with code %s",
					resObj.messageType, resObj.statusCode));
			return resObj;
		}

		//
		RtspProtoIdSession currentIdSession = RtspProtoIdSession.ofEmpty();
		RtspProtoDataCntSessionState currentSessionState = new RtspProtoDataCntSessionState();
		RtspProtoDataCntCseqRequInp ioCseqRequ = new RtspProtoDataCntCseqRequInp();
		RtspProtoDataCntStreamTpMain inpStreamTpMain = new RtspProtoDataCntStreamTpMain();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds = new HashSet<>();

		// load data from Session Info
		loadFromSessionInfo(
				msgStructured.getHeaderSessionId().orElse(null),
				currentIdSession,
				ioCseqRequ,
				inpStreamTpMain,
				inpAvailableSubStreamIds,
				ioSetupInfosStream,
				currentSessionState
			);

		// process the request - without checking authentication
		RtspRequestBasics resObj = rtspProtoHighRequestConsumer.processRequest(
				currentIdSession,
				currentSessionState,
				clientIpAddr,
				ioCseqRequ,
				ioSetupInfosStream,
				inpAvailableSubStreamIds,
				inpStreamTpMain,
				msgStructured,
				outputDataRequ
			);

		// update data in Session Info
		updateSessionInfo_immediate(outputDataRequ, ioCseqRequ);

		//
		if (! resObj.isValid()) {
			logWarn(fncName, String.format("Received invalid RTSP request (rt=%s), rejecting it with code %s (CSeq=%d)",
					resObj.messageType, resObj.statusCode,
					ioCseqRequ.cseqNr_lastRcvd.getCseq32bit().orElse(-1L)));
			return resObj;
		}

		// check whether the client needs to be authenticated and if so, whether he actually is
		if (isRequestFromClient) {
			Objects.requireNonNull(requAuthSvc);
			requAuthSvc.checkAuthorization(
					sessionInfoPtr.ptr(),
					clientIpAddr,
					resObj,
					outputDataRequ.requAuthClient
				);
		}

		// load additional request data
		try {
			loadResourceUrl(resObj, outputDataRequ);
			loadServerIp(msgStructured, resObj, outputDataRequ);
		} catch (RtspProtoInvalidRequestException e) {
			resObj.statusCode = RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
			logWarn(fncName, String.format("%s for RTSP request message (rt=%s), rejecting it with code %s",
					e.getMessage(), resObj.messageType, resObj.statusCode));
		}

		//
		outputDataRequ.writeProtect();

		//
		if (resObj.messageType == RtspProtoMessageType.SETUP && resObj.rscUrl.idSubStream.isEmpty()) {
			resObj.statusCode = RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
			logWarn(fncName, String.format("No Sub-Stream ID for RTSP request message (rt=%s), rejecting it with code %s",
					resObj.messageType, resObj.statusCode));
		}

		// update data in Session Info
		if (! resObj.isValid()) {
			return resObj;
		}
		updateSessionInfo_success(resObj, outputDataRequ, ioSetupInfosStream);

		//
		logDebug(fncName, String.format("Received %s request (CSeq=%d)",
				resObj.messageType, ioCseqRequ.cseqNr_lastRcvd.getCseq32bit().orElse(-1L)));
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void loadResourceUrl(
				@NonNull RtspRequestBasics requBasics,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		outputDataRequ.rrRscUrl.copyFrom(requBasics.rscUrl);
	}

	private void loadServerIp(
				@NonNull RtspProtoHighMsgStructuredRequest msgStructured,
				@NonNull RtspRequestBasics requBasics,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoInvalidRequestException {
		try {
			if (msgStructured.messageType == RtspProtoMessageType.SETUP && requBasics.rscUrl.idSubStream.isEmpty()) {
				throw new RtspProtoInvalidRequestException("Sub-Stream ID is empty");
			}
			outputDataRequ.rrServerIpFromRscUrl.copyFrom(
					RtspProtoSessionInfo.findRtspIpFromResourceUrl(requBasics.rscUrl)
				);
		} catch (RtspProtoCannotFindIpFromRscUrlException e) {
			throw new RtspProtoInvalidRequestException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void loadFromSessionInfo(
				@Nullable RtspProtoIdSession inpOptMsgIdSession,
				@NonNull RtspProtoIdSession outputCurrentIdSession,
				@NonNull RtspProtoDataCntCseqRequInp outputCseqRequ,
				@NonNull RtspProtoDataCntStreamTpMain outputStreamTpMain,
				@NonNull Set<@NonNull RtspProtoIdSubStream> outputAvailableSubStreamIds,
				@NonNull RtspProtoSetupInfosStream outputSetupInfosStream,
				@NonNull RtspProtoDataCntSessionState outputSessionState
			) {
		if (isRequestFromClient && globalSessionInfoInterface != null &&
				sessionInfoPtr.ptr().getIdSession().isEmpty() && inpOptMsgIdSession != null && ! inpOptMsgIdSession.isEmpty()) {
			try {
				globalSessionInfoInterface.loadSessionInfo(inpOptMsgIdSession, sessionInfoPtr);
				sessionInfoPtr.ptr().globalWriteLock();
			} catch (RtspProtoSessionInfoException e) {
				// ignore
			}
		}

		//
		outputCurrentIdSession.copyFrom(sessionInfoPtr.ptr().getIdSession());
		outputCurrentIdSession.writeProtect();

		//
		outputCseqRequ.cseqNr_lastRcvd.copyFrom(sessionInfoPtr.ptr().getCseqNr_requFromRem_lastRcvd());
		outputCseqRequ.cseqNr_expected.copyFrom(sessionInfoPtr.ptr().getCseqNr_requFromRem_expected());
		//
		outputStreamTpMain.copyFrom(sessionInfoPtr.ptr().getStreamTpMain());
		//
		outputSetupInfosStream.copyFrom(sessionInfoPtr.ptr().getDescrSetupInfosStream());
		//
		outputAvailableSubStreamIds.clear();
		outputAvailableSubStreamIds.addAll(sessionInfoPtr.ptr().getDescrAvailableSubStreamIds());
		//
		outputSessionState.setSessionState(sessionInfoPtr.ptr().getSessionState());
	}

	private void updateSessionInfo_immediate(
				@NonNull RtspProtoDataRequest dataRequ,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequ
			) {
		sessionInfoPtr.ptr().setLastIncomingRequestData(dataRequ);

		sessionInfoPtr.ptr().updateLastIncomingRequestTime();

		//
		sessionInfoPtr.ptr().setRtspProtoVersionToUse(dataRequ.getRtspProtoVersionToUse());
		//
		sessionInfoPtr.ptr().setCseqNr_requFromRem_lastRcvd(cseqRequ.cseqNr_lastRcvd);
		sessionInfoPtr.ptr().setCseqNr_requFromRem_expected(cseqRequ.cseqNr_expected);
		//
		if (! dataRequ.getClientUa().isEmpty()) {
			sessionInfoPtr.ptr().setClientUserAgent(dataRequ.getClientUa());
		}
		if (! dataRequ.getServerSoftware().isEmpty()) {
			sessionInfoPtr.ptr().setServerSoftware(dataRequ.getServerSoftware());
		}

		//
		sessionInfoPtr.ptr().setRhInvalidParamNames(dataRequ.rrInvalidParamNames);  // always overwrite
		sessionInfoPtr.ptr().clearRhGetParamNames();
		sessionInfoPtr.ptr().clearRhSetParamValues();

		//
		if (isRequestFromClient && globalSessionInfoInterface != null && ! sessionInfoPtr.ptr().getIdSession().isEmpty()) {
			globalSessionInfoInterface.saveSessionInfo(sessionInfoPtr);
		}
	}

	private void updateSessionInfo_success(
				@NonNull RtspRequestBasics requBasics,
				@NonNull RtspProtoDataRequest dataRequ,
				@NonNull RtspProtoSetupInfosStream setupInfosStream
			) {
		sessionInfoPtr.ptr().setLastIncomingRequestData(dataRequ);  // update again

		//
		if (dataRequ.rrStreamTpMain.getForceRtpRtcpEncryption()) {  // only update one-way
			sessionInfoPtr.ptr().setStreamTpMainForceRtpRtcpEncryption();
		}
		if (dataRequ.rrStreamTpMain.getRtpRtcpEncryptionRequired()) {  // only update one-way
			sessionInfoPtr.ptr().setStreamTpMainRtpRtcpEncryptionRequired();
		}
		if (dataRequ.rrStreamTpMain.getIsTransportUdp()) {  // only update one-way
			sessionInfoPtr.ptr().setStreamTpMainIsTransportUdp();
		}
		if (dataRequ.rrStreamTpMain.getIsTransportSrtpSrtcp()) {  // only update one-way
			sessionInfoPtr.ptr().setStreamTpMainIsTransportSrtpSrtcp();
		}
		//
		sessionInfoPtr.ptr().setDescrSetupInfosStream(setupInfosStream);
		//
		if (requBasics.rscUrl.idSubStream.isEmpty()) {
			sessionInfoPtr.ptr().setLastRequestRscUrl_mainStream(requBasics.rscUrl);
		}

		//
		if (! isRequestFromClient && sessionInfoPtr.ptr().getIdSession().isEmpty()) {
			if (! (sessionInfoPtr.ptr().getIdSession().isReadOnly() || dataRequ.rrIdSession.isEmpty())) {
				sessionInfoPtr.ptr().setSessionId(dataRequ.rrIdSession);
			}
		}

		//
		if (requBasics.messageType == RtspProtoMessageType.GET_PARAMETER) {
			sessionInfoPtr.ptr().setRhGetParamNames(dataRequ.rrGetParamNames);
		} else if (requBasics.messageType == RtspProtoMessageType.SET_PARAMETER) {
			sessionInfoPtr.ptr().setRhSetParamValues(dataRequ.requSetParamValues);
		}

		//
		if (requBasics.messageType == RtspProtoMessageType.OPTIONS) {
			sessionInfoPtr.ptr().setRhRequiredFeatures(dataRequ.requRequiredFeatures);
			sessionInfoPtr.ptr().setRhProxyRequiredFeatures(dataRequ.requProxyRequiredFeatures);
		}

		//
		if (requBasics.messageType == RtspProtoMessageType.ANNOUNCE) {
			sessionInfoPtr.ptr().setRhAnnouncedSdpStc(dataRequ.requAnnouncedSdpStc);
		}

		//
		if (dataRequ.getConnectionPolicy() != RtspConnectionPolicy.NONE) {
			sessionInfoPtr.ptr().setRhConnectionPolicy(dataRequ.getConnectionPolicy());
		}

		//
		if (! dataRequ.getPlaybackRangeValue().isEmpty()) {
			sessionInfoPtr.ptr().setClientPlaybackRangeValue(dataRequ.getPlaybackRangeValue());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

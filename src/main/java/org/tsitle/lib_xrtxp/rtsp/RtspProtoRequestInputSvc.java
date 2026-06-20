package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamNotReadyException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoCannotFindIpFromRscUrlException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidRequestException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.request.RtspProtoHighRequestConsumer;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterSetterInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoUserAuthInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.request.RtspProtoLowRequestConsumer;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpConsumer;

/**
 * Service for receiving and processing RTSP requests over a TCP connection.
 */
public final class RtspProtoRequestInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isRequestFromClient;
	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoRequAuthSvc requAuthSvc;
	private final RtspProtoLowMsgReader rtspProtoLowMsgReader;
	private final RtspProtoLowRequestConsumer rtspProtoLowRequestConsumer;
	private final RtspProtoHighRequestConsumer rtspProtoHighRequestConsumer;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message handling instance
	 * @param isRequestFromClient Is this a request sent by the client?
	 * @param cfgRtxpLogLevel RTxP log level
	 * @param cfgSupportedMessageTypes Supported message types (can but shouldn't be empty)
	 * @param cfgIsDebugPrintRtspRcvd Enable printing received RTSP lines for debugging?
	 * @param cfgIsDebugDisableTransportUdp Disable UDP transport for debugging?
	 * @param rtspSessionInfo RTSP session info
	 * @param userAuthInterface User authentication instance
	 * @param availableStreamsInterface Available streams instance
	 * @param globalSessionInfoInterface Global session info instance
	 * @param parameterSetterInterface Parameter setter instance
	 * @param rtxpTcpReadWrite RTxP TCP read/write instance
	 */
	public RtspProtoRequestInputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isRequestFromClient,
				@NonNull RtxpLogLevel cfgRtxpLogLevel,
				@NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes,
				boolean cfgIsDebugPrintRtspRcvd,
				boolean cfgIsDebugDisableTransportUdp,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspProtoUserAuthInterface userAuthInterface,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@Nullable RtspProtoParameterSetterInterface parameterSetterInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite
			) {
		this.logMsgInterface = logMsgInterface;
		this.isRequestFromClient = isRequestFromClient;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;

		//
		RtspProtoSdpConsumer sdpConsumer = new RtspProtoSdpConsumer();

		//
		this.requAuthSvc = new RtspProtoRequAuthSvc(
				logMsgInterface,
				cfgRtxpLogLevel,
				userAuthInterface,
				availableStreamsInterface,
				globalSessionInfoInterface
			);

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
				RtspProtoHighConstants.DEFAULT_SUBSTREAM_ID_PREFIX,
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
	 * @param clientIpAddr Client's IP address - used for associating Sub-Stream IDs with a specific client
	 * @param outputDataRequ Output for request data
	 * @return Basic request information
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws InputStreamNotReadyException If the input stream is not ready
	 */
	public @NonNull RtspRequestBasics receiveRequestFromClient(
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveRequestFromClient()";

		if (! isRequestFromClient) {
			throw new IllegalArgumentException(FNC_NAME + ": Requests can only be received from the server");
		}
		if (clientIpAddr.isEmpty()) {
			throw new IllegalArgumentException("Client IP address is empty");
		}

		return internalReceiveRequest(FNC_NAME, clientIpAddr, outputDataRequ);
	}

	/**
	 * Receive a request from the server.
	 * @param outputDataRequ Output for request data
	 * @return Basic request information
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws InputStreamNotReadyException If the input stream is not ready
	 */
	public @NonNull RtspRequestBasics receiveRequestFromServer(@NonNull RtspProtoDataRequest outputDataRequ)
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveRequestFromServer()";

		if (isRequestFromClient) {
			throw new IllegalArgumentException(FNC_NAME + ": Requests can only be received from the client");
		}

		RtspProtoIpAddr tmpClientIpAddr = RtspProtoIpAddr.ofLoopback();

		return internalReceiveRequest(FNC_NAME, tmpClientIpAddr, outputDataRequ);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspRequestBasics internalReceiveRequest(
				@NonNull String fncName,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		outputDataRequ.clear();

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

		// load data from Session Info
		loadFromSessionInfo(currentIdSession, ioCseqRequ, inpStreamTpMain, ioSetupInfosStream, currentSessionState);

		// process the request - without checking authentication
		RtspRequestBasics resObj = rtspProtoHighRequestConsumer.processRequest(
				currentIdSession,
				currentSessionState,
				clientIpAddr,
				ioCseqRequ,
				ioSetupInfosStream,
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
			requAuthSvc.checkAuthorization(clientIpAddr, resObj, outputDataRequ.requAuthClient);
		}

		// store additional request data
		try {
			storeResourceUrl(resObj, outputDataRequ);
			storeServerIp(msgStructured, resObj, outputDataRequ);
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
		if (! (resObj.isValid() && updateSessionInfo_success(resObj, outputDataRequ.rrStreamTpMain, ioSetupInfosStream))) {
			return resObj;
		}

		//
		logDebug(fncName, String.format("Received %s request (CSeq=%d)",
				resObj.messageType, ioCseqRequ.cseqNr_lastRcvd.getCseq32bit().orElse(-1L)));
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void storeResourceUrl(
				@NonNull RtspRequestBasics requBasics,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		outputDataRequ.rrRscUrl.copyFrom(requBasics.rscUrl);
	}

	private void storeServerIp(
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
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequ,
				@NonNull RtspProtoDataCntStreamTpMain streamTpMain,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataCntSessionState sessionState
			) {
		currentIdSession.copyFrom(rtspSessionInfo.getIdSession());
		currentIdSession.writeProtect();

		//
		cseqRequ.cseqNr_lastRcvd.copyFrom(rtspSessionInfo.getCseqNr_requFromRem_lastRcvd());
		cseqRequ.cseqNr_expected.copyFrom(rtspSessionInfo.getCseqNr_requFromRem_expected());
		//
		streamTpMain.copyFrom(rtspSessionInfo.getStreamTpMain());
		//
		ioSetupInfosStream.copyFrom(rtspSessionInfo.getDescrSetupInfosStream());
		//
		sessionState.setSessionState(rtspSessionInfo.getSessionState());
	}

	private void updateSessionInfo_immediate(
				@NonNull RtspProtoDataRequest dataRequ,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequ
			) {
		rtspSessionInfo.setRtspProtoVersionToUse(dataRequ.getRtspProtoVersionToUse());
		//
		rtspSessionInfo.setCseqNr_requFromRem_lastRcvd(cseqRequ.cseqNr_lastRcvd);
		rtspSessionInfo.setCseqNr_requFromRem_expected(cseqRequ.cseqNr_expected);
		//
		rtspSessionInfo.setClientUserAgent(dataRequ.getClientUa());
		rtspSessionInfo.setClientPlaybackRangeValue(dataRequ.getPlaybackRangeValue());
	}

	private boolean updateSessionInfo_success(
				@NonNull RtspRequestBasics requBasics,
				@NonNull RtspProtoDataCntStreamTpMain streamTpMain,
				@NonNull RtspProtoSetupInfosStream setupInfosStream
			) {
		if (streamTpMain.getForceRtpRtcpEncryption()) {  // only update one-way
			rtspSessionInfo.setStreamTpMainForceRtpRtcpEncryption();
		}
		if (streamTpMain.getRtpRtcpEncryptionRequired()) {  // only update one-way
			rtspSessionInfo.setStreamTpMainRtpRtcpEncryptionRequired();
		}
		if (streamTpMain.getIsTransportUdp()) {  // only update one-way
			rtspSessionInfo.setStreamTpMainIsTransportUdp();
		}
		if (streamTpMain.getIsTransportSrtpSrtcp()) {  // only update one-way
			rtspSessionInfo.setStreamTpMainIsTransportSrtpSrtcp();
		}
		//
		rtspSessionInfo.setDescrSetupInfosStream(setupInfosStream);
		//
		if (requBasics.messageType != RtspProtoMessageType.SETUP) {
			rtspSessionInfo.putResourceUrlForMt_nonSetup(requBasics.messageType, requBasics.rscUrl);
		}

		return true;
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

package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.InputStreamNotReadyException;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.*;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspCannotFindIpFromRscUrlException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.request.RtspProtoHighRequestConsumer;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoUserAuthInterface;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request.RtspProtoLowRequestConsumer;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.RtspProtoSdpConsumer;

public final class RtspProtoRequestInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoRequAuthSvc requAuthSvc;
	private final RtspProtoLowMsgReader rtspProtoLowMsgReader;
	private final RtspProtoLowRequestConsumer rtspProtoLowRequestConsumer;
	private final RtspProtoHighRequestConsumer rtspProtoHighRequestConsumer;


	public RtspProtoRequestInputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpLogLevel cfgRtxpLogLevel,
				@NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes,
				boolean cfgIsDebugPrintRtspRcvd,
				boolean cfgIsDebugDisableTransportUdp,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtspProtoUserAuthInterface userAuthInterface,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;

		//
		RtspProtoSdpConsumer sdpConsumer = new RtspProtoSdpConsumer();

		//
		this.requAuthSvc = new RtspProtoRequAuthSvc(
				logMsgInterface,
				cfgRtxpLogLevel,
				rtspSessionInfo,
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
				cfgSupportedMessageTypes,
				cfgIsDebugDisableTransportUdp,
				sdpConsumer,
				availableStreamsInterface,
				globalSessionInfoInterface,
				null  // @TODO
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspRequestBasics receiveRequest(
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		outputDataRequ.clear();

		//
		outputDataRequ.requClientIpAddr.copyFrom(clientIpAddr);

		// read the raw request from the TCP socket
		RtspProtoLowMsgRaw lowInputRaw = rtspProtoLowMsgReader.readMessage();  // blocks for setSoTimeout() value
		if (! lowInputRaw.readSuccess) {
			RtspRequestBasics resObj = RtspRequestBasics.createUnknown();
			logWarn(FNC_NAME, String.format("Receiving RTSP request message failed, rejecting it with code %s",
					resObj.statusCode));
			return resObj;
		}

		// parse the raw request
		RtspProtoHighMsgStructuredRequest msgStructured = rtspProtoLowRequestConsumer.parseMessage(lowInputRaw);
		if (msgStructured.messageType == RtspMessageType.UNKNOWN) {
			RtspRequestBasics resObj = RtspRequestBasics.createUnknown();
			logWarn(FNC_NAME, String.format("Received invalid RTSP request message, rejecting it with code %s",
					resObj.statusCode));
			return resObj;
		}
		if (msgStructured.statusCode != RtspStatusCode.OK) {
			RtspRequestBasics resObj = RtspRequestBasics.createKnownWithError(msgStructured.messageType, msgStructured.statusCode);
			logWarn(FNC_NAME, String.format("Received invalid RTSP request message (rt=%s), rejecting it with code %s",
					resObj.messageType, resObj.statusCode));
			return resObj;
		}

		//
		RtspProtoIdSession currentIdSession = new RtspProtoIdSession();
		RtspProtoDataCntCseqRequInp ioCseqRequ = new RtspProtoDataCntCseqRequInp();
		RtspProtoDataCntStreamTpMain ioStreamTpMain = new RtspProtoDataCntStreamTpMain();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		RtspProtoDataCntSessionState inpSessionState = new RtspProtoDataCntSessionState();

		// load data from Session Info
		loadFromSessionInfo(currentIdSession, ioCseqRequ, ioStreamTpMain, ioSetupInfosStream, inpSessionState);

		// process the request - without checking authentication
		RtspRequestBasics resObj = rtspProtoHighRequestConsumer.processRequest(
				currentIdSession,
				clientIpAddr,
				ioCseqRequ,
				ioStreamTpMain,
				ioSetupInfosStream,
				inpSessionState,
				msgStructured,
				outputDataRequ
			);

		// update data in Session Info
		updateSessionInfo_immediate(outputDataRequ, ioCseqRequ);

		//
		if (! resObj.isValid()) {
			logWarn(FNC_NAME, String.format("Received invalid RTSP request (rt=%s), rejecting it with code %s (CSeq=%s)",
					resObj.messageType, resObj.statusCode,
					Long.toUnsignedString(ioCseqRequ.getCseqNrLastRcvd())));
			return resObj;
		}

		// check whether the client needs to be authenticated and if so, whether he actually is
		requAuthSvc.checkAuthorization(resObj, outputDataRequ.requAuthClient);

		// store additional request data
		try {
			storeResourceUrl(resObj, outputDataRequ);
			storeServerIp(msgStructured, resObj, outputDataRequ);
		} catch (RtspInvalidRequestException e) {
			resObj.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
			logWarn(FNC_NAME, String.format("%s for RTSP request message (rt=%s), rejecting it with code %s",
					e.getMessage(), resObj.messageType, resObj.statusCode));
		}

		//
		outputDataRequ.writeProtect();

		//
		if (resObj.messageType == RtspMessageType.SETUP && resObj.rscUrl.idSubStream.isEmpty()) {
			resObj.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
			logWarn(FNC_NAME, String.format("No Sub-Stream ID for RTSP request message (rt=%s), rejecting it with code %s",
					resObj.messageType, resObj.statusCode));
		}

		// update data in Session Info
		if (! (resObj.isValid() && updateSessionInfo_success(resObj, ioStreamTpMain, ioSetupInfosStream))) {
			return resObj;
		}

		//
		logDebug(FNC_NAME, String.format("Received %s request (CSeq=%s)",
				resObj.messageType, Long.toUnsignedString(ioCseqRequ.getCseqNrLastRcvd())));
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void storeResourceUrl(
				@NonNull RtspRequestBasics requBasics,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		outputDataRequ.requRscUrl.copyFrom(requBasics.rscUrl);
	}

	private void storeServerIp(
				@NonNull RtspProtoHighMsgStructuredRequest msgStructured,
				@NonNull RtspRequestBasics requBasics,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspInvalidRequestException {
		try {
			if (msgStructured.messageType == RtspMessageType.SETUP && requBasics.rscUrl.idSubStream.isEmpty()) {
				throw new RtspInvalidRequestException("Sub-Stream ID is empty");
			}
			outputDataRequ.requServerIpFromRscUrl.copyFrom(
					rtspSessionInfo.findRtspIpFromResourceUrl(requBasics.rscUrl)
				);
		} catch (RtspCannotFindIpFromRscUrlException e) {
			throw new RtspInvalidRequestException(e.getMessage());
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
		currentIdSession.copyFrom(rtspSessionInfo.idSession);
		currentIdSession.writeProtect();

		//
		cseqRequ.setCseqNrLastRcvd(rtspSessionInfo.seqNr_requFromRem_lastRcvd);
		cseqRequ.setCseqNrExpected(rtspSessionInfo.seqNr_requFromRem_expected);
		//
		streamTpMain.copyFrom(rtspSessionInfo.streamTpMain);
		//
		ioSetupInfosStream.copyFrom(rtspSessionInfo.descrSetupInfosStream);
		//
		sessionState.setSessionState(rtspSessionInfo.sessionState);
	}

	private void updateSessionInfo_immediate(
				@NonNull RtspProtoDataRequest dataRequ,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequ
			) {
		rtspSessionInfo.rtspProtoVersionToUse = dataRequ.getRtspProtoVersionToUse();
		//
		rtspSessionInfo.seqNr_requFromRem_lastRcvd = cseqRequ.getCseqNrLastRcvd();
		rtspSessionInfo.seqNr_requFromRem_expected = cseqRequ.getCseqNrExpected();
		//
		rtspSessionInfo.clientUserAgent = dataRequ.getClientUa();
		rtspSessionInfo.clientPlaybackRangeValue = dataRequ.getPlaybackRangeValue();
	}

	private boolean updateSessionInfo_success(
				@NonNull RtspRequestBasics requBasics,
				@NonNull RtspProtoDataCntStreamTpMain streamTpMain,
				@NonNull RtspProtoSetupInfosStream setupInfosStream
			) {
		if (streamTpMain.getForceRtpRtcpEncryption()) {  // only update one-way
			rtspSessionInfo.streamTpMain.setForceRtpRtcpEncryption(true);
		}
		if (streamTpMain.getRtpRtcpEncryptionRequired()) {  // only update one-way
			rtspSessionInfo.streamTpMain.setRtpRtcpEncryptionRequired(true);
		}
		if (streamTpMain.getIsTransportUdp()) {  // only update one-way
			rtspSessionInfo.streamTpMain.setIsTransportUdp(true);
		}
		if (streamTpMain.getIsTransportSrtpSrtcp()) {  // only update one-way
			rtspSessionInfo.streamTpMain.setIsTransportSrtpSrtcp(true);
		}
		//
		rtspSessionInfo.descrSetupInfosStream.copyFrom(setupInfosStream);
		//
		if (requBasics.messageType != RtspMessageType.SETUP) {
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

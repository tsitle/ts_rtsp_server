package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.exceptions.InputStreamNotReadyException;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspRequAuthSvc;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntCseqRequInp;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataRequest;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspCannotFindIpFromRscUrlException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.request.RtspProtoHighRequestConsumer;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request.RtspProtoLowRequestConsumer;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.SdpConsumer;

import java.util.Optional;

public final class RtspProtoRequestInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoLowMsgReader rtspProtoLowMsgReader;
	private final RtspProtoLowRequestConsumer rtspProtoLowRequestConsumer;
	private final RtspProtoHighRequestConsumer rtspProtoHighRequestConsumer;

	private final RtspRequAuthSvc rtspRequAuthSvc;

	public RtspProtoRequestInputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				boolean isRequestFromClient
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;

		//
		SdpConsumer sdpConsumer = new SdpConsumer();

		//
		this.rtspProtoLowMsgReader = new RtspProtoLowMsgReader(
				logMsgInterface,
				this.rtxpTcpReadWrite,
				rtspConfig.getIsDebugPrintRtspRcvd()
			);
		this.rtspProtoLowRequestConsumer = new RtspProtoLowRequestConsumer(logMsgInterface);
		this.rtspProtoHighRequestConsumer = new RtspProtoHighRequestConsumer(
				logMsgInterface,
				rtspConfig,
				rtspSessionInfo,
				isRequestFromClient,
				sdpConsumer,
				null  // @TODO
			);

		//
		this.rtspRequAuthSvc = new RtspRequAuthSvc(logMsgInterface, rtspConfig, rtspSessionInfo);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspRequestBasics receiveRequest(@NonNull RtspProtoDataRequest outputDataRequ)
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		outputDataRequ.clear();

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

		// load data from Session Info
		RtspProtoIdSession currentIdSession = new RtspProtoIdSession();
		RtspProtoDataCntCseqRequInp cseqRequIo = new RtspProtoDataCntCseqRequInp();
		loadFromSessionInfo(currentIdSession, cseqRequIo);

		// process the request - without checking authentication
		RtspRequestBasics resObj = rtspProtoHighRequestConsumer.processRequest(
				currentIdSession,
				cseqRequIo,
				msgStructured,
				outputDataRequ
			);

		// update data in Session Info
		updateSessionInfo_immediate(outputDataRequ, cseqRequIo);

		//
		if (! resObj.isValid()) {
			logWarn(FNC_NAME, String.format("Received invalid RTSP request (rt=%s), rejecting it with code %s (CSeq=%s)",
					resObj.messageType, resObj.statusCode,
					Long.toUnsignedString(cseqRequIo.getCseqNrLastRcvd())));
			return resObj;
		}

		// check whether the client needs to be authenticated and if so, whether he actually is
		rtspRequAuthSvc.checkAuthorization(resObj, outputDataRequ.requAuthClient);

		// store additional request data
		try {
			storeInputSourceId(msgStructured, outputDataRequ);
			storeResourceUrl(msgStructured, outputDataRequ);
			storeServerIp(msgStructured, outputDataRequ, resObj);
		} catch (RtspInvalidRequestException e) {
			resObj.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
			logWarn(FNC_NAME, String.format("%s for RTSP request message (rt=%s), rejecting it with code %s",
					e.getMessage(), resObj.messageType, resObj.statusCode));
		}

		//
		outputDataRequ.writeProtect();

		// update data in Session Info
		if (! (resObj.isValid() && updateSessionInfo_success(resObj))) {
			return resObj;
		}

		//
		logDebug(FNC_NAME, String.format("Received %s request (CSeq=%s)",
				resObj.messageType, Long.toUnsignedString(cseqRequIo.getCseqNrLastRcvd())));
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void storeInputSourceId(
				RtspProtoHighMsgStructuredRequest msgStructured,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspInvalidRequestException {
		if (msgStructured.messageType == RtspMessageType.SETUP) {
			return;
		}
		Optional<RtspInputSource> tmpOptIs = rtspSessionInfo.getInputSourceObjForMt_nonSetup(msgStructured.messageType);
		if (tmpOptIs.isEmpty()) {
			throw new RtspInvalidRequestException("Input Source not found");
		}
		outputDataRequ.setIdInputSource(tmpOptIs.get().getIdAsProtoId());
	}

	private void storeResourceUrl(
				RtspProtoHighMsgStructuredRequest msgStructured,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		outputDataRequ.setResourceUrl(msgStructured.resourceUrl);
	}

	private void storeServerIp(
				RtspProtoHighMsgStructuredRequest msgStructured,
				@NonNull RtspProtoDataRequest outputDataRequ,
				@NonNull RtspRequestBasics requBasics
			) throws RtspInvalidRequestException {
		try {
			String tmpSubStreamId = "";
			if (msgStructured.messageType == RtspMessageType.SETUP) {
				if (requBasics.requestUrlInputOrStreamSource == null || requBasics.requestUrlInputOrStreamSource.subStreamId == null) {
					throw new RtspInvalidRequestException("Sub-Stream ID not found");
				}
				tmpSubStreamId = requBasics.requestUrlInputOrStreamSource.subStreamId;
			}
			outputDataRequ.setServerIpFromRscUrl(
					rtspSessionInfo.findRtspIpFromResourceUrl(msgStructured.messageType, tmpSubStreamId)
				);
		} catch (RtspCannotFindIpFromRscUrlException e) {
			throw new RtspInvalidRequestException(e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void loadFromSessionInfo(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequIo
			) {
		currentIdSession.setId(rtspSessionInfo.rtspSessionId);
		currentIdSession.writeProtect();

		//
		cseqRequIo.setCseqNrLastRcvd(rtspSessionInfo.seqNr_requFromRem_lastRcvd);
		cseqRequIo.setCseqNrExpected(rtspSessionInfo.seqNr_requFromRem_expected);
	}

	private void updateSessionInfo_immediate(
				@NonNull RtspProtoDataRequest dataRequ,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequIo
			) {
		rtspSessionInfo.rtspProtoVersionToUse = dataRequ.getRtspProtoVersionToUse();

		//
		rtspSessionInfo.seqNr_requFromRem_lastRcvd = cseqRequIo.getCseqNrLastRcvd();
		rtspSessionInfo.seqNr_requFromRem_expected = cseqRequIo.getCseqNrExpected();
	}

	private boolean updateSessionInfo_success(@NonNull RtspRequestBasics requBasics) {
		final String FNC_NAME = getClass().getSimpleName() + ".updateSessionInfo_success()";

		if (requBasics.messageType != RtspMessageType.SETUP) {
			return true;
		}
		if (requBasics.requestUrlInputOrStreamSource == null || requBasics.requestUrlInputOrStreamSource.subStreamId == null) {
			requBasics.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
			logWarn(FNC_NAME, String.format("No Sub-Stream ID for RTSP request message (rt=%s), rejecting it with code %s",
					requBasics.messageType, requBasics.statusCode));
			return false;
		}
		// add Sub-Stream ID to session info
		rtspSessionInfo.subStreamIdsSetup.add(requBasics.requestUrlInputOrStreamSource.subStreamId);

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

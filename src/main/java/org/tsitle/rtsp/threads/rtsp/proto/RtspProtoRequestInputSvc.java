package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.InputStreamNotReadyException;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspRequAuthSvc;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.request.RtspProtoHighRequestProcessor;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request.RtspProtoLowRequestParser;

public final class RtspProtoRequestInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoLowMsgReader rtspProtoLowMsgReader;
	private final RtspProtoLowRequestParser rtspProtoLowRequestParser;
	private final RtspProtoHighRequestProcessor rtspProtoHighRequestProcessor;

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
		this.rtspProtoLowMsgReader = new RtspProtoLowMsgReader(
				logMsgInterface,
				this.rtxpTcpReadWrite,
				rtspConfig.getIsDebugPrintRtspRcvd()
			);
		this.rtspProtoLowRequestParser = new RtspProtoLowRequestParser(logMsgInterface);
		this.rtspProtoHighRequestProcessor = new RtspProtoHighRequestProcessor(
				logMsgInterface,
				rtspConfig,
				rtspSessionInfo,
				isRequestFromClient
			);

		//
		this.rtspRequAuthSvc = new RtspRequAuthSvc(logMsgInterface, rtspConfig, rtspSessionInfo);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspRequestBasics receiveRequest()
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		// read the raw request from the TCP socket
		RtspProtoLowMsgRaw lowInputRaw = rtspProtoLowMsgReader.readMessage();  // blocks for setSoTimeout() value
		if (! lowInputRaw.readSuccess) {
			RtspRequestBasics resObj = RtspRequestBasics.createUnknown();
			logWarn(FNC_NAME, String.format("Receiving RTSP request message failed, rejecting it with code %s",
					resObj.statusCode));
			return resObj;
		}

		// parse the raw request
		RtspProtoHighMsgStructuredRequest msgStructured = rtspProtoLowRequestParser.parseMessage(lowInputRaw);
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

		// process the request - without checking authentication
		RtspRequestBasics resObj = rtspProtoHighRequestProcessor.processRequest(msgStructured);
		if (! resObj.isValid()) {
			logWarn(FNC_NAME, String.format("Received invalid RTSP request (rt=%s), rejecting it with code %s (CSeq=%s)",
					resObj.messageType, resObj.statusCode,
					Long.toUnsignedString(rtspSessionInfo.seqNr_requRem_lastRcvd)));
			return resObj;
		}

		// check whether the client needs to be authenticated and if so, whether he actually is
		rtspRequAuthSvc.checkAuthorization(resObj);

		//
		if (resObj.isValid()) {
			logDebug(FNC_NAME, String.format("Received %s request (CSeq=%s)",
					resObj.messageType, Long.toUnsignedString(rtspSessionInfo.seqNr_requRem_lastRcvd)));
		}
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
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

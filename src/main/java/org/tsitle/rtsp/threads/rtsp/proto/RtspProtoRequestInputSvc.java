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
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtoLowMsgReader;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtoLowMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request.RtspProtoLowRequestParser;

public class RtspProtoRequestInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	protected final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoLowMsgReader rtspProtoLowMsgReader;
	private final RtspProtoLowRequestParser rtspProtoLowRequestParser;
	private final RtspProtoRequestProcessor rtspProtoRequestProcessor;

	private final RtspRequAuthSvc rtspRequAuthSvc;

	public RtspProtoRequestInputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite
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
		this.rtspProtoRequestProcessor = new RtspProtoRequestProcessor(
				logMsgInterface,
				rtspConfig,
				rtspSessionInfo
			);

		//
		this.rtspRequAuthSvc = new RtspRequAuthSvc(logMsgInterface, rtspConfig, rtspSessionInfo);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RequestBasicInfo getNextRequest()
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".getNextRequest()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		rtspSessionInfo.rtspClientSeqNrResponse = -1;

		RequestBasicInfo resObj;

		// read the raw request from the TCP socket
		RtspProtoLowMsgRaw lowInputRaw = rtspProtoLowMsgReader.readMessage();  // blocks for setSoTimeout() value
		if (! lowInputRaw.readSuccess) {
			resObj = RequestBasicInfo.createUnknown();
			logWarn(FNC_NAME, String.format("Receiving RTSP request message failed, rejecting it with code %s",
					resObj.statusCode));
			return resObj;
		}

		// parse the raw request
		RtspProtoLowMsgStructuredRequest lowInputParsed = rtspProtoLowRequestParser.parseRequest(lowInputRaw);
		if (lowInputParsed.messageType == RtspProtoMessageType.UNKNOWN) {
			resObj = RequestBasicInfo.createUnknown();
			logWarn(FNC_NAME, String.format("Received invalid RTSP request message, rejecting it with code %s",
					resObj.statusCode));
			return resObj;
		}
		if (lowInputParsed.statusCode != RtspProtoStatusCode.OK) {
			resObj = RequestBasicInfo.createKnownWithError(lowInputParsed.messageType, lowInputParsed.statusCode);
			logWarn(FNC_NAME, String.format("Received invalid RTSP request message (rt=%s), rejecting it with code %s",
					resObj.messageType, resObj.statusCode));
			return resObj;
		}

		// process the request - without checking authentication
		resObj = rtspProtoRequestProcessor.processRequest(lowInputParsed);
		if (! resObj.isValid()) {
			logWarn(FNC_NAME, String.format("Received invalid RTSP request (rt=%s), rejecting it with code %s (CSeq=%d)",
					resObj.messageType, resObj.statusCode, rtspSessionInfo.rtspClientSeqNrLastRcvd));
			return resObj;
		}

		// check whether the client needs to be authenticated and if so, whether he actually is
		rtspRequAuthSvc.checkAuthorization(resObj);

		//
		if (resObj.isValid()) {
			logDebug(FNC_NAME, String.format("Received %s request (CSeq=%d)",
					resObj.messageType, rtspSessionInfo.rtspClientSeqNrLastRcvd));
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

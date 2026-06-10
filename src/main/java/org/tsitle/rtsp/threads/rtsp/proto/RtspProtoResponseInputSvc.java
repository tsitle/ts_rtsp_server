package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.InputStreamNotReadyException;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntCseqRespInp;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataResponse;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspResponseBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.response.RtspProtoHighResponseConsumer;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.response.RtspProtoLowResponseConsumer;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.SdpConsumer;

public final class RtspProtoResponseInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoLowMsgReader rtspProtoLowMsgReader;
	private final RtspProtoLowResponseConsumer rtspProtoLowResponseConsumer;
	private final RtspProtoHighResponseConsumer rtspProtoHighResponseConsumer;

	public RtspProtoResponseInputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite,
				boolean isResponseFromClient,
				@Nullable RtspProtoParameterNotifyInvalidInterface parameterNotifyInvalidInterface,
				@Nullable RtspProtoParameterNotifyRcvdInterface parameterNotifyRcvdInterface
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
		this.rtspProtoLowResponseConsumer = new RtspProtoLowResponseConsumer(logMsgInterface);
		this.rtspProtoHighResponseConsumer = new RtspProtoHighResponseConsumer(
				logMsgInterface,
				isResponseFromClient,
				sdpConsumer,
				parameterNotifyInvalidInterface,
				parameterNotifyRcvdInterface
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspResponseBasics receiveResponse(@NonNull RtspMessageType requestMessageType)
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveResponse()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		// read the raw response from the TCP socket
		RtspProtoLowMsgRaw lowInputRaw = rtspProtoLowMsgReader.readMessage();  // blocks for setSoTimeout() value
		if (! lowInputRaw.readSuccess) {
			logWarn(FNC_NAME, String.format("Receiving RTSP response message for request '%s' failed", requestMessageType));
			return RtspResponseBasics.createInternalServerError();
		}

		// parse the raw response
		RtspProtoHighMsgStructuredResponse msgStructured = rtspProtoLowResponseConsumer.parseMessage(requestMessageType, lowInputRaw);
		if (msgStructured.statusCode == RtspStatusCode.INTERNAL_SERVER_ERROR) {
			logWarn(FNC_NAME, String.format("Received invalid RTSP response message for request '%s'", requestMessageType));
			return RtspResponseBasics.createInternalServerError();
		}

		// load data from Session Info
		RtspProtoIdSession currentIdSession = new RtspProtoIdSession();
		RtspProtoDataCntCseqRespInp cseqRespInp = new RtspProtoDataCntCseqRespInp();
		loadFromSessionInfo(currentIdSession, cseqRespInp);

		// process the response
		RtspProtoDataResponse outputDataResp = new RtspProtoDataResponse();
		RtspResponseBasics resObj = rtspProtoHighResponseConsumer.processResponse(
				currentIdSession,
				cseqRespInp,
				msgStructured,
				outputDataResp
			);

		//
		outputDataResp.writeProtect();

		// update data in Session Info
		updateSessionInfo(outputDataResp);

		//
		logDebug(FNC_NAME, String.format("Received response for request '%s' (CSeq=%s, Status=%d)",
				requestMessageType,
				msgStructured.getHeaderCseq().isPresent() ? Integer.toUnsignedString(msgStructured.getHeaderCseq().get()) : "-",
				resObj.statusCode.getIntValue()
			));
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void loadFromSessionInfo(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRespInp cseqRespInp
			) {
		currentIdSession.setId(rtspSessionInfo.rtspSessionId);
		currentIdSession.writeProtect();

		cseqRespInp.setCseqNrExpected(rtspSessionInfo.seqNr_requToRem_lastSent);
		cseqRespInp.writeProtect();
	}

	private void updateSessionInfo(@NonNull RtspProtoDataResponse dataResp) {
		rtspSessionInfo.permAuthServer.authRealm = dataResp.respAuthServer.getAuthRealm();
		rtspSessionInfo.permAuthServer.authNonce = dataResp.respAuthServer.getAuthNonce();

		//
		if (! dataResp.respIdSession.isEmpty()) {
			rtspSessionInfo.rtspSessionId = dataResp.respIdSession.getId();
		}

		//
		rtspSessionInfo.rhSupportedMessageTypes.copyFrom(dataResp.respSuppMessageTypes);
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

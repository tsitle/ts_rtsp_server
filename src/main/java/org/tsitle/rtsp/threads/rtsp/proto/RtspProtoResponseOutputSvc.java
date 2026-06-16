package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.response.RtspProtoHighResponseProducer;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgWriter;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.response.RtspProtoLowResponseProducer;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.RtspProtoSdpProducer;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataRequest;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataResponse;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspProtoStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspProtoInvalidResponseException;

import java.util.Optional;

public final class RtspProtoResponseOutputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final boolean isResponseFromClient;

	private final RtspProtoHighResponseProducer rtspProtoHighResponseProducer;
	private final RtspProtoLowResponseProducer rtspProtoLowResponseProducer;
	private final RtspProtoLowMsgWriter rtspProtoLowMsgWriter;

	public RtspProtoResponseOutputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isResponseFromClient,
				@NonNull String cfgServerNameAndVersion,
				@NonNull String cfgContentLanguage,
				@NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes,
				boolean cfgIsDebugPrintRtspSdpSent,
				boolean cfgIsDebugPrintRtspSent,
				boolean cfgIsDebugDisableTransportUdp,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite
			) {
		this.logMsgInterface = logMsgInterface;
		this.cfgSupportedMessageTypes.copyFrom(cfgSupportedMessageTypes);
		this.cfgSupportedMessageTypes.writeProtect();
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;
		this.isResponseFromClient = isResponseFromClient;

		// check if the supported message types are valid
		for (RtspProtoMessageType tmpMt : this.cfgSupportedMessageTypes.getMts()) {
			if (! RtspProtoHighConstants.LH_SUPPORTED_MESSAGE_TYPES_INCOMING.contains(tmpMt)) {
				throw new IllegalArgumentException("Unsupported request type " + tmpMt);
			}
		}

		//
		RtspProtoSdpProducer sdpProducer = new RtspProtoSdpProducer(
				cfgServerNameAndVersion,
				cfgContentLanguage,
				availableStreamsInterface,
				globalSessionInfoInterface
			);

		//
		this.rtspProtoHighResponseProducer = new RtspProtoHighResponseProducer(
				logMsgInterface,
				isResponseFromClient,
				cfgServerNameAndVersion,
				cfgIsDebugPrintRtspSdpSent,
				cfgIsDebugDisableTransportUdp,
				sdpProducer,
				availableStreamsInterface,
				globalSessionInfoInterface,
				null
			);
		this.rtspProtoLowResponseProducer = new RtspProtoLowResponseProducer(logMsgInterface);
		this.rtspProtoLowMsgWriter = new RtspProtoLowMsgWriter(
				logMsgInterface,
				this.rtxpTcpReadWrite,
				cfgIsDebugPrintRtspSent
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void sendResponse(
				@NonNull RtspRequestBasics rtspRequestBasics,
				@NonNull RtspProtoDataRequest inputDataRequ
			) throws TcpSocketClosedException, UdpSocketIoException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponse()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		inputDataRequ.writeProtect();

		// build the outgoing message
		RtspProtoHighMsgStructuredResponse msgStructured;
		try {
			RtspProtoDataResponse ioDataResp = new RtspProtoDataResponse(inputDataRequ);
			RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();

			// load data from Session Info
			loadFromSessionInfo(ioSetupInfosStream, ioDataResp);

			//
			ioDataResp.respSuppMessageTypes.copyFrom(cfgSupportedMessageTypes);

			//
			if (rtspRequestBasics.messageType == RtspProtoMessageType.DESCRIBE && rtspRequestBasics.statusCode == RtspProtoStatusCode.OK) {
				if (ioDataResp.respServerIpFromRscUrl.isEmpty()) {
					logError(FNC_NAME, "Server IP from Resource URL must be set");
					return;
				}
				if (ioDataResp.respRscUrl.idInputSource.isEmpty()) {
					logError(FNC_NAME, "Input Source ID must be set");
					return;
				}
			}

			//
			msgStructured = rtspProtoHighResponseProducer.buildResponse(rtspRequestBasics, ioSetupInfosStream, ioDataResp);

			// update data in Session Info
			updateSessionInfo(ioSetupInfosStream, ioDataResp, msgStructured);
		} catch (RtspProtoInvalidResponseException e) {
			logError(FNC_NAME, "Failed to build HL response: " + e.getMessage());
			return;
		}

		// convert the message
		RtspProtoLowMsgRaw msgRaw;
		try {
			msgRaw = rtspProtoLowResponseProducer.buildMessage(msgStructured);
		} catch (RtspProtoInvalidResponseException e) {
			logError(FNC_NAME, "Failed to build LL response: " + e.getMessage());
			return;
		}

		// send the message
		rtspProtoLowMsgWriter.writeMessage(msgRaw);
		logDebug(FNC_NAME, String.format("Sent response '%s' to remote host (<%s>, CSeq=%s)\n",
				msgStructured.statusCode,
				rtspSessionInfo.getIdSession().isEmpty() ? "-" : rtspSessionInfo.getIdSession().getIdStr(),
				msgStructured.getHeaderCseq().isPresent() ? msgStructured.getHeaderCseq().get() + "" : "-"));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void loadFromSessionInfo(
				@NonNull RtspProtoSetupInfosStream setupInfosStream,
				@NonNull RtspProtoDataResponse dataResp
			) {
		dataResp.respIdSession.copyFrom(rtspSessionInfo.getIdSession());
		//
		dataResp.respAuthServer.copyFrom(rtspSessionInfo.getPermAuthServer());
		//
		dataResp.respStreamTpMain.copyFrom(rtspSessionInfo.getStreamTpMain());
		//
		setupInfosStream.copyFrom(rtspSessionInfo.getDescrSetupInfosStream());
	}

	private void updateSessionInfo(
				@NonNull RtspProtoSetupInfosStream setupInfosStream,
				@NonNull RtspProtoDataResponse dataResp,
				@NonNull RtspProtoHighMsgStructuredResponse msgStructured
			) {
		// store new Session ID if one has been generated
		Optional<RtspProtoIdSession> tmpOptIdSess = msgStructured.getHeaderSessionId();
		if (tmpOptIdSess.isPresent() && rtspSessionInfo.getIdSession().isEmpty() &&
				! tmpOptIdSess.get().isEmpty() && ! rtspSessionInfo.getIdSession().isReadOnly()) {
			rtspSessionInfo.setSessionId(tmpOptIdSess.get());
		}
		// store permanent Auth data
		if (! (isResponseFromClient || rtspSessionInfo.getPermAuthServer().isReadOnly() || dataResp.respAuthServer.isEmpty())) {
			rtspSessionInfo.setPermAuthServer(dataResp.respAuthServer);
		}
		//
		rtspSessionInfo.setDescrSetupInfosStream(setupInfosStream);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

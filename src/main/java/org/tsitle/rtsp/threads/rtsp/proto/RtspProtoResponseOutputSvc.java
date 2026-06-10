package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataRequest;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataResponse;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.exceptions.TcpSocketClosedException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.response.RtspProtoHighResponseProducer;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.network.RtspProtoLowMsgWriter;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.response.RtspProtoLowResponseProducer;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.SdpProducer;

import java.util.Optional;

public final class RtspProtoResponseOutputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoHighResponseProducer rtspProtoHighResponseProducer;
	private final RtspProtoLowResponseProducer rtspProtoLowResponseProducer;
	private final RtspProtoLowMsgWriter rtspProtoLowMsgWriter;

	public RtspProtoResponseOutputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;

		//
		SdpProducer sdpProducer = new SdpProducer(rtspConfig, cfgServerNameAndVersion, rtspSessionInfo);

		//
		this.rtspProtoHighResponseProducer = new RtspProtoHighResponseProducer(
				logMsgInterface,
				cfgServerNameAndVersion,
				rtspConfig.getIsDebugPrintRtspSdpSent(),
				rtspConfig.getIsDebugDisableTransportUdp(),
				rtspSessionInfo,
				null,
				sdpProducer
			);
		this.rtspProtoLowResponseProducer = new RtspProtoLowResponseProducer(logMsgInterface);
		this.rtspProtoLowMsgWriter = new RtspProtoLowMsgWriter(
				logMsgInterface,
				this.rtxpTcpReadWrite,
				rtspConfig.getIsDebugPrintRtspSent()
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
			RtspProtoDataResponse inputDataResp = new RtspProtoDataResponse(inputDataRequ);

			// load data from Session Info
			loadFromSessionInfo(inputDataResp);

			//
			inputDataResp.writeProtect();

			//
			if (rtspRequestBasics.messageType == RtspMessageType.DESCRIBE && rtspRequestBasics.statusCode == RtspStatusCode.OK) {
				if (inputDataResp.getServerIpFromRscUrl().isEmpty()) {
					logError(FNC_NAME, "Server IP from Resource URL must be set");
					return;
				}
				if (inputDataResp.getIdInputSource().isEmpty()) {
					logError(FNC_NAME, "Input Source ID must be set");
					return;
				}
			}

			//
			msgStructured = rtspProtoHighResponseProducer.buildResponse(rtspRequestBasics, inputDataResp);

			// update data in Session Info
			updateSessionInfo(msgStructured);
		} catch (RtspInvalidResponseException e) {
			logError(FNC_NAME, "Failed to build HL response: " + e.getMessage());
			return;
		}

		// convert the message
		RtspProtoLowMsgRaw msgRaw;
		try {
			msgRaw = rtspProtoLowResponseProducer.buildMessage(msgStructured);
		} catch (RtspInvalidResponseException e) {
			logError(FNC_NAME, "Failed to build LL response: " + e.getMessage());
			return;
		}

		// send the message
		rtspProtoLowMsgWriter.writeMessage(msgRaw);
		logDebug(FNC_NAME, String.format("Sent response '%s' to remote host (<%s>, CSeq=%s)\n",
				msgStructured.statusCode,
				rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId,
				msgStructured.getHeaderCseq().isPresent() ? Integer.toUnsignedString(msgStructured.getHeaderCseq().get()) : "-"));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void loadFromSessionInfo(@NonNull RtspProtoDataResponse dataResp) {
		dataResp.respIdSession.setId(rtspSessionInfo.rtspSessionId);
	}

	private void updateSessionInfo(@NonNull RtspProtoHighMsgStructuredResponse msgStructured) {
		// store new Session ID if one has been generated
		Optional<RtspProtoIdSession> tmpOptIdSess = msgStructured.getHeaderSessionId();
		if (tmpOptIdSess.isPresent() && rtspSessionInfo.rtspSessionId.isEmpty() &&
				! tmpOptIdSess.get().isEmpty()) {
			rtspSessionInfo.rtspSessionId = tmpOptIdSess.get().getId();
		}
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

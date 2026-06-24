package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.exceptions.UdpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.response.RtspProtoHighResponseProducer;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoDescribeRespSrtxpTypeDeciderInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterGetterInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.network.RtspProtoLowMsgWriter;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.response.RtspProtoLowResponseProducer;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpProducer;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntMessageTypes;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataRequest;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataResponse;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidResponseException;

import java.util.Optional;
import java.util.Set;

/**
 * Service for sending RTSP responses over a TCP connection.
 */
public final class RtspProtoResponseOutputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;
	private final boolean isResponseFromClient;

	private final RtspProtoHighResponseProducer rtspProtoHighResponseProducer;
	private final RtspProtoLowResponseProducer rtspProtoLowResponseProducer;
	private final RtspProtoLowMsgWriter rtspProtoLowMsgWriter;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message handling instance
	 * @param isResponseFromClient Is this a response being sent by the client?
	 * @param cfgSenderAppNameAndVersion Server/client software name and version
	 * @param cfgContentLanguage Content language (can be empty)
	 * @param cfgSupportedMessageTypes Supported message types (can but shouldn't be empty), required for an OPTIONS response
	 * @param cfgSubStreamIdPrefix Prefix for Sub-Stream IDs (only required for responses from the server)
	 * @param cfgIsDebugPrintRtspSdpSent Enable printing sent RTSP SDP for debugging?
	 * @param cfgIsDebugPrintRtspSent Enable printing sent RTSP lines for debugging?
	 * @param cfgIsDebugDisableTransportUdp Disable UDP transport for debugging?
	 * @param rtspSessionInfo RTSP session info
	 * @param availableStreamsInterface Available streams instance (only required for responses from the server)
	 * @param globalSessionInfoInterface Global session info instance (only required for responses from the server)
	 * @param parameterGetterInterface Parameter getter instance (can be null)
	 * @param srtxpKmdsTypeDeciderInterface SRTxP KMDs type decider instance (can be null)
	 * @param rtxpTcpReadWrite RTxP TCP read/write instance
	 */
	public RtspProtoResponseOutputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isResponseFromClient,
				@NonNull String cfgSenderAppNameAndVersion,
				@NonNull String cfgContentLanguage,
				@NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes,
				@NonNull String cfgSubStreamIdPrefix,
				boolean cfgIsDebugPrintRtspSdpSent,
				boolean cfgIsDebugPrintRtspSent,
				boolean cfgIsDebugDisableTransportUdp,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@Nullable RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@Nullable RtspProtoParameterGetterInterface parameterGetterInterface,
				@Nullable RtspProtoDescribeRespSrtxpTypeDeciderInterface srtxpKmdsTypeDeciderInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite
			) {
		if (cfgSenderAppNameAndVersion.isBlank()) {
			throw new IllegalArgumentException("cfgSenderAppNameAndVersion cannot be blank");
		}
		if (! isResponseFromClient && cfgSubStreamIdPrefix.isBlank()) {
			throw new IllegalArgumentException("cfgSubStreamIdPrefix cannot be blank for responses from the server");
		}
		if (! isResponseFromClient && availableStreamsInterface == null) {
			throw new IllegalArgumentException("availableStreamsInterface cannot be null for responses from the server");
		}
		if (! isResponseFromClient && globalSessionInfoInterface == null) {
			throw new IllegalArgumentException("globalSessionInfoInterface cannot be null for responses from the server");
		}

		this.logMsgInterface = logMsgInterface;
		this.cfgSupportedMessageTypes.copyFrom(cfgSupportedMessageTypes);
		this.cfgSupportedMessageTypes.writeProtect();
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;
		this.isResponseFromClient = isResponseFromClient;

		// check if the supported message types are valid
		if (this.cfgSupportedMessageTypes.containsMt(RtspProtoMessageType.UNKNOWN)) {
			throw new IllegalArgumentException("cfgSupportedMessageTypes contains UNKNOWN message type");
		}

		//
		RtspProtoSdpProducer sdpProducer;
		if (availableStreamsInterface == null || globalSessionInfoInterface == null) {
			sdpProducer = null;
		} else {
			sdpProducer = new RtspProtoSdpProducer(
					cfgSenderAppNameAndVersion,
					cfgContentLanguage,
					availableStreamsInterface,
					globalSessionInfoInterface,
					srtxpKmdsTypeDeciderInterface
				);
		}

		//
		this.rtspProtoHighResponseProducer = new RtspProtoHighResponseProducer(
				logMsgInterface,
				isResponseFromClient,
				cfgSenderAppNameAndVersion,
				cfgSubStreamIdPrefix,
				cfgIsDebugPrintRtspSdpSent,
				cfgIsDebugDisableTransportUdp,
				sdpProducer,
				availableStreamsInterface,
				globalSessionInfoInterface,
				parameterGetterInterface
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

	/**
	 * Send a response.
	 * @param rtspRequestBasics Basic information about the request that this response is for
	 * @param inputDataRequ Input data from the request that this response is for
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws UdpSocketIoException If an I/O error occurs on the UDP socket
	 * @throws TcpSocketIoException If an I/O error occurs on the TCP socket
	 */
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

			// copy the supported message types for an OPTIONS response
			ioDataResp.respSuppMessageTypes.copyFrom(cfgSupportedMessageTypes);

			//
			if (rtspRequestBasics.messageType == RtspProtoMessageType.DESCRIBE && rtspRequestBasics.statusCode == RtspProtoStatusCode.OK) {
				if (ioDataResp.rrServerIpFromRscUrl.isEmpty()) {
					logError(FNC_NAME, "Server IP from Resource URL must be set");
					return;
				}
				if (ioDataResp.rrRscUrl.idInputSource.isEmpty()) {
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
		logDebug(FNC_NAME, String.format("Sent response '%s' to remote host (<%s>, CSeq=%s)\n",  // <-- intentional extra NL
				msgStructured.statusCode,
				rtspSessionInfo.getIdSession().isEmpty() ? "-" : rtspSessionInfo.getIdSession().getIdStr().orElseThrow(),
				msgStructured.getHeaderCseq().isPresent() ? msgStructured.getHeaderCseq().get() + "" : "-"));
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void loadFromSessionInfo(
				@NonNull RtspProtoSetupInfosStream setupInfosStream,
				@NonNull RtspProtoDataResponse dataResp
			) {
		dataResp.rrIdSession.copyFrom(rtspSessionInfo.getIdSession());
		//
		dataResp.respAuthServer.copyFrom(rtspSessionInfo.getPermAuthServer());
		//
		dataResp.rrStreamTpMain.copyFrom(rtspSessionInfo.getStreamTpMain());
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
		// store the available Sub-Stream IDs from a DESCRIBE response
		Set<@NonNull RtspProtoIdSubStream> tmpSiSsIds = setupInfosStream.getSubStreamIds();
		if (! tmpSiSsIds.isEmpty()) {
			rtspSessionInfo.setDescrAvailableSubStreamIds(tmpSiSsIds);
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

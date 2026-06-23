package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamNotReadyException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntCseqRespInp;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataResponse;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.response.RtspProtoHighResponseConsumer;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.response.RtspProtoLowResponseConsumer;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpConsumer;

import java.util.Set;

/**
 * Service for receiving and processing RTSP responses over a TCP connection.
 */
public final class RtspProtoResponseInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspProtoSessionInfo rtspSessionInfo;
	private final @NonNull RtxpTcpReadWrite rtxpTcpReadWrite;

	private final RtspProtoLowMsgReader rtspProtoLowMsgReader;
	private final RtspProtoLowResponseConsumer rtspProtoLowResponseConsumer;
	private final RtspProtoHighResponseConsumer rtspProtoHighResponseConsumer;

	/**
	 * Constructor.
	 * @param logMsgInterface Log message handling instance
	 * @param isResponseFromClient Is this a response sent by the client?
	 * @param cfgIsDebugPrintRtspRcvd Enable printing received RTSP lines for debugging?
	 * @param rtspSessionInfo RTSP session info
	 * @param rtxpTcpReadWrite RTxP TCP read/write instance
	 */
	public RtspProtoResponseInputSvc(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isResponseFromClient,
				boolean cfgIsDebugPrintRtspRcvd,
				@NonNull RtspProtoSessionInfo rtspSessionInfo,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWrite
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtxpTcpReadWrite = rtxpTcpReadWrite;

		//
		RtspProtoSdpConsumer sdpConsumer = new RtspProtoSdpConsumer();

		//
		this.rtspProtoLowMsgReader = new RtspProtoLowMsgReader(
				logMsgInterface,
				this.rtxpTcpReadWrite,
				cfgIsDebugPrintRtspRcvd
			);
		this.rtspProtoLowResponseConsumer = new RtspProtoLowResponseConsumer(logMsgInterface);
		this.rtspProtoHighResponseConsumer = new RtspProtoHighResponseConsumer(
				logMsgInterface,
				isResponseFromClient,
				sdpConsumer
			);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Receive a response.
	 * @param requestMessageType The message type of the request that this response is for.
	 * @return Basic response information
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws InputStreamNotReadyException If the input stream is not ready
	 */
	public @NonNull RtspResponseBasics receiveResponse(@NonNull RtspProtoMessageType requestMessageType)
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
		if (msgStructured.statusCode == RtspProtoStatusCode.INTERNAL_SERVER_ERROR) {
			logWarn(FNC_NAME, String.format("Received invalid RTSP response message for request '%s'", requestMessageType));
			return RtspResponseBasics.createInternalServerError();
		}

		// load data from Session Info
		RtspProtoIdSession currentIdSession = RtspProtoIdSession.ofEmpty();
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
				msgStructured.getHeaderCseq().isPresent() ? msgStructured.getHeaderCseq().get() + "" : "-",
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
		currentIdSession.copyFrom(rtspSessionInfo.getIdSession());
		currentIdSession.writeProtect();

		cseqRespInp.cseqNr_expected.copyFrom(rtspSessionInfo.getCseqNr_requToRem_lastSent());
		cseqRespInp.writeProtect();
	}

	private void updateSessionInfo(@NonNull RtspProtoDataResponse dataResp) {
		if (! (rtspSessionInfo.getPermAuthServer().isReadOnly() || dataResp.respAuthServer.isEmpty())) {
			rtspSessionInfo.setPermAuthServer(dataResp.respAuthServer);
		}
		//
		if (! (rtspSessionInfo.getIdSession().isReadOnly() || dataResp.rrIdSession.isEmpty())) {
			rtspSessionInfo.setSessionId(dataResp.rrIdSession);
		}
		//
		if (! dataResp.respSuppMessageTypes.isMtsEmpty()) {
			rtspSessionInfo.setRhSupportedMessageTypes(dataResp.respSuppMessageTypes);
		}
		//
		rtspSessionInfo.setUnsupportedFeatureName(dataResp.getUnsupportedFeatureName());  // always overwrite
		//
		rtspSessionInfo.setRhInvalidParamNames(dataResp.rrInvalidParamNames);  // always overwrite
		//
		rtspSessionInfo.setRhGetParamValues(dataResp.respGetParamValues);  // always overwrite
		//
		if (! dataResp.getClientUa().isEmpty()) {
			rtspSessionInfo.setClientUserAgent(dataResp.getClientUa());
		}
		//
		if (! dataResp.getServerSoftware().isEmpty()) {
			rtspSessionInfo.setServerSoftware(dataResp.getServerSoftware());
		}
		//
		if (! dataResp.respDescribeSdpStc.getMediaEntries().isEmpty()) {
			// store the available Sub-Stream IDs from a DESCRIBE response
			Set<@NonNull RtspProtoIdSubStream> tmpMeCtrlIds = dataResp.respDescribeSdpStc.findMediaEntryControlIds();
			if (! tmpMeCtrlIds.isEmpty()) {
				rtspSessionInfo.setDescrAvailableSubStreamIds(tmpMeCtrlIds);
			}
			// store the received structured SDP data
			rtspSessionInfo.setRhDescribeSdpStc(dataResp.respDescribeSdpStc);
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

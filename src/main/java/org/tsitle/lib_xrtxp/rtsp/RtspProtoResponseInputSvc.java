package org.tsitle.lib_xrtxp.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.InputStreamNotReadyException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketClosedException;
import org.tsitle.lib_xrtxp.common.exceptions.TcpSocketIoException;
import org.tsitle.lib_xrtxp.common.helpers.RandomHelper;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntCseqRespInp;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataResponse;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSessionInfoException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.response.RtspProtoHighResponseConsumer;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.network.RtspProtoLowMsgReader;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.response.RtspProtoLowResponseConsumer;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoKmdForSubStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.lib_xrtxp.rtsp.sdp.RtspProtoSdpConsumer;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpMediaType;
import org.tsitle.lib_xrtxp.rtsp.sdp.constants.RtspProtoSdpTransport;
import org.tsitle.lib_xrtxp.rtsp.sdp.types.RtspProtoSdpDataMediaEntry;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Service for receiving and processing RTSP responses over a TCP connection.
 */
public final class RtspProtoResponseInputSvc {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isResponseFromClient;
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
		this.isResponseFromClient = isResponseFromClient;
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
	 * @return Basic response information
	 * @throws TcpSocketClosedException If the TCP socket is closed
	 * @throws TcpSocketIoException If an I/O error occurs
	 * @throws InputStreamNotReadyException If the input stream is not ready
	 */
	public @NonNull RtspResponseBasics receiveResponse()
			throws TcpSocketClosedException, TcpSocketIoException, InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".receiveResponse()";

		if (rtxpTcpReadWrite.isSocketClosed()) {
			throw new TcpSocketClosedException();
		}

		//
		Optional<RtspProtoMessageType> tmpOptLastMsgTp = loadFromSessionInfo_onlyLastMsgType();
		if (tmpOptLastMsgTp.isEmpty()) {
			logWarn(FNC_NAME, "Receiving RTSP response message failed because no request message type is known");
			return RtspResponseBasics.createInternalServerError();
		}
		RtspProtoMessageType requestMessageType = tmpOptLastMsgTp.get();

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
		Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds = new HashSet<>();
		RtspProtoSetupInfosStream ioSetupInfosStream = new RtspProtoSetupInfosStream();
		RtspProtoDataResponse outputDataResp = new RtspProtoDataResponse();
		if (! loadFromSessionInfo(
					currentIdSession,
					cseqRespInp,
					requestMessageType,
					inpAvailableSubStreamIds,
					ioSetupInfosStream,
					outputDataResp
				)) {
			return RtspResponseBasics.createInternalServerError();
		}

		// process the response
		RtspResponseBasics resObj = rtspProtoHighResponseConsumer.processResponse(
				currentIdSession,
				cseqRespInp,
				inpAvailableSubStreamIds,
				ioSetupInfosStream,
				msgStructured,
				outputDataResp
			);

		//
		outputDataResp.writeProtect();

		// update data in Session Info
		updateSessionInfo(requestMessageType, ioSetupInfosStream, outputDataResp);

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

	private Optional<RtspProtoMessageType> loadFromSessionInfo_onlyLastMsgType() {
		return rtspSessionInfo.getLastUsedOutgoingRequestMsgType();
	}

	private boolean loadFromSessionInfo(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRespInp cseqRespInp,
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull Set<@NonNull RtspProtoIdSubStream> availableSubStreamIds,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataResponse dataResp
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".loadFromSessionInfo()";

		// copy the last used Resource URL object
		if (rtspSessionInfo.getLastUsedOutgoingRequestResourceUrl().isEmpty()) {
			logError(FNC_NAME, "Resource URL UrlStr cannot be empty");
			return false;
		}
		dataResp.rrRscUrl.copyFrom(rtspSessionInfo.getLastUsedOutgoingRequestResourceUrl().orElseThrow());
		if (dataResp.rrRscUrl.idInputSource.isEmpty()) {
			logError(FNC_NAME, "Resource URL idInputSource cannot be empty");
			return false;
		}

		RtspProtoIdSubStream idSsPtr = dataResp.rrRscUrl.idSubStream;

		if (requestMessageType == RtspProtoMessageType.SETUP && idSsPtr.isEmpty()) {
			logError(FNC_NAME, "Resource URL idSubStream cannot be empty for SETUP responses");
			return false;
		}

		//
		currentIdSession.copyFrom(rtspSessionInfo.getIdSession());
		currentIdSession.writeProtect();

		//
		cseqRespInp.cseqNr_expected.copyFrom(rtspSessionInfo.getCseqNr_requToRem_lastSent());
		cseqRespInp.writeProtect();

		//
		try {
			RtspProtoSetupInfoForSubStream inpSiForSs = rtspSessionInfo.getDescrSetupInfoBySubStreamsId(idSsPtr);
			dataResp.respSetupSubStreamTp.copyFrom(inpSiForSs.getSubStreamTpPtr());
		} catch (RtspProtoSessionInfoException e) {
			// ignore
		}
		dataResp.respSetupSubStreamTp.setIsUdp(rtspSessionInfo.getStreamTpMain().getIsTransportUdp());
		dataResp.respSetupSubStreamTp.setIsInterleaved(! rtspSessionInfo.getStreamTpMain().getIsTransportUdp());
		dataResp.respSetupSubStreamTp.setIsUnicast(true);
		dataResp.respSetupSubStreamTp.setIsEncr(rtspSessionInfo.getStreamTpMain().getIsTransportSrtpSrtcp());

		//
		availableSubStreamIds.clear();
		availableSubStreamIds.addAll(rtspSessionInfo.getDescrAvailableSubStreamIds());
		//
		ioSetupInfosStream.copyFrom(rtspSessionInfo.getDescrSetupInfosStream());

		return true;
	}

	private void updateSessionInfo(
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataResponse dataResp
			) {
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
		if (! dataResp.getPlaybackRangeValue().isEmpty()) {
			rtspSessionInfo.setServerPlaybackRangeValue(dataResp.getPlaybackRangeValue());
		}

		// store stream settings from a SETUP response
		if (! isResponseFromClient && requestMessageType == RtspProtoMessageType.SETUP) {
			storeSubStreamSettingsFromSetupResponse(dataResp);
			return;
		}

		// store stream settings from a PLAY response
		if (! isResponseFromClient && requestMessageType == RtspProtoMessageType.PLAY) {
			rtspSessionInfo.setDescrSetupInfosStream(ioSetupInfosStream);
			return;
		}

		// store some information from a DESCRIBE response
		if (isResponseFromClient || requestMessageType != RtspProtoMessageType.DESCRIBE) {
			return;
		}
		if (dataResp.respDescribeSdpStc.getMediaEntries().isEmpty()) {
			return;
		}
		Set<@NonNull RtspProtoIdSubStream> tmpMeCtrlIdsInput = dataResp.respDescribeSdpStc.findMediaEntryControlIds();
		if (! tmpMeCtrlIdsInput.isEmpty()) {
			Set<@NonNull RtspProtoIdSubStream> tmpMeCtrlIdsOutput = new HashSet<>();
			// store stream settings from a DESCRIBE response
			RtspProtoSetupInfosStream tmpSis = new RtspProtoSetupInfosStream();
			for (RtspProtoIdSubStream tmpMeCtrlId : tmpMeCtrlIdsInput) {
				boolean isAudOrVid = buildSubStreamSettingsFromDescribeResponse(
						requestMessageType,
						dataResp,
						tmpMeCtrlId,
						tmpSis
					);
				if (isAudOrVid) {
					tmpMeCtrlIdsOutput.add(tmpMeCtrlId);
				}
			}
			rtspSessionInfo.setDescrSetupInfosStream(tmpSis);

			// store the available Sub-Stream IDs from a DESCRIBE response
			rtspSessionInfo.setDescrAvailableSubStreamIds(tmpMeCtrlIdsOutput);

			// store the encryption settings
			for (RtspProtoIdSubStream tmpMeCtrlId : tmpMeCtrlIdsOutput) {
				Optional<RtspProtoSetupInfoForSubStream> tmpOptSiForSsPtr = tmpSis.getSiPtrBySubStreamId(tmpMeCtrlId);
				if (tmpOptSiForSsPtr.isPresent() && tmpOptSiForSsPtr.get().getSubStreamTpPtr().getIsEncr()) {
					rtspSessionInfo.setStreamTpMainIsTransportSrtpSrtcp();
					break;
				}
			}
		}
		// store the received structured SDP data
		rtspSessionInfo.setRhDescribeSdpStc(dataResp.respDescribeSdpStc);
	}

	private void storeSubStreamSettingsFromSetupResponse(@NonNull RtspProtoDataResponse dataResp) {
		final String FNC_NAME = getClass().getSimpleName() + ".storeSubStreamSettingsFromSetupResponse()";

		RtspProtoIdSubStream idSsPtr = dataResp.rrRscUrl.idSubStream;

		RtspProtoSetupInfoForSubStream outSiForSs;
		try {
			outSiForSs = new RtspProtoSetupInfoForSubStream(rtspSessionInfo.getDescrSetupInfoBySubStreamsId(idSsPtr));
			outSiForSs.getSsrcInboundPtr().copyFrom(dataResp.respSetupSubStreamSsrc);
		} catch (RtspProtoSessionInfoException e) {
			logError(FNC_NAME, "Could not find Sub-Stream info for ss='" +
					idSsPtr.getIdStr().orElse("-unset-") + "'");
			return;
		}
		outSiForSs.getSubStreamTpPtr().copyFrom(dataResp.respSetupSubStreamTp);
		outSiForSs.setHaveSetup(true);
		rtspSessionInfo.setDescrSetupInfosForSubStream(idSsPtr, outSiForSs);
	}

	private boolean buildSubStreamSettingsFromDescribeResponse(
				@NonNull RtspProtoMessageType requestMessageType,
				@NonNull RtspProtoDataResponse dataResp,
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoSetupInfosStream outputSis
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".buildSubStreamSettingsFromDescribeResponse()";

		RtspProtoSdpDataMediaEntry tmpMeObj = dataResp.respDescribeSdpStc.findMediaEntryForControlId(idSubStream).orElseThrow();

		//
		if (tmpMeObj.header().mediaType() != RtspProtoSdpMediaType.AUDIO &&
				tmpMeObj.header().mediaType() != RtspProtoSdpMediaType.VIDEO) {
			logWarn(FNC_NAME, "Ignoring SDP Media Entry of type " + tmpMeObj.header().mediaType());
			return false;
		}
		if (tmpMeObj.header().transport() != RtspProtoSdpTransport.RTP_AVP &&
				tmpMeObj.header().transport() != RtspProtoSdpTransport.RTP_SAVP) {
			logWarn(FNC_NAME, "Ignoring SDP Media Entry with unsupported Transport Type " + tmpMeObj.header().transport());
			return false;
		}

		// get the Input Source ID
		Optional<RtspProtoRscUrl> tmpOptRscUrl = rtspSessionInfo.getResourceUrlForMt_nonSetup(requestMessageType);
		if (tmpOptRscUrl.isEmpty()) {
			logError(FNC_NAME, "No Resource URL found for request message type: " + requestMessageType);
			return false;
		}
		if (tmpOptRscUrl.orElseThrow().idInputSource.isEmpty()) {
			logError(FNC_NAME, "Resource URL for request message type " + requestMessageType + " has no idInputSource");
			return false;
		}
		// create the Resource URL object
		Optional<String> tmpOptBaseUrl = dataResp.respDescribeSdpStc.getContentBase();
		if (tmpOptBaseUrl.isEmpty()) {
			logWarn(FNC_NAME, "Content-Base from SDP is empty");
			return false;
		}
		String tmpBaseUrl = tmpOptBaseUrl.orElseThrow();
		if (! tmpBaseUrl.endsWith("/")) {
			tmpBaseUrl += "/";
		}
		RtspProtoRscUrl tmpMeRscUrl = RtspProtoRscUrl.of(
				tmpBaseUrl + idSubStream.getIdStr().orElse("-unset-"),
				tmpOptRscUrl.orElseThrow().idInputSource,
				idSubStream
			);

		// extract the KMD from the SDP Media Entry
		RtspProtoKmdForSubStream kmdInbound = new RtspProtoKmdForSubStream();
		RtspProtoIdXsrc tmpDummySsrcInbound;
		RtspProtoIdXsrc tmpSsrcOutbound;
		try {
			tmpDummySsrcInbound = RtspProtoIdXsrc.of(0x01);
			Optional<SrtxpKmd> tmpMeKmd = dataResp.respDescribeSdpStc.extractMediaEntrySrtxpKmd(tmpMeObj);
			tmpMeKmd.ifPresent(srtxpKmd -> kmdInbound.setKmd(srtxpKmd, idSubStream));

			// generate SSRC ID
			long tmpOutRtspSsrcIdLong = Integer.toUnsignedLong(RandomHelper.getRandomUint32(false));
			tmpSsrcOutbound = RtspProtoIdXsrc.of(tmpOutRtspSsrcIdLong);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
			logError(FNC_NAME, "Setting dummy SSRC failed: " + e.getMessage());
			return false;
		} catch (SrtxpSecurityException e) {
			logError(FNC_NAME, "Extracting KMD from SDP Media Entry caught: " + e.getMessage());
			return false;
		}

		// store the Sub-Stream info
		outputSis.createAndAddDescribeSubStream(tmpMeRscUrl, tmpDummySsrcInbound, tmpSsrcOutbound, kmdInbound);

		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

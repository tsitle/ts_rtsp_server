package org.tsitle.lib_xrtxp.rtsp.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.kmd.MikeyParser;
import org.tsitle.lib_xrtxp.kmd.exceptions.SrtxpSecurityException;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.data_rr.*;
import org.tsitle.lib_xrtxp.rtsp.exceptions.*;
import org.tsitle.lib_xrtxp.rtsp.highlevel.ResourceUrlProcessor;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdInputSource;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.*;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoInputSource;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterSetterInterface;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpConsumerInterface;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.lib_xrtxp.rtsp.sdp.types.RtspProtoSdpDataMediaEntry;

import java.util.*;

public final class RtspProtoHighRequestConsumer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isRequestFromClient;
	private final @NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
	private final @NonNull Set<@NonNull String> cfgSupportedFeatures;
	private final @NonNull Set<@NonNull String> cfgProxySupportedFeatures;
	private final boolean cfgIsDebugDisableTransportUdp;
	private final @NonNull RtspProtoSdpConsumerInterface sdpConsumerInterface;
	private final @Nullable RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;
	private final @Nullable RtspProtoParameterSetterInterface parameterSetterInterface;

	private final Set<@NonNull RtspHeaderKey> preProcessedHeaders = new HashSet<>();

	public RtspProtoHighRequestConsumer(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isRequestFromClient,
				@NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes,
				@NonNull Set<@NonNull String> cfgSupportedFeatures,
				@NonNull Set<@NonNull String> cfgProxySupportedFeatures,
				boolean cfgIsDebugDisableTransportUdp,
				@NonNull RtspProtoSdpConsumerInterface sdpConsumerInterface,
				@Nullable RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@Nullable RtspProtoParameterSetterInterface parameterSetterInterface
			) {
		if (isRequestFromClient && availableStreamsInterface == null) {
			throw new IllegalArgumentException("availableStreamsInterface must be set for requests from the client");
		}
		if (isRequestFromClient && globalSessionInfoInterface == null) {
			throw new IllegalArgumentException("globalSessionInfoInterface must be set for requests from the client");
		}
		this.logMsgInterface = logMsgInterface;
		this.isRequestFromClient = isRequestFromClient;
		this.cfgSupportedMessageTypes.copyFrom(cfgSupportedMessageTypes);
		this.cfgSupportedMessageTypes.writeProtect();
		this.cfgSupportedFeatures = new HashSet<>(cfgSupportedFeatures);
		this.cfgProxySupportedFeatures = new HashSet<>(cfgProxySupportedFeatures);
		this.cfgIsDebugDisableTransportUdp = cfgIsDebugDisableTransportUdp;
		this.sdpConsumerInterface = sdpConsumerInterface;
		this.availableStreamsInterface = availableStreamsInterface;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
		this.parameterSetterInterface = parameterSetterInterface;

		// check if the supported message types are valid
		if (this.cfgSupportedMessageTypes.containsMt(RtspProtoMessageType.UNKNOWN)) {
			throw new IllegalArgumentException("cfgSupportedMessageTypes contains UNKNOWN message type");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Process a request.
	 * @param currentIdSession Current Session ID
	 * @param currentSessionState Current Session State
	 * @param clientIpAddr Client's IP address - used for associating Sub-Stream IDs with a specific client (for requests from the server this can always be 127.0.0.1)
	 * @param ioCseqRequ I/O for CSeq Nr.
	 * @param ioSetupInfosStream I/O for stream setup info
	 * @param inpAvailableSubStreamIds Input for available Sub-Stream IDs (from a previous DESCRIBE response)
	 * @param inpStreamTpMain Input for main stream transport settings
	 * @param inputMsgStc Input message
	 * @param outputDataRequ Output for request data
	 * @return Basic request information
	 */
	public @NonNull RtspRequestBasics processRequest(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntSessionState currentSessionState,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull RtspProtoDataCntCseqRequInp ioCseqRequ,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds,
				@NonNull RtspProtoDataCntStreamTpMain inpStreamTpMain,
				@NonNull RtspProtoHighMsgStructuredRequest inputMsgStc,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processRequest()";

		//System.out.println("<<<<<<<<< <<<<<<<<< " + inputMsgStc);

		preProcessedHeaders.clear();

		currentIdSession.writeProtect();
		outputDataRequ.clear();

		//
		final String logMsgSuffix = " (mt=" + inputMsgStc.messageType + "), " +
				"rejecting request (URL='" + inputMsgStc.resourceUrl + "')";

		//
		RtspProtoStatusCode tmpPreflightSc = preflightChecks(
				FNC_NAME,
				logMsgSuffix,
				currentIdSession,
				currentSessionState,
				ioCseqRequ,
				inputMsgStc,
				outputDataRequ
			);
		if (tmpPreflightSc != RtspProtoStatusCode.OK) {
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, tmpPreflightSc);
		}

		//
		outputDataRequ.requRtspSessionState.copyFrom(currentSessionState);
		outputDataRequ.rrClientIpAddr.copyFrom(clientIpAddr);
		outputDataRequ.rrStreamTpMain.copyFrom(inpStreamTpMain);

		// process resource URL
		RtspProtoRscUrl rscUrlObj;
		try {
			rscUrlObj = processResourceUrl(
					inputMsgStc.messageType,
					inputMsgStc.resourceUrl,
					clientIpAddr,
					inpAvailableSubStreamIds,
					outputDataRequ.rrStreamTpMain
				);
		} catch (RtspProtoInvalidUriException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.FORBIDDEN);
		} catch (RtspProtoInvalidRequestException | RtspProtoIdInputSourceNotFoundException |
					RtspProtoIdStreamSourceNotFoundException | RtspProtoIdSubStreamNotFoundException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.NOT_FOUND);
		}
		try {
			checkResourceUrlVsSessionInfo(rscUrlObj, ioSetupInfosStream);
		} catch (RtspProtoInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.BAD_REQUEST);
		}

		// process resource URL query parameters
		try {
			processQueryParams(inputMsgStc, outputDataRequ.rrStreamTpMain);
		} catch (RtspProtoInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.BAD_REQUEST);
		}

		// process headers that haven't been processed yet
		try {
			processRemainingHeaders(
					inputMsgStc,
					rscUrlObj,
					ioSetupInfosStream,
					outputDataRequ
				);
		} catch (RtspProtoInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.BAD_REQUEST);
		} catch (RtspProtoUnsupportedTransportException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.UNSUPPORTED_TRANSPORT);
		} catch (RtspProtoUnsupportedFeatureRequestedException e) {
			logWarn(FNC_NAME, "Requested Option '" + e.getMessage() + "' not supported" + logMsgSuffix);
			outputDataRequ.setUnsupportedFeatureName(e.getMessage());
			return RtspRequestBasics.createKnownWithOptionNotSupported(inputMsgStc.messageType);
		}

		//
		outputDataRequ.rrRscUrl.copyFrom(rscUrlObj);

		// process body
		if (inputMsgStc.messageType == RtspProtoMessageType.ANNOUNCE ||
				inputMsgStc.messageType == RtspProtoMessageType.GET_PARAMETER || inputMsgStc.messageType == RtspProtoMessageType.SET_PARAMETER) {
			try {
				processBody(inputMsgStc, outputDataRequ);
			} catch (RtspProtoInvalidRequestException e) {
				logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
				return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.BAD_REQUEST);
			}
		}

		// handle body
		try {
			handleBody(inputMsgStc.messageType, ioSetupInfosStream, outputDataRequ);
		} catch (RtspProtoRtspParamUnknownException e) {
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.INVALID_PARAMETER);
		} catch (RtspProtoSdpException | RtspProtoInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.BAD_REQUEST);
		}

		//
		return RtspRequestBasics.createOk(inputMsgStc.messageType, rscUrlObj);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspProtoStatusCode preflightChecks(
				@NonNull String fncName,
				@NonNull String logMsgSuffix,
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntSessionState currentSessionState,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequIo,
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		try {
			checkAndUpdateRtspProtoVersion(input, outputDataRequ);
			checkAndUpdateCseq(cseqRequIo, input, outputDataRequ);
		} catch (RtspProtoInvalidRequestException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return RtspProtoStatusCode.BAD_REQUEST;
		}
		try {
			checkAndUpdateSessionId(currentIdSession, input, outputDataRequ);
		} catch (RtspProtoInvalidRequestException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return RtspProtoStatusCode.SESSION_NOT_FOUND;
		}
		try {
			checkMessageType(input);
		} catch (RtspProtoInvalidRequestException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return RtspProtoStatusCode.METHOD_NOT_ALLOWED;
		}
		try {
			// check whether the request is allowed in the current RTSP state
			checkRequestTypeVsState(currentSessionState, input);
		} catch (RtspProtoInvalidRequestException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE;
		}
		return RtspProtoStatusCode.OK;
	}

	private void checkAndUpdateRtspProtoVersion(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoInvalidRequestException {
		if (input.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspProtoInvalidRequestException("Missing RTSP protocol version");
		}
		outputDataRequ.setRtspProtoVersionToUse(input.rtspProtoVersion);
	}

	private void checkAndUpdateCseq(
				@NonNull RtspProtoDataCntCseqRequInp cseqRequIo,
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoInvalidRequestException {
		Optional<Long> tmpOptCseq = input.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			throw new RtspProtoInvalidRequestException("Missing CSeq header");
		}
		final long tmpRcvdCseq = tmpOptCseq.get();
		try {
			cseqRequIo.cseqNr_lastRcvd.setCseq32bit(tmpRcvdCseq);
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
		}
		final long tmpExp = cseqRequIo.cseqNr_expected.getCseq32bit().orElse(-1L);
		if (tmpRcvdCseq > tmpExp) {
			try {
				cseqRequIo.cseqNr_expected.setCseq32bit(tmpRcvdCseq);
			} catch (RtspProtoNumberRangeException e) {
				// this will never happen
			}
		} else if (tmpRcvdCseq < tmpExp) {
			throw new RtspProtoInvalidRequestException("Invalid CSeq value");
		}

		cseqRequIo.cseqNr_expected.increment();
		//
		outputDataRequ.rrCseqNrLastRcvd.copyFrom(cseqRequIo.cseqNr_lastRcvd);

		//
		preProcessedHeaders.add(RtspHeaderKey.CSEQ);
	}

	private void checkAndUpdateSessionId(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoInvalidRequestException {
		Optional<RtspProtoIdSession> tmpOptHeaderSid = input.getHeaderSessionId();

		switch (input.messageType) {
			case RtspProtoMessageType.GET_PARAMETER:
			case RtspProtoMessageType.PAUSE:
			case RtspProtoMessageType.PLAY:
			case RtspProtoMessageType.SET_PARAMETER:
			case RtspProtoMessageType.SETUP:
			case RtspProtoMessageType.TEARDOWN:
				if (! currentIdSession.isEmpty()) {
					if (isRequestFromClient && tmpOptHeaderSid.isEmpty()) {
						throw new RtspProtoInvalidRequestException("Missing Session header");
					}
					if (tmpOptHeaderSid.isPresent() && ! tmpOptHeaderSid.get().equals(currentIdSession)) {
						throw new RtspProtoInvalidRequestException("Invalid Session ID");
					}
				} else if (isRequestFromClient && input.getHeaderSessionId().isPresent()) {
					throw new RtspProtoInvalidRequestException("Session header present when not expected");
				}
				break;
			default:
				break;
		}

		//
		if (! isRequestFromClient) {
			if (currentIdSession.isEmpty() && tmpOptHeaderSid.isPresent()) {
				outputDataRequ.rrIdSession.copyFrom(tmpOptHeaderSid.get());
			} else if (! currentIdSession.isEmpty() && tmpOptHeaderSid.isPresent() &&
					! tmpOptHeaderSid.get().equals(currentIdSession)) {
				throw new RtspProtoInvalidRequestException("Invalid Session ID");
			}
		}

		//
		if (outputDataRequ.rrIdSession.isEmpty() && ! currentIdSession.isEmpty()) {
			outputDataRequ.rrIdSession.copyFrom(currentIdSession);
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.SESSION);
	}

	private void checkMessageType(@NonNull RtspProtoHighMsgStructuredRequest input) throws RtspProtoInvalidRequestException {
		if (! cfgSupportedMessageTypes.containsMt(input.messageType)) {
			throw new RtspProtoInvalidRequestException("Unsupported request type " + input.messageType);
		}
	}

	private void checkRequestTypeVsState(
				@NonNull RtspProtoDataCntSessionState currentSessionState,
				@NonNull RtspProtoHighMsgStructuredRequest input
			) throws RtspProtoInvalidRequestException {
		if (input.messageType == RtspProtoMessageType.ANNOUNCE ||
				input.messageType == RtspProtoMessageType.DESCRIBE ||
				input.messageType == RtspProtoMessageType.GET_PARAMETER ||
				input.messageType == RtspProtoMessageType.OPTIONS ||
				input.messageType == RtspProtoMessageType.SET_PARAMETER) {
			return;
		}

		boolean wasOk = false;
		switch (currentSessionState.getSessionState()) {
			case INIT:
				if (input.messageType == RtspProtoMessageType.SETUP) {  // SETUP is allowed in INIT and READY states
					wasOk = true;
				}
				break;
			case READY:
				if (input.messageType == RtspProtoMessageType.PLAY ||
						input.messageType == RtspProtoMessageType.SETUP ||  // SETUP is allowed in INIT and READY states
						input.messageType == RtspProtoMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
			case PLAYING:
				if (input.messageType == RtspProtoMessageType.PAUSE ||
						input.messageType == RtspProtoMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
		}

		if (! wasOk) {
			throw new RtspProtoInvalidRequestException(String.format("Request %s not valid for current RTSP state %s",
					input.messageType, currentSessionState.getSessionState()));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspProtoRscUrl processResourceUrl(
				@NonNull RtspProtoMessageType requestType,
				@NonNull String resourceUrlStr,
				@NonNull RtspProtoIpAddr clientIpAddr,
				@NonNull Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds,
				@NonNull RtspProtoDataCntStreamTpMain ioStreamTpMain
			) throws RtspProtoInvalidRequestException, RtspProtoInvalidUriException,
					RtspProtoIdInputSourceNotFoundException, RtspProtoIdSubStreamNotFoundException,
					RtspProtoIdStreamSourceNotFoundException {
		if (ioStreamTpMain.getIsRtspsConnection() && ! resourceUrlStr.startsWith(RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL + "://")) {
			throw new RtspProtoInvalidUriException("Invalid URL for RTSPS");
		}
		if (! ioStreamTpMain.getIsRtspsConnection() && ! resourceUrlStr.startsWith(RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL + "://")) {
			throw new RtspProtoInvalidUriException("Invalid URL for RTSP");
		}

		//
		RtspProtoRscUrl resObj = ResourceUrlProcessor.parseUrlIntoRscUrlObject(
				availableStreamsInterface,
				globalSessionInfoInterface,
				resourceUrlStr,
				clientIpAddr,
				inpAvailableSubStreamIds
			);

		//
		if (requestType == RtspProtoMessageType.SETUP && resObj.idSubStream.isEmpty()) {
			throw new RtspProtoInvalidRequestException("Sub-Stream ID missing in URL for SETUP");
		}

		if (availableStreamsInterface == null) {
			return resObj;
		}
		// ensure that the Input Source is enabled
		RtspProtoInputSource tmpIsObj = availableStreamsInterface.getInputSourceObj(resObj.idInputSource);
		if (! tmpIsObj.getEnabled()) {
			throw new RtspProtoInvalidRequestException("Input Source is disabled");
		}

		// preliminary setting
		ioStreamTpMain.setRtpRtcpEncryptionRequired(
				tmpIsObj.getNeedsEncryption()
			);
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void checkResourceUrlVsSessionInfo(
				@NonNull RtspProtoRscUrl rscUrl,
				@NonNull RtspProtoSetupInfosStream inpSetupInfosStream
			) throws RtspProtoInvalidRequestException {
		if (inpSetupInfosStream.getNumberOfSubStreams() == 0) {
			return;
		}
		RtspProtoIdInputSource idIsSetup = inpSetupInfosStream.getSiPtrs().iterator().next().getRscUrlSubStreamPtr().idInputSource;
		if (! idIsSetup.equals(rscUrl.idInputSource)) {
			throw new RtspProtoInvalidRequestException("Input Source ID in request doesn't match previous SETUP request's Input Source ID " +
					"(is='" + rscUrl.idInputSource.getIdStr().orElse("-unset-") + "', " +
					"exp='" + idIsSetup.getIdStr().orElse("-unset-") + "')");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processQueryParams(
				@NonNull RtspProtoHighMsgStructuredRequest inputMsgStc,
				@NonNull RtspProtoDataCntStreamTpMain ioStreamTpMain
			) throws RtspProtoInvalidRequestException {
		for (Map.Entry<@NonNull String, @NonNull String> entry : inputMsgStc.queryParams.entrySet()) {
			if (! entry.getKey().equalsIgnoreCase(RtspProtoHighConstants.URL_QUERY_PARAM_SRTP)) {
				continue;
			}
			if (entry.getValue().equals("1")) {
				ioStreamTpMain.setForceRtpRtcpEncryption(true);
			} else if (! entry.getValue().equals("0")) {
				throw new RtspProtoInvalidRequestException("Invalid value for URL Query parameter '" + entry.getKey() + "': " +
						"'" + entry.getValue() + "'");
			}
			break;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processRemainingHeaders(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoRscUrl rscUrlObj,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoInvalidRequestException, RtspProtoUnsupportedTransportException,
					RtspProtoUnsupportedFeatureRequestedException {
		final String FNC_NAME = getClass().getSimpleName() + ".processRemainingHeaders()";

		for (Map.Entry<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryRequest> entry : input.headers.entrySet()) {
			switch (entry.getKey()) {
				case RtspHeaderKey.ACCEPT -> processHeader_describe_accept(input.messageType, entry.getValue());
				case RtspHeaderKey.AUTH_CLIENT -> processHeader_com_auth_client(entry.getValue(), outputDataRequ);
				case RtspHeaderKey.CONNECTION -> processHeader_com_connection(entry.getValue(), outputDataRequ);
				case RtspHeaderKey.CONTENT_BASE -> processHeader_announce_contbase(input.messageType);
				case RtspHeaderKey.CONTENT_ENC -> processHeader_com_contenc(input.messageType, entry.getValue());
				case RtspHeaderKey.CONTENT_LANG -> processHeader_com_contlang(input.messageType);
				case RtspHeaderKey.CONTENT_LEN -> processHeader_com_contlen(input.messageType);
				case RtspHeaderKey.CONTENT_TYPE -> processHeader_com_conttype(input.messageType);
				case RtspHeaderKey.DATE -> processHeader_com_date();
				case RtspHeaderKey.KEYMGMT ->
						processHeader_com_keymgmt(
								input.messageType,
								rscUrlObj,
								ioSetupInfosStream,
								entry.getValue()
							);
				case RtspHeaderKey.PROXY_REQU -> processHeader_com_proxyrequ(entry.getValue(), outputDataRequ);
				case RtspHeaderKey.RANGE -> processHeader_play_range(input.messageType, entry.getValue(), outputDataRequ);
				case RtspHeaderKey.REQUIRE -> processHeader_com_require(entry.getValue(), outputDataRequ);
				case RtspHeaderKey.SERVER -> processHeader_com_server(entry.getValue(), outputDataRequ);
				case RtspHeaderKey.TRANSPORT ->
						processHeader_setup_transport(
								input.messageType,
								rscUrlObj,
								entry.getValue(),
								outputDataRequ.rrStreamTpMain,
								ioSetupInfosStream
							);
				case RtspHeaderKey.USERAGENT -> processHeader_com_useragent(entry.getValue(), outputDataRequ);
				default -> {
					if (! preProcessedHeaders.contains(entry.getKey())) {
						logWarn(FNC_NAME, "Skipping header: " + entry.getKey());
					}
				}
			}
		}

		//
		if (! input.headers.containsKey(RtspHeaderKey.AUTH_CLIENT)) {
			outputDataRequ.requAuthClient.setAuthUser(input.authUser);
			outputDataRequ.requAuthClient.setAuthPlainPassword(input.authPlainPassword);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processHeader_describe_accept(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryRequest headerEntry
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_describe_accept()";

		if (messageType != RtspProtoMessageType.DESCRIBE) {
			logWarn(FNC_NAME, "Received Accept header in non-DESCRIBE request");
			return;
		}
		if (headerEntry.hdValAccept.rtspMimeType != RtspMimeType.SDP) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Only SDP allowed for Accept header value");
		}
	}

	private void processHeader_com_auth_client(
				@NonNull RtspProtoHeaderEntryRequest headerEntry,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		// copy parameters - ignore empty values here and reject the request later if necessary
		outputDataRequ.requAuthClient.setAuthUser(headerEntry.hdValAuthClient.authUser);
		outputDataRequ.requAuthClient.setAuthPlainPassword("");
		outputDataRequ.requAuthClient.setAuthRealm(headerEntry.hdValAuthClient.authRealm);
		outputDataRequ.requAuthClient.setAuthNonce(headerEntry.hdValAuthClient.authNonce);
		outputDataRequ.requAuthClient.setAuthUri(headerEntry.hdValAuthClient.authUri);
		outputDataRequ.requAuthClient.setAuthResp(headerEntry.hdValAuthClient.authResp);
	}

	private void processHeader_com_connection(
				@NonNull RtspProtoHeaderEntryRequest headerEntry,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoInvalidRequestException {
		if (headerEntry.hdValConnection.connectionPol == RtspConnectionPolicy.NONE) {
			throw new RtspProtoInvalidRequestException("Connection Policy must be set");
		}
		outputDataRequ.setConnectionPolicy(headerEntry.hdValConnection.connectionPol);
	}

	private void processHeader_announce_contbase(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_announce_contbase()";

		if (messageType != RtspProtoMessageType.ANNOUNCE) {
			logWarn(FNC_NAME, "Received Content-Base header in non-ANNOUNCE request");
		}
	}

	private void processHeader_com_contenc(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryRequest headerEntry
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contenc()";

		if (! allowOnlyAnnounceGetOrSetParameter(FNC_NAME, "Content-Encoding", messageType)) {
			return;
		}
		if (headerEntry.hdValContEnc.contentEnc != RtspContentEncoding.NONE) {
			throw new RtspProtoInvalidRequestException("No Content-Encoding other than NONE is supported");
		}
	}

	private void processHeader_com_contlang(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlang()";

		allowOnlyAnnounceGetOrSetParameter(FNC_NAME, "Content-Language", messageType);
	}

	private void processHeader_com_contlen(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlen()";

		allowOnlyAnnounceGetOrSetParameter(FNC_NAME, "Content-Length", messageType);
	}

	private void processHeader_com_conttype(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_conttype()";

		allowOnlyAnnounceGetOrSetParameter(FNC_NAME, "Content-Type", messageType);
	}

	private void processHeader_com_date() {
		// nothing to do
	}

	private void processHeader_com_keymgmt(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoRscUrl rscUrlObj,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoHeaderEntryRequest headerEntry
			) throws RtspProtoInvalidRequestException {
		if (messageType != RtspProtoMessageType.SETUP && messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspProtoInvalidRequestException("Received Keymgmt header in non-SETUP/SET_PARAMETER request");
		}
		if (rscUrlObj.idInputSource.isEmpty()) {
			throw new RtspProtoInvalidRequestException("No Input Source ID in " + messageType + " request");
		}
		if (rscUrlObj.idSubStream.isEmpty()) {
			throw new RtspProtoInvalidRequestException("No Sub-Stream ID in " + messageType + " request");
		}
		if (headerEntry.hdValKeymgmt.proto == RtspKeymgmtProto.NONE) {
			throw new RtspProtoInvalidRequestException("Unsupported Keymgmt protocol");
		}

		SrtxpKmd tmpKmd;
		try {
			tmpKmd = MikeyParser.parseMickeyMsgIntoKmd(headerEntry.hdValKeymgmt.dataStr);
		} catch (SrtxpSecurityException e) {
			throw new RtspProtoInvalidRequestException("Failed to set client MIKEY: " + e.getMessage());
		}

		handleNewInboundKmd(
				messageType == RtspProtoMessageType.SETUP,
				rscUrlObj.idSubStream,
				ioSetupInfosStream,
				tmpKmd
			);
	}

	private void processHeader_com_proxyrequ(
				@NonNull RtspProtoHeaderEntryRequest headerEntry,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoUnsupportedFeatureRequestedException {
		processRequiredFeatures(true, headerEntry.hdValProxyRequ.requiredFeatures, outputDataRequ);
	}

	private void processHeader_play_range(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryRequest headerEntry,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_play_range()";

		if (messageType != RtspProtoMessageType.PLAY) {
			logWarn(FNC_NAME, "Received Range header in non-PLAY request");
			return;
		}
		outputDataRequ.setPlaybackRangeValue(headerEntry.hdValRange.rangeStr);
	}

	private void processHeader_com_require(
				@NonNull RtspProtoHeaderEntryRequest headerEntry,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoUnsupportedFeatureRequestedException {
		processRequiredFeatures(false, headerEntry.hdValRequire.requiredFeatures, outputDataRequ);
	}

	private void processHeader_com_server(
				@NonNull RtspProtoHeaderEntryRequest headerEntry,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		outputDataRequ.setServerSoftware(headerEntry.hdValServer.serverStr);
	}

	private void processHeader_setup_transport(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoRscUrl rscUrlObj,
				@NonNull RtspProtoHeaderEntryRequest headerEntry,
				@NonNull RtspProtoDataCntStreamTpMain ioStreamTpMain,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream
			) throws RtspProtoInvalidRequestException, RtspProtoUnsupportedTransportException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_setup_transport()";

		if (messageType != RtspProtoMessageType.SETUP) {
			throw new RtspProtoInvalidRequestException("Received Transport header in non-SETUP request");
		}
		if (rscUrlObj.idInputSource.isEmpty()) {
			throw new RtspProtoInvalidRequestException("No Input Source ID in SETUP request");
		}
		if (rscUrlObj.idSubStream.isEmpty()) {
			throw new RtspProtoInvalidRequestException("No Sub-Stream ID in SETUP request");
		}

		if (! ioSetupInfosStream.containsSiForSubStreamId(rscUrlObj.idSubStream)) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Sub-Stream info not found");
		}
		RtspProtoSetupInfoForSubStream tmpSiSsPtr = ioSetupInfosStream.getSiPtrBySubStreamId(rscUrlObj.idSubStream).orElseThrow();

		// copy settings
		tmpSiSsPtr.getSubStreamTpPtr().copyFrom(headerEntry.hdValTransport.tpSubStream);
		if (tmpSiSsPtr.getSubStreamTpPtr().getIsUdp()) {
			if (tmpSiSsPtr.getSubStreamTpPtr().getClientUdpPortRtpPtr().isEmpty()) {
				throw new RtspProtoInvalidRequestException("No client UDP RTP port in SETUP request");
			}
			if (tmpSiSsPtr.getSubStreamTpPtr().getClientUdpPortRtcpPtr().isEmpty()) {
				throw new RtspProtoInvalidRequestException("No client UDP RTCP port in SETUP request");
			}
		} else {
			if (tmpSiSsPtr.getSubStreamTpPtr().getClientTcpChannRtpPtr().isEmpty()) {
				throw new RtspProtoInvalidRequestException("No client TCP RTP channel in SETUP request");
			}
			if (tmpSiSsPtr.getSubStreamTpPtr().getClientTcpChannRtcpPtr().isEmpty()) {
				throw new RtspProtoInvalidRequestException("No client TCP RTCP channel in SETUP request");
			}
		}

		//
		if (ioStreamTpMain.getForceRtpRtcpEncryption() && ! tmpSiSsPtr.getSubStreamTpPtr().getIsEncr()) {
			logWarn(FNC_NAME, "Client requested unencrypted Transport but server will force encryption");
			tmpSiSsPtr.getSubStreamTpPtr().setIsEncr(true);
		}

		//
		try {
			tmpSiSsPtr.isTransportValid(
					ioStreamTpMain.getRtpRtcpEncryptionRequired(),
					ioStreamTpMain.getForceRtpRtcpEncryption(),
					ioStreamTpMain.getIsRtspsConnection(),
					cfgIsDebugDisableTransportUdp
				);
		} catch (RtspProtoInvalidTpSettingsException e) {
			throw new RtspProtoUnsupportedTransportException("Invalid Transport: " + e.getMessage());
		}

		if (tmpSiSsPtr.getSubStreamTpPtr().getIsUdp()) {  // only update one-way
			ioStreamTpMain.setIsTransportUdp(true);
		}
		if (tmpSiSsPtr.getSubStreamTpPtr().getIsEncr()) {  // only update one-way
			ioStreamTpMain.setIsTransportSrtpSrtcp(true);
		}
	}

	private void processHeader_com_useragent(
				@NonNull RtspProtoHeaderEntryRequest headerEntry,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		outputDataRequ.setClientUa(headerEntry.hdValUserAgent.userAgentStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processRequiredFeatures(
				boolean isForProxy,
				@NonNull Set<@NonNull String> requiredFeatures,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoUnsupportedFeatureRequestedException {
		if (requiredFeatures.isEmpty()) {
			return;
		}
		String unsupp = "";
		Set<@NonNull String> inputPtr = (isForProxy ? cfgProxySupportedFeatures : cfgSupportedFeatures);
		for (String tmpInp : requiredFeatures) {
			if (inputPtr.contains(tmpInp)) {
				continue;
			}
			unsupp = tmpInp;
			break;
		}
		if (! unsupp.isEmpty()) {
			// we need to respond with "551 Option not supported"
			throw new RtspProtoUnsupportedFeatureRequestedException(unsupp);
		}
		if (isForProxy) {
			outputDataRequ.requProxyRequiredFeatures.putAllFeatureNames(requiredFeatures);
		} else {
			outputDataRequ.requRequiredFeatures.putAllFeatureNames(requiredFeatures);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean allowOnlyAnnounceGetOrSetParameter(
				@NonNull String fncName,
				@NonNull String hdDesc,
				@NonNull RtspProtoMessageType messageType
			) {
		if (messageType != RtspProtoMessageType.ANNOUNCE &&
				messageType != RtspProtoMessageType.GET_PARAMETER && messageType != RtspProtoMessageType.SET_PARAMETER) {
			logWarn(fncName, hdDesc + " header is only valid for " +
					"ANNOUNCE/GET_PARAMETER/SET_PARAMETER requests");
			return false;
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleNewInboundKmd(
				boolean isSetup,
				@NonNull RtspProtoIdSubStream idSubStream,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull SrtxpKmd kmd
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleNewInboundKmd()";

		if (idSubStream.isEmpty()) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Sub-Stream ID must be set");
		}

		if (! ioSetupInfosStream.containsSiForSubStreamId(idSubStream)) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Sub-Stream info not found");
		}
		RtspProtoSetupInfoForSubStream tmpInpSiSsPtr = ioSetupInfosStream.getSiPtrBySubStreamId(idSubStream).orElseThrow();
		RtspProtoSetupInfoForSubStream outSiSs = new RtspProtoSetupInfoForSubStream(tmpInpSiSsPtr);

		//System.out.println("<<<<<<<<<<<<<<<< rcvd KMD: " + kmd);

		SrtxpKmd kmdToUse = kmd;
		if (kmd.authKeyLen() != SrtxpKmd.DEFAULT_AUTH_KEY_LEN) {
			/*
			 * GStreamer is currently buggy. It sends MIKEY messages with a wrong Auth Key length of 10 bytes
			 * when it is actually using the correct length of 20 bytes.
			 * I have submitted a Merge Request (#11629) to GStreamer to fix the issue with MIKEY.
			 */
			kmdToUse = new SrtxpKmd(
					false,
					-1,
					kmd.encrKeyLen(),
					kmd.masterKey(),
					kmd.masterSalt(),
					SrtxpKmd.DEFAULT_AUTH_KEY_LEN,
					kmd.authTagLen(),
					kmd.mki(),
					kmd.ssrcId(),
					kmd.kdr()
				);
			//System.out.println("<<<<<<<<<<<<<<<< fixed KMD: " + kmdToUse);
			logDebug(FNC_NAME, "fixed inbound KMD with wrong Auth Key length");
		}

		boolean haveCurrentKmd = outSiSs.getKmdInboundCurPtr().isKmdSet();
		if (haveCurrentKmd &&
				kmdToUse.getMetaIsForLegacySdes() != outSiSs.getKmdInboundCurPtr().getIsKmdForLegacySdes().orElseThrow()) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": " +
					"received new inbound SDES KMD but the previous KMD was for MIKEY - rejecting new KMD");
		}
		if (kmdToUse.getMetaIsForLegacySdes()) {
			if (kmdToUse.getMetaTagForLegacySdes().isEmpty()) {
				throw new RtspProtoInvalidRequestException(FNC_NAME + ": " +
						"received new inbound SDES KMD but it has no Tag - rejecting new KMD");
			}
			if (haveCurrentKmd) {
				if (outSiSs.getKmdInboundCurPtr().getKmd().orElseThrow().getMetaTagForLegacySdes().isEmpty()) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": " +
							"received new inbound SDES KMD but previous KMD had no Tag - rejecting new KMD");
				}
				if (kmdToUse.getMetaTagForLegacySdes() ==
						outSiSs.getKmdInboundCurPtr().getKmd().orElseThrow().getMetaTagForLegacySdes()) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": " +
							"received new inbound SDES KMD but Tag is unchanged - rejecting new KMD");
				}
			}
		} else {
			if (haveCurrentKmd) {
				if (outSiSs.getKmdInboundCurPtr().getKmd().orElseThrow().mki().isEmpty()) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": " +
							"received new inbound MIKEY KMD but previous KMD had no MKI - rejecting new KMD");
				}
				if (kmdToUse.mki().getValue() == outSiSs.getKmdInboundCurPtr().getKmd().orElseThrow().mki().getValue()) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": " +
							"received new inbound MIKEY KMD but MKI is unchanged - rejecting new KMD");
				}
				if (! kmdToUse.ssrcId().equals(outSiSs.getKmdInboundCurPtr().getKmd().orElseThrow().ssrcId())) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": " +
							"received new inbound KMD but SSRC has been modified - rejecting new KMD");
				}
			}
			if (kmdToUse.mki().isEmpty()) {
				throw new RtspProtoInvalidRequestException(FNC_NAME + ": " +
						"received new inbound MIKEY KMD but it has no MKI - rejecting new KMD");
			}
		}

		if (isSetup || ! haveCurrentKmd) {
			outSiSs.getKmdInboundCurPtr().setKmd(kmdToUse, idSubStream);
		} else {
			outSiSs.getKmdInboundNextPtr().setKmd(kmdToUse, idSubStream);
		}

		ioSetupInfosStream.replaceSiForSubStream(idSubStream, outSiSs);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processBody(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoInvalidRequestException {
		/*
		 * Example:
		 *   ANNOUNCE:
		 *     "ANNOUNCE rtsp://example.com/fizzle/foo RTSP/1.0"
		 *     "Content-Base: rtsp://example.com/fizzle/foo/"
		 *     "Content-Type: application/sdp"
		 *     "Content-Length: 1234"
		 *     ...
		 *     ""
		 *     "v=0"
		 *     "a=tool:TS RTSP Server/1.0"
		 *     ...
		 *   GET_PARAMETER:
		 *     "GET_PARAMETER rtsp://example.com/fizzle/foo RTSP/1.0"
		 *     "Content-Type: text/parameters"
		 *     "Content-Length: 1234"
		 *     ...
		 *     ""
		 *     "packets_received"
		 *     "jitter"
		 *   SET_PARAMETER:
		 *     "SET_PARAMETER rtsp://example.com/fizzle/foo RTSP/1.0"
		 *     "Content-Length: 1234"
		 *     "Content-Type: text/parameters"
		 *     ...
		 *     ""
		 *     "barparam: barstuff"
		 */

		// Content-Type
		boolean haveHdContTp = input.headers.containsKey(RtspHeaderKey.CONTENT_TYPE);
		if (! haveHdContTp) {
			if (input.messageType == RtspProtoMessageType.ANNOUNCE) {
				throw new RtspProtoInvalidRequestException("Content-Type header is required for ANNOUNCE message");
			}
			return;
		}
		RtspMimeType contentType = input.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType;
		// Content-Length
		boolean haveHdContLen = input.headers.containsKey(RtspHeaderKey.CONTENT_LEN);
		if (! haveHdContLen) {
			if (input.messageType == RtspProtoMessageType.ANNOUNCE) {
				throw new RtspProtoInvalidRequestException("Content-Length header is required for ANNOUNCE message");
			}
			return;
		}
		long contentLengthLong = input.headers.get(RtspHeaderKey.CONTENT_LEN).hdValContLen.contentLen.getLen32bit().orElseThrow();
		if (contentLengthLong == 0L) {
			if (input.messageType == RtspProtoMessageType.ANNOUNCE) {
				throw new RtspProtoInvalidRequestException("Body for ANNOUNCE message missing");
			}
			return;
		}

		//
		switch (input.messageType) {
			case ANNOUNCE:
				if (contentType != RtspMimeType.SDP) {
					throw new RtspProtoInvalidRequestException("Content-Type for ANNOUNCE message must be SDP");
				}
				//
				outputDataRequ.requAnnouncedSdpRaw.copyFrom(input.bodyAnnounceSdp);
				// Content-Base
				if (! input.headers.containsKey(RtspHeaderKey.CONTENT_BASE)) {
					throw new RtspProtoInvalidRequestException("Content-Base for ANNOUNCE message missing");
				}
				outputDataRequ.requAnnouncedSdpRaw.setContentBase(
						input.headers.get(RtspHeaderKey.CONTENT_BASE).hdValContBase.contentBaseStr
					);
				break;
			case GET_PARAMETER:
				if (contentType != RtspMimeType.PARAMETERS) {
					throw new RtspProtoInvalidRequestException("Content-Type for GET_PARAMETER message must be PARAMETERS");
				}
				outputDataRequ.rrGetParamNames.copyFrom(input.bodyGetParamNames);
				break;
			case SET_PARAMETER:
				if (contentType != RtspMimeType.PARAMETERS) {
					throw new RtspProtoInvalidRequestException("Content-Type for SET_PARAMETER message must be PARAMETERS");
				}
				outputDataRequ.requSetParamValues.copyFrom(input.bodySetParamKv);
				break;
		}

		// Content-Language
		if ((input.messageType == RtspProtoMessageType.ANNOUNCE || input.messageType == RtspProtoMessageType.SET_PARAMETER) &&
				input.headers.containsKey(RtspHeaderKey.CONTENT_LANG)) {
			String tmpContLang = input.headers.get(RtspHeaderKey.CONTENT_LANG).hdValContLang.contentLangStr;
			if (input.messageType == RtspProtoMessageType.ANNOUNCE) {
				outputDataRequ.requAnnouncedSdpRaw.setContentLang(tmpContLang);
			} else {
				outputDataRequ.requSetParamValues.setContentLang(tmpContLang);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleBody(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoRtspParamUnknownException, RtspProtoSdpException, RtspProtoInvalidRequestException {
		switch (messageType) {
			case ANNOUNCE:
				handleBody_announce(ioSetupInfosStream, outputDataRequ);
				break;
			case SET_PARAMETER:
				handleBody_setParameter(outputDataRequ);
				break;
		}
	}

	private void handleBody_announce(
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoSdpException, RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleBody_announce()";

		sdpConsumerInterface.parseUpdatedSdpFromAnnounce(outputDataRequ.requAnnouncedSdpRaw, outputDataRequ.requAnnouncedSdpStc);

		// handle inbound KMDs from SDP
		Set<@NonNull RtspProtoIdSubStream> mediaEntryControlIds = outputDataRequ.requAnnouncedSdpStc.findMediaEntryControlIds();
		if (mediaEntryControlIds.isEmpty()) {
			return;
		}
		for (RtspProtoIdSubStream tmpIdSs : mediaEntryControlIds) {
			Optional<RtspProtoSdpDataMediaEntry> tmpMe = outputDataRequ.requAnnouncedSdpStc.findMediaEntryForControlId(tmpIdSs);
			if (tmpMe.isEmpty()) {
				continue;
			}
			//
			if (! ioSetupInfosStream.containsSiForSubStreamId(tmpIdSs)) {
				continue;
			}
			//
			SrtxpKmd tmpKmd;
			try {
				Optional<SrtxpKmd> tmpOptKmd = outputDataRequ.requAnnouncedSdpStc.extractMediaEntrySrtxpKmd(
						tmpMe.orElseThrow()
					);
				if (tmpOptKmd.isEmpty()) {
					continue;
				}
				tmpKmd = tmpOptKmd.orElseThrow();
			} catch (SrtxpSecurityException e) {
				throw new RtspProtoInvalidRequestException(FNC_NAME + ": SrtxpSecurityException for Sub-Stream ID '" +
						tmpIdSs.getIdStr().orElse("-unset-") + "': " + e.getMessage());
			}
			//
			handleNewInboundKmd(
					false,
					tmpIdSs,
					ioSetupInfosStream,
					tmpKmd
				);
		}
	}

	private void handleBody_setParameter(@NonNull RtspProtoDataRequest outputDataRequ) throws RtspProtoRtspParamUnknownException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleBody_setParameter()";

		if (outputDataRequ.requSetParamValues.isParamKvsEmpty()) {
			return;
		}
		if (parameterSetterInterface == null) {
			outputDataRequ.rrInvalidParamNames.putAllParamNames(outputDataRequ.requSetParamValues.getParamKvsKeySet());
			logWarn(FNC_NAME, "RtspProtoParameterSetterInterface is not set");
			throw new RtspProtoRtspParamUnknownException("all params");
		}

		outputDataRequ.rrInvalidParamNames.clear();

		//
		outputDataRequ.requSetParamValues.setIdInputSource(outputDataRequ.rrRscUrl.idInputSource);
		outputDataRequ.requSetParamValues.setIdSubStream(outputDataRequ.rrRscUrl.idSubStream);

		// if one of the parameters cannot be set, the entire request needs to be rejected
		for (Map.Entry<@NonNull String, @NonNull String> entry : outputDataRequ.requSetParamValues.getParamKvsEntrySet()) {
			try {
				parameterSetterInterface.setRtspParameter(
						true,
						outputDataRequ.rrIdSession,
						outputDataRequ.requSetParamValues.getIdInputSource(),
						outputDataRequ.requSetParamValues.getIdSubStream(),
						outputDataRequ.requSetParamValues.getContentLang(),
						entry.getKey(),
						entry.getValue()
					);
			} catch (RtspProtoRtspParamUnknownException e) {
				outputDataRequ.rrInvalidParamNames.putParamName(entry.getKey());
				logWarn(FNC_NAME, "RtspProtoRtspParamUnknownException caught for parameter: '" +
						entry.getKey() + "': " + e.getMessage());
			} catch (RtspProtoRtspParamInvalidValueException e) {
				outputDataRequ.rrInvalidParamNames.putParamName(entry.getKey());
				logWarn(FNC_NAME, "RtspProtoRtspParamInvalidValueException caught for parameter key='" +
						entry.getKey() + "': '" + entry.getValue() + "': " + e.getMessage());
			}
		}
		if (! outputDataRequ.rrInvalidParamNames.isParamNamesEmpty()) {
			throw new RtspProtoRtspParamUnknownException(
					String.join(", ", outputDataRequ.rrInvalidParamNames.getParamNames())
				);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("SameParameterValue")
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

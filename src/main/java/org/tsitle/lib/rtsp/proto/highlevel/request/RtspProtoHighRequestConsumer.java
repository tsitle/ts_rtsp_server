package org.tsitle.lib.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.security.MikeyParser;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoInputSource;
import org.tsitle.lib.rtsp.proto.enums.RtspProtoMessageType;
import org.tsitle.lib.rtsp.proto.ids.RtspProtoIdSession;
import org.tsitle.lib.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.lib.rtsp.proto.interfaces.RtspProtoParameterSetterInterface;
import org.tsitle.lib.rtsp.proto.enums.RtspProtoStatusCode;
import org.tsitle.lib.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.lib.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.lib.rtsp.proto.interfaces.RtspProtoGlobalSessionInfoInterface;
import org.tsitle.lib.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.lib.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.lib.rtsp.proto.interfaces.RtspProtoSdpConsumerInterface;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoIpAddr;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoRscUrl;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.lib.rtsp.proto.misctypes.RtspProtoSetupInfosStream;
import org.tsitle.lib.rtsp.proto.data_rr.*;
import org.tsitle.lib.rtsp.proto.exceptions.*;
import org.tsitle.lib.rtsp.proto.lowlevel.*;

import java.util.*;

public final class RtspProtoHighRequestConsumer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes = new RtspProtoDataCntMessageTypes();
	private final boolean cfgIsDebugDisableTransportUdp;
	private final @NonNull RtspProtoSdpConsumerInterface sdpConsumerInterface;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;
	private final @Nullable RtspProtoParameterSetterInterface parameterSetterInterface;

	private final Set<@NonNull RtspHeaderKey> preProcessedHeaders = new HashSet<>();

	public RtspProtoHighRequestConsumer(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspProtoDataCntMessageTypes cfgSupportedMessageTypes,
				boolean cfgIsDebugDisableTransportUdp,
				@NonNull RtspProtoSdpConsumerInterface sdpConsumerInterface,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@Nullable RtspProtoParameterSetterInterface parameterSetterInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.cfgSupportedMessageTypes.copyFrom(cfgSupportedMessageTypes);
		this.cfgSupportedMessageTypes.writeProtect();
		this.cfgIsDebugDisableTransportUdp = cfgIsDebugDisableTransportUdp;
		this.sdpConsumerInterface = sdpConsumerInterface;
		this.availableStreamsInterface = availableStreamsInterface;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
		this.parameterSetterInterface = parameterSetterInterface;

		// check if the supported message types are valid
		for (RtspProtoMessageType tmpMt : this.cfgSupportedMessageTypes.getMts()) {
			if (! RtspProtoHighConstants.LH_SUPPORTED_MESSAGE_TYPES_INCOMING.contains(tmpMt)) {
				throw new IllegalArgumentException("Unsupported request type " + tmpMt);
			}
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
	 * @param inpStreamTpMain I/O for main stream transport settings
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
		final String logMsgSuffix = " for " + inputMsgStc.messageType + " request, " +
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
		outputDataRequ.rrIdSession.copyFrom(currentIdSession);
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
			handleBody(inputMsgStc.messageType, outputDataRequ);
		} catch (RtspProtoRtspParamUnknownException e) {
			return RtspRequestBasics.createKnownWithError(inputMsgStc.messageType, RtspProtoStatusCode.INVALID_PARAMETER);
		} catch (RtspProtoSdpException e) {
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
			checkSessionId(currentIdSession, input);
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

		//
		outputDataRequ.rrCseqNrLastRcvd.copyFrom(cseqRequIo.cseqNr_lastRcvd);

		//
		preProcessedHeaders.add(RtspHeaderKey.CSEQ);
	}

	private void checkSessionId(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoHighMsgStructuredRequest input
			) throws RtspProtoInvalidRequestException {
		switch (input.messageType) {
			case RtspProtoMessageType.GET_PARAMETER:
			case RtspProtoMessageType.PAUSE:
			case RtspProtoMessageType.PLAY:
			case RtspProtoMessageType.SET_PARAMETER:
			case RtspProtoMessageType.TEARDOWN:
				Optional<RtspProtoIdSession> tmpOptSessionId = input.getHeaderSessionId();
				if (tmpOptSessionId.isEmpty()) {
					throw new RtspProtoInvalidRequestException("Missing Session header");
				}

				if (currentIdSession.isEmpty() || ! tmpOptSessionId.get().equals(currentIdSession)) {
					throw new RtspProtoInvalidRequestException("Invalid/missing Session ID");
				}
				break;
			default:
				break;
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
		if (input.messageType == RtspProtoMessageType.DESCRIBE ||
				input.messageType == RtspProtoMessageType.GET_PARAMETER ||
				input.messageType == RtspProtoMessageType.OPTIONS ||
				input.messageType == RtspProtoMessageType.REDIRECT ||
				input.messageType == RtspProtoMessageType.SET_PARAMETER ||
				input.messageType == RtspProtoMessageType.SETUP) {
			return;
		}

		boolean wasOk = false;
		switch (currentSessionState.getSessionState()) {
			case READY:
				if (input.messageType == RtspProtoMessageType.PLAY ||
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
		RtspProtoRscUrl resObj = ResourceUrlProcessorNg.parseUrlIntoRscUrlObject(
				availableStreamsInterface,
				globalSessionInfoInterface,
				resourceUrlStr,
				clientIpAddr
			);

		// ensure that the Input Source is enabled
		RtspProtoInputSource tmpIsObj = availableStreamsInterface.getInputSourceObj(resObj.idInputSource);
		if (! tmpIsObj.getEnabled()) {
			throw new RtspProtoInvalidRequestException("Input Source is disabled");
		}

		//
		if (requestType == RtspProtoMessageType.SETUP && resObj.idSubStream.isEmpty()) {
			throw new RtspProtoInvalidRequestException("Sub-Stream ID missing in URL for SETUP");
		}

		// preliminary setting
		ioStreamTpMain.setRtpRtcpEncryptionRequired(
				tmpIsObj.getNeedsEncryption()
			);
		return resObj;
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
				case RtspHeaderKey.CONNECTION -> processHeader_com_connection(entry.getValue());
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
				case RtspHeaderKey.PROXY_REQU -> processHeader_com_proxyrequ(entry.getValue());
				case RtspHeaderKey.RANGE -> processHeader_play_range(input.messageType, entry.getValue(), outputDataRequ);
				case RtspHeaderKey.REQUIRE -> processHeader_com_require(entry.getValue());
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

	private void processHeader_com_connection(@NonNull RtspProtoHeaderEntryRequest headerEntry)
			throws RtspProtoInvalidRequestException {
		if (headerEntry.hdValConnection.connectionPol == RtspConnectionPolicy.NONE) {
			throw new RtspProtoInvalidRequestException("Connection Policy must be set");
		}
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
		if (rscUrlObj.idInputSource.isEmpty() || rscUrlObj.idStreamSource.isEmpty()) {
			throw new RtspProtoInvalidRequestException("No IS/SS in SETUP/SET_PARAMETER request");
		}
		if (headerEntry.hdValKeymgmt.proto == RtspKeymgmtProto.NONE) {
			throw new RtspProtoInvalidRequestException("Unsupported Keymgmt protocol in SETUP/SET_PARAMETER request");
		}

		SrtxpKmd tmpKmd;
		try {
			tmpKmd = MikeyParser.parseMickeyMsgIntoKmd(headerEntry.hdValKeymgmt.dataStr);
		} catch (SrtxpSecurityException e) {
			throw new RtspProtoInvalidRequestException("Failed to set client MIKEY: " + e.getMessage());
		}

		handleKmdFromMikey(
				messageType == RtspProtoMessageType.SETUP,
				rscUrlObj,
				ioSetupInfosStream,
				tmpKmd
			);
	}

	private void processHeader_com_proxyrequ(@NonNull RtspProtoHeaderEntryRequest headerEntry)
			throws RtspProtoUnsupportedFeatureRequestedException {
		processRequiredFeatures(headerEntry.hdValProxyRequ.requiredFeatures);
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

	private void processHeader_com_require(@NonNull RtspProtoHeaderEntryRequest headerEntry)
			throws RtspProtoUnsupportedFeatureRequestedException {
		processRequiredFeatures(headerEntry.hdValRequire.requiredFeatures);
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
		if (rscUrlObj.idInputSource.isEmpty() || rscUrlObj.idStreamSource.isEmpty()) {
			throw new RtspProtoInvalidRequestException("No IS/SS in SETUP request");
		}
		if (rscUrlObj.idSubStream.isEmpty()) {
			throw new RtspProtoInvalidRequestException("No Sub-Stream ID in SETUP request");
		}

		if (! ioSetupInfosStream.containsSiForSubStreamId(rscUrlObj.idSubStream)) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Sub-Stream info not found");
		}
		RtspProtoSetupInfoForSubStream tmpSiSs = ioSetupInfosStream.getSiBySubStreamId(rscUrlObj.idSubStream).orElseThrow();

		// copy settings
		tmpSiSs.getSubStreamTpPtr().copyFrom(headerEntry.hdValTransport.tpSubStream);
		if (tmpSiSs.getSubStreamTpPtr().getIsUdp()) {
			if (tmpSiSs.getSubStreamTpPtr().getClientUdpPortRtpPtr().isEmpty()) {
				throw new RtspProtoInvalidRequestException("No client UDP RTP port in SETUP request");
			}
			if (tmpSiSs.getSubStreamTpPtr().getClientUdpPortRtcpPtr().isEmpty()) {
				throw new RtspProtoInvalidRequestException("No client UDP RTCP port in SETUP request");
			}
		} else {
			if (tmpSiSs.getSubStreamTpPtr().getClientTcpChannRtpPtr().isEmpty()) {
				throw new RtspProtoInvalidRequestException("No client TCP RTP channel in SETUP request");
			}
			if (tmpSiSs.getSubStreamTpPtr().getClientTcpChannRtcpPtr().isEmpty()) {
				throw new RtspProtoInvalidRequestException("No client TCP RTCP channel in SETUP request");
			}
		}

		//
		if (ioStreamTpMain.getForceRtpRtcpEncryption() && ! tmpSiSs.getSubStreamTpPtr().getIsEncr()) {
			logWarn(FNC_NAME, "Client requested unencrypted Transport but server will force encryption");
			tmpSiSs.getSubStreamTpPtr().setIsEncr(true);
		}

		//
		try {
			tmpSiSs.isTransportValid(
					ioStreamTpMain.getRtpRtcpEncryptionRequired(),
					ioStreamTpMain.getForceRtpRtcpEncryption(),
					ioStreamTpMain.getIsRtspsConnection(),
					cfgIsDebugDisableTransportUdp
				);
		} catch (RtspProtoInvalidTpSettingsException e) {
			throw new RtspProtoUnsupportedTransportException("Invalid Transport: " + e.getMessage());
		}

		if (tmpSiSs.getSubStreamTpPtr().getIsUdp()) {  // only update one-way
			ioStreamTpMain.setIsTransportUdp(true);
		}
		if (tmpSiSs.getSubStreamTpPtr().getIsEncr()) {  // only update one-way
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

	private void processRequiredFeatures(@NonNull Set<@NonNull String> requiredFeatures)
			throws RtspProtoUnsupportedFeatureRequestedException {
		if (requiredFeatures.isEmpty()) {
			return;
		}
		// we need to respond with "551 Option not supported"
		// @TODO make custom supported features configurable
		throw new RtspProtoUnsupportedFeatureRequestedException(
				requiredFeatures.stream().findFirst().orElse("")
			);
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

	private void handleKmdFromMikey(
				boolean isSetup,
				@NonNull RtspProtoRscUrl rscUrlObj,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull SrtxpKmd kmd
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleKmdFromMikey()";

		if (rscUrlObj.idSubStream.isEmpty()) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Sub-Stream ID must be set");
		}

		if (! ioSetupInfosStream.containsSiForSubStreamId(rscUrlObj.idSubStream)) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": Sub-Stream info not found");
		}
		RtspProtoSetupInfoForSubStream tmpSiSs = ioSetupInfosStream.getSiBySubStreamId(rscUrlObj.idSubStream).orElseThrow();

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

		if (isSetup) {
			tmpSiSs.getKmdInboundCurPtr().setKmd(kmdToUse, rscUrlObj.idSubStream);
		} else if (! tmpSiSs.getKmdInboundCurPtr().isKmdSet()) {
			logWarn(FNC_NAME, "received new inbound KMD but had no previous KMD - ignoring new KMD");
		} else if (tmpSiSs.getKmdInboundCurPtr().getKmd().orElseThrow().mki().isEmpty()) {
			logWarn(FNC_NAME, "received new inbound KMD but previous KMD had no MKI - ignoring new KMD");
		} else if (kmdToUse.mki().isEmpty()) {
			logWarn(FNC_NAME, "received new inbound KMD but it has no MKI - ignoring new KMD");
		} else if (kmdToUse.mki().value() == tmpSiSs.getKmdInboundCurPtr().getKmd().orElseThrow().mki().value()) {
			logWarn(FNC_NAME, "received new inbound KMD but MKI is unchanged - ignoring new KMD");
		} else if (kmdToUse.ssrcId() != tmpSiSs.getKmdInboundCurPtr().getKmd().orElseThrow().ssrcId()) {
			logWarn(FNC_NAME, "received new inbound KMD but SSRC has been modified - ignoring new KMD");
		} else {
			tmpSiSs.getKmdInboundNextPtr().setKmd(kmdToUse, rscUrlObj.idSubStream);
		}
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
				outputDataRequ.requAnnouncedSdp.copyFrom(input.bodyAnnounceSdp);
				// Content-Base
				if (! input.headers.containsKey(RtspHeaderKey.CONTENT_BASE)) {
					throw new RtspProtoInvalidRequestException("Content-Base for ANNOUNCE message missing");
				}
				outputDataRequ.requAnnouncedSdp.setContentBase(
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
				outputDataRequ.requAnnouncedSdp.setContentLang(tmpContLang);
			} else {
				outputDataRequ.requSetParamValues.setContentLang(tmpContLang);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleBody(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspProtoRtspParamUnknownException, RtspProtoSdpException {
		switch (messageType) {
			case ANNOUNCE:
				handleBody_announce(outputDataRequ);
				break;
			case SET_PARAMETER:
				handleBody_setParameter(outputDataRequ);
				break;
		}
	}

	private void handleBody_announce(@NonNull RtspProtoDataRequest outputDataRequ) throws RtspProtoSdpException {
		sdpConsumerInterface.parseUpdatedSdpFromAnnounce(outputDataRequ.requAnnouncedSdp);
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

		// if one of the parameters cannot be set, the entire request needs to be rejected
		for (Map.Entry<@NonNull String, @NonNull String> entry : outputDataRequ.requSetParamValues.getParamKvsEntrySet()) {
			try {
				parameterSetterInterface.testSettingRtspParameter(
						outputDataRequ.rrIdSession,
						outputDataRequ.requSetParamValues.getContentLang(),
						entry.getKey(),
						entry.getValue()
					);
			} catch (RtspProtoRtspParamUnknownException e) {
				outputDataRequ.rrInvalidParamNames.putParamName(entry.getKey());
				logWarn(FNC_NAME, "Unknown parameter: '" + entry.getKey() + "'");
			} catch (RtspProtoRtspParamInvalidValueException e) {
				outputDataRequ.rrInvalidParamNames.putParamName(entry.getKey());
				logWarn(FNC_NAME, "Invalid value for parameter key='" + entry.getKey() + "': '" + entry.getValue() + "'");
			}
		}
		if (! outputDataRequ.rrInvalidParamNames.isParamNamesEmpty()) {
			throw new RtspProtoRtspParamUnknownException(
					String.join(", ", outputDataRequ.rrInvalidParamNames.getParamNames())
				);
		}

		//
		for (Map.Entry<@NonNull String, @NonNull String> entry : outputDataRequ.requSetParamValues.getParamKvsEntrySet()) {
			try {
				parameterSetterInterface.setRtspParameter(
						outputDataRequ.rrIdSession,
						outputDataRequ.requSetParamValues.getContentLang(),
						entry.getKey(),
						entry.getValue()
					);
			} catch (RtspProtoRtspParamUnknownException | RtspProtoRtspParamInvalidValueException e) {
				// this cannot happen
			}
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

package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.MikeyParser;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.*;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoParameterSetterInterface;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntCseqRequInp;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataRequest;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.*;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.SdpConsumerInterface;

import java.net.InetAddress;
import java.util.*;

public final class RtspProtoHighRequestConsumer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtspConfig rtspConfig;
	private final boolean isRequestFromClient;
	private final @NonNull SdpConsumerInterface sdpConsumerInterface;
	private final @Nullable RtspProtoParameterSetterInterface parameterSetterInterface;

	private final Set<@NonNull RtspHeaderKey> preProcessedHeaders = new HashSet<>();

	public RtspProtoHighRequestConsumer(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo,
				boolean isRequestFromClient,
				@NonNull SdpConsumerInterface sdpConsumerInterface,
				@Nullable RtspProtoParameterSetterInterface parameterSetterInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
		this.isRequestFromClient = isRequestFromClient;
		this.sdpConsumerInterface = sdpConsumerInterface;
		this.parameterSetterInterface = parameterSetterInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspRequestBasics processRequest(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequIo,
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processRequest()";

		//System.out.println("<<<<<<<<< <<<<<<<<< " + input);

		preProcessedHeaders.clear();

		currentIdSession.writeProtect();
		outputDataRequ.clear();

		//
		final String logMsgSuffix = " for " + input.messageType + " request, " +
				"rejecting request (URL='" + input.resourceUrl + "')";

		//
		RtspStatusCode tmpPreflightSc = preflightChecks(
				FNC_NAME,
				logMsgSuffix,
				currentIdSession,
				cseqRequIo,
				input,
				outputDataRequ
			);
		if (tmpPreflightSc != RtspStatusCode.OK) {
			return RtspRequestBasics.createKnownWithError(input.messageType, tmpPreflightSc);
		}

		//
		outputDataRequ.requIdSession.copyFrom(currentIdSession);

		// process resource URL
		RtspRequestBasics.RequestUrlInputOrStreamSource ruioss;
		try {
			ruioss = processResourceUrl(input.messageType, input.resourceUrl);
		} catch (RtspInvalidUriException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.FORBIDDEN);
		} catch (RtspInputSourceIdNotFoundException | RtspSubStreamIdNotFoundException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.NOT_FOUND);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.INTERNAL_SERVER_ERROR);
		}

		// process resource URL query parameters
		try {
			processQueryParams(input);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.BAD_REQUEST);
		}

		// process headers that haven't been processed yet
		try {
			processRemainingHeaders(input, ruioss, outputDataRequ);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.BAD_REQUEST);
		} catch (RtspUnsupportedTransportException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.UNSUPPORTED_TRANSPORT);
		} catch (RtspUnsupportedFeatureRequestedException e) {
			logWarn(FNC_NAME, "Requested Option '" + e.getMessage() + "' not supported" + logMsgSuffix);
			outputDataRequ.setUnsupportedFeatureName(e.getMessage());
			return RtspRequestBasics.createKnownWithOptionNotSupported(input.messageType);
		}

		// process body
		if (input.messageType == RtspMessageType.ANNOUNCE ||
				input.messageType == RtspMessageType.GET_PARAMETER || input.messageType == RtspMessageType.SET_PARAMETER) {
			try {
				processBody(input, outputDataRequ);
			} catch (RtspInvalidRequestException e) {
				logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
				return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.BAD_REQUEST);
			}
		}

		// handle body
		try {
			handleBody(input.messageType, outputDataRequ);
		} catch (RtspUnknownRtspParamException e) {
			return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.INVALID_PARAMETER);
		} catch (RtspSdpException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(input.messageType, RtspStatusCode.BAD_REQUEST);
		}

		//
		return RtspRequestBasics.createOk(input.messageType, ruioss);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull RtspStatusCode preflightChecks(
				@NonNull String fncName,
				@NonNull String logMsgSuffix,
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRequInp cseqRequIo,
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) {
		try {
			checkAndUpdateRtspProtoVersion(input, outputDataRequ);
			checkAndUpdateCseq(cseqRequIo, input, outputDataRequ);
		} catch (RtspInvalidRequestException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return RtspStatusCode.BAD_REQUEST;
		}
		try {
			checkSessionId(currentIdSession, input);
		} catch (RtspInvalidRequestException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return RtspStatusCode.SESSION_NOT_FOUND;
		}
		try {
			checkMessageType(input);
		} catch (RtspInvalidRequestException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return RtspStatusCode.METHOD_NOT_ALLOWED;
		}
		try {
			// check whether the request is allowed in the current RTSP state
			checkRequestTypeVsState(input);
		} catch (RtspInvalidRequestException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return RtspStatusCode.METHOD_NOT_VALID_IN_THIS_STATE;
		}
		return RtspStatusCode.OK;
	}

	private void checkAndUpdateRtspProtoVersion(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspInvalidRequestException {
		if (input.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspInvalidRequestException("Missing RTSP protocol version");
		}
		outputDataRequ.setRtspProtoVersionToUse(input.rtspProtoVersion);
	}

	private void checkAndUpdateCseq(
				@NonNull RtspProtoDataCntCseqRequInp cseqRequIo,
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspInvalidRequestException {
		Optional<Integer> tmpOptCseq = input.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			throw new RtspInvalidRequestException("Missing CSeq header");
		}
		cseqRequIo.setCseqNrLastRcvd(Integer.toUnsignedLong(tmpOptCseq.get()));
		if (cseqRequIo.getCseqNrLastRcvd() > cseqRequIo.getCseqNrExpected()) {
			cseqRequIo.setCseqNrExpected(cseqRequIo.getCseqNrLastRcvd());
		} else if (cseqRequIo.getCseqNrLastRcvd() < cseqRequIo.getCseqNrExpected()) {
			throw new RtspInvalidRequestException("Invalid CSeq value");
		}

		//
		outputDataRequ.setCseqNrLastRcvd(cseqRequIo.getCseqNrLastRcvd());

		//
		preProcessedHeaders.add(RtspHeaderKey.CSEQ);
	}

	private void checkSessionId(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoHighMsgStructuredRequest input
			) throws RtspInvalidRequestException {
		switch (input.messageType) {
			case RtspMessageType.PLAY:
			case RtspMessageType.PAUSE:
			case RtspMessageType.GET_PARAMETER:
			case RtspMessageType.SET_PARAMETER:
			case RtspMessageType.TEARDOWN:
				Optional<RtspProtoIdSession> tmpOptSessionId = input.getHeaderSessionId();
				if (tmpOptSessionId.isEmpty()) {
					throw new RtspInvalidRequestException("Missing Session header");
				}

				if (currentIdSession.isEmpty() || ! tmpOptSessionId.get().equals(currentIdSession)) {
					throw new RtspInvalidRequestException("Invalid/missing Session ID");
				}
				break;
			default:
				break;
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.SESSION);
	}

	private void checkMessageType(@NonNull RtspProtoHighMsgStructuredRequest input) throws RtspInvalidRequestException {
		if (! RtspProtoHighConstants.LH_SUPPORTED_MESSAGE_TYPES.contains(input.messageType)) {
			throw new RtspInvalidRequestException("Unsupported request type");
		}
	}

	private void checkRequestTypeVsState(@NonNull RtspProtoHighMsgStructuredRequest input) throws RtspInvalidRequestException {
		if (input.messageType == RtspMessageType.OPTIONS ||
				input.messageType == RtspMessageType.DESCRIBE ||
				input.messageType == RtspMessageType.SETUP ||
				input.messageType == RtspMessageType.GET_PARAMETER ||
				input.messageType == RtspMessageType.SET_PARAMETER) {
			return;
		}

		boolean wasOk = false;
		switch (rtspSessionInfo.sessionState) {
			case READY:
				if (input.messageType == RtspMessageType.PLAY ||
						input.messageType == RtspMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
			case PLAYING:
				if (input.messageType == RtspMessageType.PAUSE ||
						input.messageType == RtspMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
		}

		if (! wasOk) {
			throw new RtspInvalidRequestException(String.format("Request %s not valid for current RTSP state %s",
					input.messageType, rtspSessionInfo.sessionState));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource processResourceUrl(
				@NonNull RtspMessageType requestType,
				@NonNull String resourceUrl
			) throws RtspInvalidUriException, RtspInputSourceIdNotFoundException, RtspSubStreamIdNotFoundException,
					RtspInvalidRequestException{
		if (rtspSessionInfo.isRtspsConnection && ! resourceUrl.startsWith(RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL + "://")) {
			throw new RtspInvalidUriException("Invalid URL for RTSPS");
		}
		if (! rtspSessionInfo.isRtspsConnection && ! resourceUrl.startsWith(RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL + "://")) {
			throw new RtspInvalidUriException("Invalid URL for RTSP");
		}

		//
		final ResourceUrlParsingVars resourceUrlParsingVars = new ResourceUrlParsingVars(requestType, resourceUrl);

		//
		InetAddress tmpRemoteHostIpAddr = getRemoteHostIpAddr();

		//
		ResourceUrlProcessor.extractSubStreamId(resourceUrlParsingVars);
		if (! resourceUrlParsingVars.subStreamId.isBlank()) {
			ResourceUrlProcessor.findStreamSourceObj(
					resourceUrlParsingVars,
					tmpRemoteHostIpAddr,
					rtspConfig
				);
		}

		//
		if (requestType == RtspMessageType.SETUP) {
			ResourceUrlProcessor.createSetupSubStreamRecord(resourceUrlParsingVars, tmpRemoteHostIpAddr);
			//
			RtspRequestBasics.RequestUrlInputOrStreamSource resObj = ResourceUrlProcessor.buildRuiossForSetupRequest(
					resourceUrlParsingVars,
					tmpRemoteHostIpAddr
				);
			// preliminary setting
			Objects.requireNonNull(resObj.inputSourceId);
			rtspSessionInfo.isRtpRtcpEncryptionRequired =
					rtspConfig.getInputSourceObj(resObj.inputSourceId).orElseThrow().getNeedsEncryption();
			//
			Objects.requireNonNull(resObj.subStreamId);
			rtspSessionInfo.setResourceUrlForMt_onlySetup(resObj.subStreamId, resourceUrl);
			return resObj;
		}

		//
		RtspRequestBasics.RequestUrlInputOrStreamSource resObj = new RtspRequestBasics.RequestUrlInputOrStreamSource();

		ResourceUrlProcessor.storeSubStreamAndStreamSourceIdsForNonSetupRequests(
				resourceUrlParsingVars,
				tmpRemoteHostIpAddr,
				resObj
			);

		//
		RtspInputSource tmpInputSrcObj = ResourceUrlProcessor.findInputSourceObjectForNonSetupRequests(
				resourceUrlParsingVars,
				rtspConfig
			);
		rtspSessionInfo.setResourceUrlForMt_nonSetup(requestType, resourceUrl);
		rtspSessionInfo.setInputSourceObjForMt_nonSetup(requestType, tmpInputSrcObj);
		// preliminary setting
		rtspSessionInfo.isRtpRtcpEncryptionRequired = tmpInputSrcObj.getNeedsEncryption();

		resObj.inputSourceId = tmpInputSrcObj.getId();
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processQueryParams(@NonNull RtspProtoHighMsgStructuredRequest input) throws RtspInvalidRequestException {
		for (Map.Entry<@NonNull String, @NonNull String> entry : input.queryParams.entrySet()) {
			if (! entry.getKey().equalsIgnoreCase(RtspProtoHighConstants.URL_QUERY_PARAM_SRTP)) {
				continue;
			}
			if (entry.getValue().equalsIgnoreCase("1")) {
				rtspSessionInfo.forceRtpRtcpEncryption = true;
			} else if (entry.getValue().equalsIgnoreCase("0")) {
				rtspSessionInfo.forceRtpRtcpEncryption = false;
			} else {
				throw new RtspInvalidRequestException("Invalid value for URL Query parameter '" + entry.getKey() + "': " +
						"'" + entry.getValue() + "'");
			}
			break;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processRemainingHeaders(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource ruioss,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspInvalidRequestException, RtspUnsupportedTransportException, RtspUnsupportedFeatureRequestedException {
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
				case RtspHeaderKey.KEYMGMT -> processHeader_com_keymgmt(input.messageType, ruioss, entry.getValue());
				case RtspHeaderKey.PROXY_REQU -> processHeader_com_proxyrequ(entry.getValue());
				case RtspHeaderKey.RANGE -> processHeader_play_range(input.messageType, entry.getValue());
				case RtspHeaderKey.REQUIRE -> processHeader_com_require(entry.getValue());
				case RtspHeaderKey.TRANSPORT -> processHeader_setup_transport(input.messageType, ruioss, entry.getValue());
				case RtspHeaderKey.USERAGENT -> processHeader_com_useragent(entry.getValue());
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
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryRequest headerEntry
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_describe_accept()";

		if (messageType != RtspMessageType.DESCRIBE) {
			logWarn(FNC_NAME, "Received Accept header in non-DESCRIBE request");
			return;
		}
		if (headerEntry.hdValAccept.rtspMimeType != RtspMimeType.SDP) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Only SDP allowed for Accept header value");
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
			throws RtspInvalidRequestException {
		if (headerEntry.hdValConnection.connectionPol == RtspConnectionPolicy.NONE) {
			throw new RtspInvalidRequestException("Connection Policy must be set");
		}
	}

	private void processHeader_announce_contbase(@NonNull RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_announce_contbase()";

		if (messageType != RtspMessageType.ANNOUNCE) {
			logWarn(FNC_NAME, "Received Content-Base header in non-ANNOUNCE request");
		}
	}

	private void processHeader_com_contenc(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryRequest headerEntry
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contenc()";

		if (! allowOnlyAnnounceGetOrSetParameter(FNC_NAME, "Content-Encoding", messageType)) {
			return;
		}
		if (headerEntry.hdValContEnc.contentEnc != RtspContentEncoding.NONE) {
			throw new RtspInvalidRequestException("No Content-Encoding other than NONE is supported");
		}
	}

	private void processHeader_com_contlang(@NonNull RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlang()";

		allowOnlyAnnounceGetOrSetParameter(FNC_NAME, "Content-Language", messageType);
	}

	private void processHeader_com_contlen(@NonNull RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlen()";

		allowOnlyAnnounceGetOrSetParameter(FNC_NAME, "Content-Length", messageType);
	}

	private void processHeader_com_conttype(@NonNull RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_conttype()";

		allowOnlyAnnounceGetOrSetParameter(FNC_NAME, "Content-Type", messageType);
	}

	private void processHeader_com_keymgmt(
				@NonNull RtspMessageType messageType,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource ruioss,
				@NonNull RtspProtoHeaderEntryRequest headerEntry
			) throws RtspInvalidRequestException {
		if (messageType != RtspMessageType.SETUP && messageType != RtspMessageType.SET_PARAMETER) {
			throw new RtspInvalidRequestException("Received Keymgmt header in non-SETUP/SET_PARAMETER request");
		}
		if (ruioss.inputSourceId == null ||
				ruioss.streamSourceId < 0) {
			throw new RtspInvalidRequestException("No IS/SS in SETUP/SET_PARAMETER request");
		}
		if (headerEntry.hdValKeymgmt.proto == RtspKeymgmtProto.NONE) {
			throw new RtspInvalidRequestException("Unsupported Keymgmt protocol in SETUP/SET_PARAMETER request");
		}

		SrtxpKmd tmpKmd;
		try {
			tmpKmd = MikeyParser.parseMickeyMsgIntoKmd(headerEntry.hdValKeymgmt.dataStr);
		} catch (SrtxpSecurityException e) {
			throw new RtspInvalidRequestException("Failed to set client MIKEY: " + e.getMessage());
		}

		handleKmdFromMikey(messageType == RtspMessageType.SETUP, ruioss, tmpKmd);
	}

	private void processHeader_com_proxyrequ(@NonNull RtspProtoHeaderEntryRequest headerEntry)
			throws RtspUnsupportedFeatureRequestedException {
		processRequiredFeatures(headerEntry.hdValProxyRequ.requiredFeatures);
	}

	private void processHeader_play_range(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryRequest headerEntry
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_play_range()";

		if (messageType != RtspMessageType.PLAY) {
			logWarn(FNC_NAME, "Received Range header in non-PLAY request");
			return;
		}
		rtspSessionInfo.clientPlaybackRangeValue = headerEntry.hdValRange.rangeStr;
	}

	private void processHeader_com_require(@NonNull RtspProtoHeaderEntryRequest headerEntry)
			throws RtspUnsupportedFeatureRequestedException {
		processRequiredFeatures(headerEntry.hdValRequire.requiredFeatures);
	}

	private void processHeader_setup_transport(
				@NonNull RtspMessageType messageType,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource ruioss,
				@NonNull RtspProtoHeaderEntryRequest headerEntry
			) throws RtspInvalidRequestException, RtspUnsupportedTransportException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_setup_transport()";

		if (messageType != RtspMessageType.SETUP) {
			throw new RtspInvalidRequestException("Received Transport header in non-SETUP request");
		}
		if (ruioss.inputSourceId == null ||
				ruioss.streamSourceId < 0) {
			throw new RtspInvalidRequestException("No IS/SS in SETUP request");
		}
		if (ruioss.subStreamId == null) {
			throw new RtspInvalidRequestException("No SSID in SETUP request");
		}

		RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStream = RtspStaticSessionInfo.getSetupSubStreamOrThrow(
				FNC_NAME,
				ruioss.subStreamId
			);

		// copy settings
		tmpSetupSubStream.tpIsUdp = headerEntry.hdValTransport.tpIsUdp;
		tmpSetupSubStream.tpIsUnicast = headerEntry.hdValTransport.tpIsUnicast;
		if (tmpSetupSubStream.tpIsUdp) {
			tmpSetupSubStream.tpClientUdpPortRtp = Short.toUnsignedInt(headerEntry.hdValTransport.getClientUdpPortRtp16bit()
					.orElseThrow(() -> new RtspInvalidRequestException("No client UDP RTP port in SETUP request")));
			tmpSetupSubStream.tpClientUdpPortRtcp = Short.toUnsignedInt(headerEntry.hdValTransport.getClientUdpPortRtcp16bit()
					.orElseThrow(() -> new RtspInvalidRequestException("No client UDP RTCP port in SETUP request")));
		} else {
			tmpSetupSubStream.tpClientTcpChannRtp = Short.toUnsignedInt(headerEntry.hdValTransport.getClientTcpChannRtp16bit()
					.orElseThrow(() -> new RtspInvalidRequestException("No client TCP RTP channel in SETUP request")));
			tmpSetupSubStream.tpClientTcpChannRtcp = Short.toUnsignedInt(headerEntry.hdValTransport.getClientTcpChannRtcp16bit()
					.orElseThrow(() -> new RtspInvalidRequestException("No client TCP RTCP channel in SETUP request")));
		}
		tmpSetupSubStream.tpIsInterleaved = headerEntry.hdValTransport.tpIsInterleaved;
		tmpSetupSubStream.tpIsEncr = headerEntry.hdValTransport.tpIsEncr;

		//
		if (rtspSessionInfo.forceRtpRtcpEncryption) {
			if (! tmpSetupSubStream.tpIsEncr) {
				logWarn(FNC_NAME, "Client requested unencrypted Transport but server will force encryption");
			}
			tmpSetupSubStream.tpIsEncr = true;
		}

		//
		try {
			tmpSetupSubStream.isTransportValid(
					rtspSessionInfo.isRtpRtcpEncryptionRequired,
					rtspSessionInfo.forceRtpRtcpEncryption,
					rtspSessionInfo.isRtspsConnection,
					rtspConfig.getIsDebugDisableTransportUdp()
				);
		} catch (Exception e) {
			throw new RtspUnsupportedTransportException("Invalid Transport: " + e.getMessage());
		}

		rtspSessionInfo.isTransportUdp = tmpSetupSubStream.tpIsUdp;
		rtspSessionInfo.isTransportSrtpSrtcp = tmpSetupSubStream.tpIsEncr;
	}

	private void processHeader_com_useragent(@NonNull RtspProtoHeaderEntryRequest headerEntry) {
		rtspSessionInfo.clientUserAgent = headerEntry.hdValUserAgent.userAgentStr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processRequiredFeatures(@NonNull Set<@NonNull String> requiredFeatures)
			throws RtspUnsupportedFeatureRequestedException {
		if (requiredFeatures.isEmpty()) {
			return;
		}
		// we need to respond with "551 Option not supported"
		// @TODO make custom supported features configurable
		throw new RtspUnsupportedFeatureRequestedException(
				requiredFeatures.stream().findFirst().orElse("")
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean allowOnlyAnnounceGetOrSetParameter(
				@NonNull String fncName,
				@NonNull String hdDesc,
				@NonNull RtspMessageType messageType
			) {
		if (messageType != RtspMessageType.ANNOUNCE &&
				messageType != RtspMessageType.GET_PARAMETER && messageType != RtspMessageType.SET_PARAMETER) {
			logWarn(fncName, hdDesc + " header is only valid for " +
					"ANNOUNCE/GET_PARAMETER/SET_PARAMETER requests");
			return false;
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleKmdFromMikey(
				boolean isSetup,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource ruioss,
				@NonNull SrtxpKmd kmd
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleKmdFromMikey()";

		InetAddress tmpRemoteHostIpAddr = getRemoteHostIpAddr();

		Objects.requireNonNull(
				ruioss.subStreamId,
				FNC_NAME + ": ruioss.subStreamId is null"
			);
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				tmpRemoteHostIpAddr,
				ruioss.subStreamId,
				RandomHelper.getRandomUint32(false)
			);

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
			tmpStreamKmds.kmdInbound = kmdToUse.clone();
		} else if (tmpStreamKmds.kmdInbound == null) {
			logWarn(FNC_NAME, "received new inbound KMD but had no previous KMD - ignoring new KMD");
		} else if (tmpStreamKmds.kmdInbound.mki().isEmpty()) {
			logWarn(FNC_NAME, "received new inbound KMD but previous KMD had no MKI - ignoring new KMD");
		} else if (kmdToUse.mki().isEmpty()) {
			logWarn(FNC_NAME, "received new inbound KMD but it has no MKI - ignoring new KMD");
		} else if (kmdToUse.mki().value() == tmpStreamKmds.kmdInbound.mki().value()) {
			logWarn(FNC_NAME, "received new inbound KMD but MKI is unchanged - ignoring new KMD");
		} else if (kmdToUse.ssrcId() != tmpStreamKmds.kmdInbound.ssrcId()) {
			logWarn(FNC_NAME, "received new inbound KMD but SSRC has been modified - ignoring new KMD");
		} else {
			tmpStreamKmds.nextKmdInbound = kmdToUse.clone();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processBody(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspInvalidRequestException {
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
			if (input.messageType == RtspMessageType.ANNOUNCE) {
				throw new RtspInvalidRequestException("Content-Type header is required for ANNOUNCE message");
			}
			return;
		}
		RtspMimeType contentType = input.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType;
		// Content-Length
		boolean haveHdContLen = input.headers.containsKey(RtspHeaderKey.CONTENT_LEN);
		if (! haveHdContLen) {
			if (input.messageType == RtspMessageType.ANNOUNCE) {
				throw new RtspInvalidRequestException("Content-Length header is required for ANNOUNCE message");
			}
			return;
		}
		int contentLengthInt = input.headers.get(RtspHeaderKey.CONTENT_LEN).hdValContLen.getContentLen32bit().orElseThrow();
		long contentLengthLong = Integer.toUnsignedLong(contentLengthInt);
		if (contentLengthLong == 0L) {
			if (input.messageType == RtspMessageType.ANNOUNCE) {
				throw new RtspInvalidRequestException("Body for ANNOUNCE message missing");
			}
			return;
		}

		//
		switch (input.messageType) {
			case ANNOUNCE:
				if (contentType != RtspMimeType.SDP) {
					throw new RtspInvalidRequestException("Content-Type for ANNOUNCE message must be SDP");
				}
				//
				outputDataRequ.requAnnouncedSdp.copyFrom(input.bodyAnnounceSdp);
				// Content-Base
				if (! input.headers.containsKey(RtspHeaderKey.CONTENT_BASE)) {
					throw new RtspInvalidRequestException("Content-Base for ANNOUNCE message missing");
				}
				outputDataRequ.requAnnouncedSdp.setContentBase(
						input.headers.get(RtspHeaderKey.CONTENT_BASE).hdValContBase.contentBaseStr
					);
				break;
			case GET_PARAMETER:
				if (contentType != RtspMimeType.PARAMETERS) {
					throw new RtspInvalidRequestException("Content-Type for GET_PARAMETER message must be PARAMETERS");
				}
				outputDataRequ.requGetParamNames.copyFrom(input.bodyGetParamNames);
				break;
			case SET_PARAMETER:
				if (contentType != RtspMimeType.PARAMETERS) {
					throw new RtspInvalidRequestException("Content-Type for SET_PARAMETER message must be PARAMETERS");
				}
				outputDataRequ.requSetParamValues.copyFrom(input.bodySetParamKv);
				break;
		}

		// Content-Language
		if ((input.messageType == RtspMessageType.ANNOUNCE || input.messageType == RtspMessageType.SET_PARAMETER) &&
				input.headers.containsKey(RtspHeaderKey.CONTENT_LANG)) {
			String tmpContLang = input.headers.get(RtspHeaderKey.CONTENT_LANG).hdValContLang.contentLangStr;
			if (input.messageType == RtspMessageType.ANNOUNCE) {
				outputDataRequ.requAnnouncedSdp.setContentLang(tmpContLang);
			} else {
				outputDataRequ.requSetParamValues.setContentLang(tmpContLang);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleBody(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoDataRequest outputDataRequ
			) throws RtspUnknownRtspParamException, RtspSdpException {
		switch (messageType) {
			case ANNOUNCE:
				handleBody_announce(outputDataRequ);
				break;
			case SET_PARAMETER:
				handleBody_setParameter(outputDataRequ);
				break;
		}
	}

	private void handleBody_announce(@NonNull RtspProtoDataRequest outputDataRequ) throws RtspSdpException {
		sdpConsumerInterface.parseUpdatedSdpFromAnnounce(outputDataRequ.requAnnouncedSdp);
	}

	private void handleBody_setParameter(@NonNull RtspProtoDataRequest outputDataRequ) throws RtspUnknownRtspParamException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleBody_setParameter()";

		if (outputDataRequ.requSetParamValues.isParamKvsEmpty()) {
			return;
		}
		if (parameterSetterInterface == null) {
			outputDataRequ.requInvalidParamNames.putAllParamNames(outputDataRequ.requSetParamValues.getParamKvsKeySet());
			logWarn(FNC_NAME, "RtspProtoParameterSetterInterface is not set");
			throw new RtspUnknownRtspParamException("all params");
		}

		for (Map.Entry<@NonNull String, @NonNull String> entry : outputDataRequ.requSetParamValues.getParamKvsEntrySet()) {
			try {
				parameterSetterInterface.setRtspParameter(
						outputDataRequ.requIdSession,
						outputDataRequ.requSetParamValues.getContentLang(),
						entry.getKey(),
						entry.getValue()
					);
			} catch (RtspUnknownRtspParamException e) {
				outputDataRequ.requInvalidParamNames.putParamName(entry.getKey());
				logWarn(FNC_NAME, "Unknown parameter: " + entry.getKey());
				throw new RtspUnknownRtspParamException(entry.getKey());
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull InetAddress getRemoteHostIpAddr() throws RtspInvalidRequestException {
		Optional<InetAddress> tmpOptRemoteHostIpAddr = (isRequestFromClient ?
				rtspSessionInfo.getClientIpAddr() : rtspSessionInfo.getServerIpAddr());
		final String excMsg = (isRequestFromClient ? "Client" : "Server") + " IP address is not set";
		return tmpOptRemoteHostIpAddr.orElseThrow(() -> new RtspInvalidRequestException(excMsg));
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

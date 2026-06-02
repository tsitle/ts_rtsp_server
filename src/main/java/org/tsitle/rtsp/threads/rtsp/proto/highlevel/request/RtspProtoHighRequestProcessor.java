package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.MikeyParser;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.*;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header.RtspProtoLowHeaderEntryRequest;

import java.util.*;

public final class RtspProtoHighRequestProcessor {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtspConfig rtspConfig;

	private final Set<@NonNull RtspHeaderKey> preProcessedHeaders = new HashSet<>();

	public RtspProtoHighRequestProcessor(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspRequestBasics processRequest(@NonNull RtspProtoHighMsgStructuredRequest msg) {
		final String FNC_NAME = getClass().getSimpleName() + ".processRequest()";

		System.out.println("<<<<<<<<< <<<<<<<<< " + msg);  // @TODO

		preProcessedHeaders.clear();

		rtspSessionInfo.authInfo.resetPerRequest();

		//
		final String logMsgSuffix = " for " + msg.messageType + " request, " +
				"rejecting request (URL='" + msg.resourceUrl + "')";

		//
		try {
			checkAndUpdateRtspProtoVersion(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.BAD_REQUEST);
		}
		try {
			checkAndUpdateCseq(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.BAD_REQUEST);
		}
		try {
			checkSessionId(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.SESSION_NOT_FOUND);
		}
		try {
			checkMessageType(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.METHOD_NOT_ALLOWED);
		}
		try {
			// check whether the request is allowed in the current RTSP state
			checkRequestTypeVsState(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.METHOD_NOT_VALID_IN_THIS_STATE);
		}

		// process resource URL
		RtspRequestBasics.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource;
		try {
			requestUrlInputOrStreamSource = processResourceUrl(msg.messageType, msg.resourceUrl);
		} catch (RtspInvalidUriException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.FORBIDDEN);
		} catch (RtspInputSourceIdNotFoundException | RtspSubStreamIdNotFoundException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.NOT_FOUND);
		}

		// process resource URL query parameters
		try {
			processQueryParams(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.BAD_REQUEST);
		}

		// process headers that haven't been processed yet
		try {
			processRemainingHeaders(msg, requestUrlInputOrStreamSource);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.BAD_REQUEST);
		} catch (RtspUnsupportedTransportException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspRequestBasics.createKnownWithError(msg.messageType, RtspStatusCode.UNSUPPORTED_TRANSPORT);
		} catch (RtspUnsupportedFeatureRequestedException e) {
			logWarn(FNC_NAME, "Requested Option '" + e.getMessage() + "' not supported" + logMsgSuffix);
			return RtspRequestBasics.createKnownWithOptionNotSupported(
					msg.messageType,
					e.getMessage()
				);
		}

		// process body
		// @TODO

		// add Sub-Stream ID to session info
		if (msg.messageType == RtspMessageType.SETUP) {
			Objects.requireNonNull(
					requestUrlInputOrStreamSource.subStreamId,
					FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null"
				);
			if (! rtspSessionInfo.subStreamIdsSetup.contains(requestUrlInputOrStreamSource.subStreamId)) {
				rtspSessionInfo.subStreamIdsSetup.add(requestUrlInputOrStreamSource.subStreamId);
			}
		}

		//
		return RtspRequestBasics.createOk(msg.messageType, requestUrlInputOrStreamSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAndUpdateRtspProtoVersion(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		if (msg.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspInvalidRequestException("Missing RTSP protocol version");
		}
		rtspSessionInfo.lastRequestRtspProtoVersion = msg.rtspProtoVersion;
	}

	private void checkAndUpdateCseq(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		Optional<Integer> tmpOptCseq = msg.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			throw new RtspInvalidRequestException("Missing CSeq header");
		}
		rtspSessionInfo.rtspClientSeqNrLastRcvd = tmpOptCseq.get();
		if (rtspSessionInfo.rtspClientSeqNrLastRcvd > rtspSessionInfo.rtspClientSeqNrExpected) {
			rtspSessionInfo.rtspClientSeqNrExpected = rtspSessionInfo.rtspClientSeqNrLastRcvd;
		} else if (rtspSessionInfo.rtspClientSeqNrLastRcvd < rtspSessionInfo.rtspClientSeqNrExpected) {
			throw new RtspInvalidRequestException("Invalid CSeq value");
		}
		rtspSessionInfo.rtspClientSeqNrResponse = rtspSessionInfo.rtspClientSeqNrExpected++;

		//
		preProcessedHeaders.add(RtspHeaderKey.CSEQ);
	}

	private void checkSessionId(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		switch (msg.messageType) {
			case RtspMessageType.PLAY:
			case RtspMessageType.PAUSE:
			case RtspMessageType.GET_PARAMETER:
			case RtspMessageType.SET_PARAMETER:
			case RtspMessageType.TEARDOWN:
				Optional<String> tmpOptSessionId = msg.getHeaderSessionId();
				if (tmpOptSessionId.isEmpty()) {
					throw new RtspInvalidRequestException("Missing Session header");
				}
				String tmpSessionId = tmpOptSessionId.get();

				if (rtspSessionInfo.rtspSessionId.isBlank() ||
						! tmpSessionId.equalsIgnoreCase(rtspSessionInfo.rtspSessionId)) {
					throw new RtspInvalidRequestException("Invalid/missing Session ID");
				}
				break;
			default:
				break;
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.SESSION);
	}

	private void checkMessageType(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		if (! RtspProtoConstants.SUPPORTED_MESSAGE_TYPES_SERVER.contains(msg.messageType)) {
			throw new RtspInvalidRequestException("Unsupported request type");
		}
	}

	private void checkRequestTypeVsState(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		if (msg.messageType == RtspMessageType.OPTIONS ||
				msg.messageType == RtspMessageType.DESCRIBE ||
				msg.messageType == RtspMessageType.SETUP ||
				msg.messageType == RtspMessageType.GET_PARAMETER ||
				msg.messageType == RtspMessageType.SET_PARAMETER) {
			return;
		}

		boolean wasOk = false;
		switch (rtspSessionInfo.sessionState) {
			case READY:
				if (msg.messageType == RtspMessageType.PLAY ||
						msg.messageType == RtspMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
			case PLAYING:
				if (msg.messageType == RtspMessageType.PAUSE ||
						msg.messageType == RtspMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
		}

		if (! wasOk) {
			throw new RtspInvalidRequestException(String.format("Request %s not valid for current RTSP state %s",
					msg.messageType, rtspSessionInfo.sessionState));
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource processResourceUrl(
				@NonNull RtspMessageType requestType,
				@NonNull String resourceUrl
			) throws RtspInvalidUriException, RtspInputSourceIdNotFoundException, RtspSubStreamIdNotFoundException {
		if (rtspSessionInfo.isRtspsConnection && ! resourceUrl.startsWith(RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL + "://")) {
			throw new RtspInvalidUriException("Invalid URL for RTSPS");
		}
		if (! rtspSessionInfo.isRtspsConnection && ! resourceUrl.startsWith(RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL + "://")) {
			throw new RtspInvalidUriException("Invalid URL for RTSP");
		}

		//
		final ResourceUrlParsingVars resourceUrlParsingVars = new ResourceUrlParsingVars(requestType, resourceUrl);

		//
		ResourceUrlProcessor.extractSubStreamId(resourceUrlParsingVars);
		if (! resourceUrlParsingVars.subStreamId.isBlank()) {
			ResourceUrlProcessor.findStreamSourceObj(
					resourceUrlParsingVars,
					rtspSessionInfo.getClientIpAddr(),
					rtspConfig
				);
		}

		//
		if (requestType == RtspMessageType.SETUP) {
			ResourceUrlProcessor.createSetupSubStreamRecord(resourceUrlParsingVars, rtspSessionInfo.getClientIpAddr());
			//
			RtspRequestBasics.RequestUrlInputOrStreamSource resObj = ResourceUrlProcessor.buildRuiossForSetupRequest(
					resourceUrlParsingVars,
					rtspSessionInfo.getClientIpAddr()
				);
			// preliminary setting
			Objects.requireNonNull(resObj.inputSourceId);
			rtspSessionInfo.isRtpRtcpEncryptionRequired =
					rtspConfig.getInputSourceObj(resObj.inputSourceId).orElseThrow().getNeedsEncryption();
			//
			rtspSessionInfo.inputSourceUrlPerMtMap.put(RtspMessageType.SETUP, resourceUrl);
			return resObj;
		}

		//
		RtspRequestBasics.RequestUrlInputOrStreamSource resObj = new RtspRequestBasics.RequestUrlInputOrStreamSource();

		ResourceUrlProcessor.storeSubStreamAndStreamSourceIdsForNonSetupRequests(
				resourceUrlParsingVars,
				rtspSessionInfo.getClientIpAddr(),
				resObj
			);

		//
		RtspInputSource tmpInputSrcObj = ResourceUrlProcessor.findInputSourceObjectForNonSetupRequests(
				resourceUrlParsingVars,
				rtspConfig
			);
		rtspSessionInfo.inputSourceUrlPerMtMap.put(requestType, resourceUrl);
		rtspSessionInfo.inputSourceObjPerMtMap.put(requestType, tmpInputSrcObj);
		// preliminary setting
		rtspSessionInfo.isRtpRtcpEncryptionRequired = tmpInputSrcObj.getNeedsEncryption();

		resObj.inputSourceId = tmpInputSrcObj.getId();
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processQueryParams(@NonNull RtspProtoHighMsgStructuredRequest msg) throws RtspInvalidRequestException {
		for (Map.Entry<@NonNull String, @NonNull String> entry : msg.queryParams.entrySet()) {
			if (! entry.getKey().equalsIgnoreCase(RtspProtoConstants.URL_QUERY_PARAM_SRTP)) {
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
				@NonNull RtspProtoHighMsgStructuredRequest msg,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource
			) throws RtspInvalidRequestException, RtspUnsupportedTransportException, RtspUnsupportedFeatureRequestedException {
		final String FNC_NAME = getClass().getSimpleName() + ".processRemainingHeaders()";

		for (Map.Entry<@NonNull RtspHeaderKey, @NonNull RtspProtoLowHeaderEntryRequest> entry : msg.headers.entrySet()) {
			switch (entry.getKey()) {
				case RtspHeaderKey.ACCEPT ->
						processHeader_describe_accept(msg.messageType);
				case RtspHeaderKey.AUTH_CLIENT ->
						processHeader_com_auth_client(msg.authUser, entry.getValue());
				case RtspHeaderKey.CONTENT_BASE ->
						processHeader_describe_contbase(entry.getValue());
				case RtspHeaderKey.CONTENT_LEN ->
						processHeader_com_contlen(entry.getValue());
				case RtspHeaderKey.CONTENT_TYPE ->
						processHeader_com_conttype(entry.getValue());
				case RtspHeaderKey.KEYMGMT ->
						processHeader_com_keymgmt(msg.messageType, requestUrlInputOrStreamSource, entry.getValue());
				case RtspHeaderKey.RANGE ->
						processHeader_play_range(msg.messageType, entry.getValue());
				case RtspHeaderKey.REQUIRE ->
						processHeader_options_require(msg.messageType, entry.getValue());
				case RtspHeaderKey.TRANSPORT ->
						processHeader_setup_transport(msg.messageType, requestUrlInputOrStreamSource, entry.getValue());
				case RtspHeaderKey.USERAGENT ->
						processHeader_com_useragent(entry.getValue());
				default -> {
					if (! preProcessedHeaders.contains(entry.getKey())) {
						logWarn(FNC_NAME, "Skipping header: " + entry.getKey());
					}
				}
			}
		}

		//
		if (! msg.headers.containsKey(RtspHeaderKey.AUTH_CLIENT)) {
			rtspSessionInfo.authInfo.authUser = msg.authUser;
			rtspSessionInfo.authInfo.authPlainPassword = msg.authPlainPassword;
		}
	}

	private void processHeader_describe_accept(@NonNull RtspMessageType messageType) throws RtspInvalidRequestException {
		if (messageType != RtspMessageType.DESCRIBE) {
			throw new RtspInvalidRequestException("Received ACCEPT header in non-DESCRIBE request");
		}
	}

	private void processHeader_com_auth_client(
				@NonNull String authUser,
				@NonNull RtspProtoLowHeaderEntryRequest headerEntry
			) {
		// copy parameters - ignore empty values here and reject the request later if necessary
		rtspSessionInfo.authInfo.authUser = authUser;
		rtspSessionInfo.authInfo.authPlainPassword = "";
		rtspSessionInfo.authInfo.authRealmClient = headerEntry.hdValAuthClient.authRealm;
		rtspSessionInfo.authInfo.authNonceClient = headerEntry.hdValAuthClient.authNonce;
		rtspSessionInfo.authInfo.authUri = headerEntry.hdValAuthClient.authUri;
		rtspSessionInfo.authInfo.authResp = headerEntry.hdValAuthClient.authResp;
	}

	private void processHeader_describe_contbase(@NonNull RtspProtoLowHeaderEntryRequest headerEntry) {
		// @TODO
	}

	private void processHeader_com_contlen(@NonNull RtspProtoLowHeaderEntryRequest headerEntry) {
		// @TODO
	}

	private void processHeader_com_conttype(@NonNull RtspProtoLowHeaderEntryRequest headerEntry) {
		// @TODO
	}

	private void processHeader_com_keymgmt(
				@NonNull RtspMessageType messageType,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull RtspProtoLowHeaderEntryRequest headerEntry
			) throws RtspInvalidRequestException {
		if (messageType != RtspMessageType.SETUP && messageType != RtspMessageType.SET_PARAMETER) {
			throw new RtspInvalidRequestException("Received Keymgmt header in non-SETUP/SET_PARAMETER request");
		}
		if (requestUrlInputOrStreamSource.inputSourceId == null ||
				requestUrlInputOrStreamSource.streamSourceId < 0) {
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

		handleKmd(messageType == RtspMessageType.SETUP, requestUrlInputOrStreamSource, tmpKmd);
	}

	private void processHeader_play_range(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoLowHeaderEntryRequest headerEntry
			) throws RtspInvalidRequestException {
		if (messageType != RtspMessageType.PLAY) {
			throw new RtspInvalidRequestException("Received RANGE header in non-PLAY request");
		}
		rtspSessionInfo.clientPlaybackRangeValue = headerEntry.hdValRange.rangeStr;
	}

	private void processHeader_options_require(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoLowHeaderEntryRequest headerEntry
			) throws RtspInvalidRequestException, RtspUnsupportedFeatureRequestedException {
		if (messageType != RtspMessageType.OPTIONS && messageType != RtspMessageType.SETUP) {
			throw new RtspInvalidRequestException("Received REQUIRE header in non-OPTIONS/SETUP request");
		}
		if (headerEntry.hdValRequire.requiredFeatures.isEmpty()) {
			return;
		}
		// we need to respond with "551 Option not supported"
		throw new RtspUnsupportedFeatureRequestedException(
				headerEntry.hdValRequire.requiredFeatures.stream().findFirst().orElse("")
			);
	}

	private void processHeader_setup_transport(
				@NonNull RtspMessageType messageType,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull RtspProtoLowHeaderEntryRequest headerEntry
			) throws RtspInvalidRequestException, RtspUnsupportedTransportException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_setup_transport()";

		if (messageType != RtspMessageType.SETUP) {
			throw new RtspInvalidRequestException("Received TRANSPORT header in non-SETUP request");
		}
		if (requestUrlInputOrStreamSource.inputSourceId == null ||
				requestUrlInputOrStreamSource.streamSourceId < 0) {
			throw new RtspInvalidRequestException("No IS/SS in SETUP request");
		}
		if (requestUrlInputOrStreamSource.subStreamId == null) {
			throw new RtspInvalidRequestException("No SSID in SETUP request");
		}

		RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStream = RtspStaticSessionInfo.getSetupSubStreamOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.subStreamId
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

	private void processHeader_com_useragent(@NonNull RtspProtoLowHeaderEntryRequest headerEntry) {
		rtspSessionInfo.clientUserAgent = headerEntry.hdValUserAgent.userAgentStr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleKmd(
				boolean isSetup,
				RtspRequestBasics.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull SrtxpKmd kmd
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleKmd()";

		Objects.requireNonNull(
				requestUrlInputOrStreamSource.subStreamId,
				FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null"
			);
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				rtspSessionInfo.getClientIpAddr(),
				requestUrlInputOrStreamSource.subStreamId,
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

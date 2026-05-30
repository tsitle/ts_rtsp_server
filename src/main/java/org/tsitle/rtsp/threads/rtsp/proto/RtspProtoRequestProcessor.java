package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.MikeyParser;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderEntry;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderTypeKeymgmt;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtoLowMsgStructured;

import java.net.*;
import java.util.*;

import static org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants.*;

public final class RtspProtoRequestProcessor {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @NonNull RtspConfig rtspConfig;

	public RtspProtoRequestProcessor(
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

	public RequestBasicInfo processRequest(@NonNull RtspProtoLowMsgStructured msg) {
		final String FNC_NAME = getClass().getSimpleName() + ".processRequest()";

		//System.out.println("<<<<<<<<< <<<<<<<<< " + msg);

		rtspSessionInfo.authInfo.resetPerRequest();

		//
		final String logMsgSuffix = " for " + msg.messageType + " request, " +
				"rejecting request (URL='" + msg.resourceUrl + "')";

		//
		try {
			checkAndUpdateRtspProtoVersion(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.BAD_REQUEST);
		}
		try {
			checkAndUpdateCseq(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.BAD_REQUEST);
		}
		try {
			checkSessionId(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.SESSION_NOT_FOUND);
		}
		try {
			checkMessageType(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.METHOD_NOT_ALLOWED);
		}
		try {
			// check whether the request is allowed in the current RTSP state
			checkRequestTypeVsState(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.METHOD_NOT_VALID_IN_THIS_STATE);
		}

		// process resource URL
		RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource;
		try {
			requestUrlInputOrStreamSource = processResourceUrl(msg.messageType, msg.resourceUrl);
		} catch (RtspInvalidUriException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.FORBIDDEN);
		} catch (RtspInputSourceIdNotFoundException | RtspSubStreamIdNotFoundException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.NOT_FOUND);
		}

		// process resource URL query parameters
		try {
			processQueryParams(msg);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.BAD_REQUEST);
		}

		// process headers that haven't been processed yet
		try {
			processRemainingHeaders(msg, requestUrlInputOrStreamSource);
		} catch (RtspInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.BAD_REQUEST);
		} catch (RtspUnsupportedTransportException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RequestBasicInfo.createKnownWithError(msg.messageType, RtspProtoStatusCode.UNSUPPORTED_TRANSPORT);
		}

		// add Sub-Stream ID to session info
		if (msg.messageType == RtspProtoMessageType.SETUP) {
			Objects.requireNonNull(
					requestUrlInputOrStreamSource.subStreamId,
					FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null"
				);
			if (! rtspSessionInfo.subStreamIdsSetup.contains(requestUrlInputOrStreamSource.subStreamId)) {
				rtspSessionInfo.subStreamIdsSetup.add(requestUrlInputOrStreamSource.subStreamId);
			}
		}

		//
		return RequestBasicInfo.createOk(msg.messageType, requestUrlInputOrStreamSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkAndUpdateRtspProtoVersion(@NonNull RtspProtoLowMsgStructured msg) throws RtspInvalidRequestException {
		if (msg.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspInvalidRequestException("Missing RTSP protocol version");
		}
		rtspSessionInfo.lastRequestRtspProtoVersion = msg.rtspProtoVersion;
	}

	private void checkAndUpdateCseq(@NonNull RtspProtoLowMsgStructured msg) throws RtspInvalidRequestException {
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
	}

	private void checkSessionId(@NonNull RtspProtoLowMsgStructured msg) throws RtspInvalidRequestException {
		switch (msg.messageType) {
			case RtspProtoMessageType.PLAY:
			case RtspProtoMessageType.PAUSE:
			case RtspProtoMessageType.GET_PARAMETER:
			case RtspProtoMessageType.SET_PARAMETER:
			case RtspProtoMessageType.TEARDOWN:
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
	}

	private void checkMessageType(@NonNull RtspProtoLowMsgStructured msg) throws RtspInvalidRequestException {
		if (! SUPPORTED_MESSAGE_TYPES_SERVER.contains(msg.messageType)) {
			throw new RtspInvalidRequestException("Unsupported request type");
		}
	}

	private void checkRequestTypeVsState(@NonNull RtspProtoLowMsgStructured msg) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".checkRequestTypeVsState()";

		if (msg.messageType == RtspProtoMessageType.OPTIONS ||
				msg.messageType == RtspProtoMessageType.DESCRIBE ||
				msg.messageType == RtspProtoMessageType.SETUP ||
				msg.messageType == RtspProtoMessageType.GET_PARAMETER ||
				msg.messageType == RtspProtoMessageType.SET_PARAMETER) {
			return;
		}

		boolean wasOk = false;
		switch (rtspSessionInfo.sessionState) {
			case READY:
				if (msg.messageType == RtspProtoMessageType.PLAY ||
						msg.messageType == RtspProtoMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
					wasOk = true;
				}
				break;
			case PLAYING:
				if (msg.messageType == RtspProtoMessageType.PAUSE ||
						msg.messageType == RtspProtoMessageType.TEARDOWN) {  // TEARDOWN is allowed in READY and PLAYING states
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

	private RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource processResourceUrl(
				@NonNull RtspProtoMessageType requestType,
				@NonNull String resourceUrl
			) throws RtspInvalidUriException, RtspInputSourceIdNotFoundException, RtspSubStreamIdNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".processResourceUrl()";

		if (rtspSessionInfo.isRtspsConnection && ! resourceUrl.startsWith(RTSPS_URL_PROTOCOL + "://")) {
			throw new RtspInvalidUriException("Invalid URL for RTSPS");
		}
		if (! rtspSessionInfo.isRtspsConnection && ! resourceUrl.startsWith(RTSP_URL_PROTOCOL + "://")) {
			throw new RtspInvalidUriException("Invalid URL for RTSP");
		}

		//
		final String rscUrlPathOrg = extractResourceUrlPath(resourceUrl);
		String rscUrlPathMod = rscUrlPathOrg;

		/*
		 * Extract Input Source ID from the URL.
		 * For DESCRIBE/PLAY/PAUSE/TEARDOWN requests, the Resource URL needs to contain only the Input Source ID (== SDP name):
		 *   rtsp://localhost:1051/movie.sdp
		 * For SETUP/GET_PARAMETER/SET_PARAMETER requests, the Resource URL can contain the Input Source ID and the Stream ID,
		 * or it only contains the Stream ID:
		 *   rtsp://localhost:1051/movie.sdp/streamid0
		 *   or
		 *   rtsp://localhost:1051/streamid0
		 */
		String tmpRscStreamIdStr = rscUrlPathOrg;
		int tmpIdxA = tmpRscStreamIdStr.lastIndexOf("/" + STREAM_ID_PREFIX);
		if (tmpIdxA > 0) {
			// the Resource URL Path contains the Input Source ID and the Stream ID
			tmpRscStreamIdStr = tmpRscStreamIdStr.substring(tmpIdxA + 1 + STREAM_ID_PREFIX.length());
			rscUrlPathMod = rscUrlPathMod.substring(0, tmpIdxA);
		} else if (tmpRscStreamIdStr.startsWith(STREAM_ID_PREFIX)) {
			tmpRscStreamIdStr = tmpRscStreamIdStr.substring(STREAM_ID_PREFIX.length());
		} else {
			tmpRscStreamIdStr = "";
		}
		String rscSubStreamId = "";
		RtspStreamSource rtspStreamSource = null;
		if (! tmpRscStreamIdStr.isEmpty()) {
			rscSubStreamId = tmpRscStreamIdStr;
			if (rscSubStreamId.isBlank()) {
				throw new RtspInvalidUriException("Invalid Stream Source ID in URL path: '" + rscUrlPathOrg + "'");
			}
			//
			Objects.requireNonNull(rtspSessionInfo.clientIpAddr, FNC_NAME + ": rtspSessionInfo.clientIpAddr is null");
			Optional<RtspStaticSessionInfo.SubStreamInfo> tmpSubStreamInfo =
					RtspStaticSessionInfo.getSubStreamInfo(rtspSessionInfo.clientIpAddr, rscSubStreamId);
			if (tmpSubStreamInfo.isEmpty()) {
				throw new RtspSubStreamIdNotFoundException("Sub-Stream ID='" + rscSubStreamId + "'");
			}
			//
			rtspStreamSource = rtspConfig.getStreamSourceObj(tmpSubStreamInfo.get().streamSourceId()).orElse(null);
			if (rtspStreamSource == null) {  // sanity check
				throw new RtspSubStreamIdNotFoundException("Non-existing Stream Source ID in Sub-Stream ID " +
						"'" + rscSubStreamId + "'");
			}
		}

		//
		RequestBasicInfo.RequestUrlInputOrStreamSource resObj = new RequestBasicInfo.RequestUrlInputOrStreamSource();

		//
		if (requestType == RtspProtoMessageType.SETUP) {
			if (rscSubStreamId.isBlank()) {  // sanity check
				throw new RtspInvalidUriException("Missing Sub-Stream ID in URL path: '" + rscUrlPathOrg + "'");
			}
			//
			Objects.requireNonNull(rtspSessionInfo.clientIpAddr, FNC_NAME + ": rtspSessionInfo.clientIpAddr is null");
			final String tmpErrMsgSsid = rscSubStreamId;
			RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getStreamKmds(
					rtspSessionInfo.clientIpAddr,
					rscSubStreamId
				).orElseThrow(() -> new RtspInvalidUriException("No StreamKmds for Sub-Stream ID '" + tmpErrMsgSsid + "'"));
			RtspStaticSessionInfo.addStreamInfo(
					rscSubStreamId,
					tmpStreamKmds,
					rtspStreamSource,
					resourceUrl
				);

			//
			rtspSessionInfo.inputSourceUrlPerMtMap.put(RtspProtoMessageType.SETUP, resourceUrl);

			Objects.requireNonNull(rtspSessionInfo.clientIpAddr, FNC_NAME + ": rtspSessionInfo.clientIpAddr is null");
			Optional<RtspStaticSessionInfo.SubStreamInfo> tmpSubStreamInfo =
					RtspStaticSessionInfo.getSubStreamInfo(rtspSessionInfo.clientIpAddr, rscSubStreamId);
			resObj.subStreamId = rscSubStreamId;
			resObj.inputSourceId = tmpSubStreamInfo.orElseThrow().inputSourceId();
			resObj.streamSourceId = tmpSubStreamInfo.orElseThrow().streamSourceId();

			// preliminary setting
			rtspSessionInfo.isRtpRtcpEncryptionRequired =
					rtspConfig.getInputSourceObj(resObj.inputSourceId).orElseThrow().getNeedsEncryption();
			return resObj;
		}

		if ((requestType == RtspProtoMessageType.GET_PARAMETER || requestType == RtspProtoMessageType.SET_PARAMETER) &&
				! rscSubStreamId.isBlank()) {
			Objects.requireNonNull(rtspSessionInfo.clientIpAddr, FNC_NAME + ": rtspSessionInfo.clientIpAddr is null");
			Optional<RtspStaticSessionInfo.SubStreamInfo> tmpSubStreamInfo =
					RtspStaticSessionInfo.getSubStreamInfo(rtspSessionInfo.clientIpAddr, rscSubStreamId);

			resObj.subStreamId = rscSubStreamId;
			resObj.streamSourceId = tmpSubStreamInfo.orElseThrow().streamSourceId();
		}

		//
		if (rscUrlPathMod.endsWith("/")) {
			rscUrlPathMod = rscUrlPathMod.substring(0, rscUrlPathMod.length() - 1);
		}
		Optional<RtspInputSource> optInputSource = rtspConfig.getInputSourceObj(rscUrlPathMod);
		if (optInputSource.isEmpty()) {
			throw new RtspInputSourceIdNotFoundException("URL path: '" + rscUrlPathMod + "'");
		}
		if (! optInputSource.get().getEnabled()) {
			throw new RtspInputSourceIdNotFoundException("Disabled Input Source used in URL path: '" + rscUrlPathMod + "'");
		}
		rtspSessionInfo.inputSourceUrlPerMtMap.put(requestType, resourceUrl);
		rtspSessionInfo.inputSourceObjPerMtMap.put(requestType, optInputSource.get());
		// preliminary setting
		rtspSessionInfo.isRtpRtcpEncryptionRequired = optInputSource.get().getNeedsEncryption();

		resObj.inputSourceId = optInputSource.get().getId();
		return resObj;
	}

	/**
	 * Returns the resource URL path from the given resource URL string
	 * @param resourceUrlStr Full resource URL string (e.g. 'rtsp://localhost:1051/movie.sdp/streamid0')
	 * @return The resource URL path (e.g. 'movie.sdp/streamid0')
	 * @throws RtspInvalidUriException If the resource URL is invalid
	 */
	private static @NonNull String extractResourceUrlPath(@NonNull String resourceUrlStr) throws RtspInvalidUriException {
		URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(resourceUrlStr);
		String tmpPath = rscUriObj.getPath();
		tmpPath = tmpPath.strip();
		if (tmpPath.startsWith("/")) {
			tmpPath = tmpPath.substring(1).strip();
		}
		if (tmpPath.startsWith("../") || tmpPath.contains("/../")) {
			throw new RtspInvalidUriException("Path contains '../'");
		}
		if (tmpPath.isBlank()) {
			throw new RtspInvalidUriException("Empty path");
		}
		return tmpPath;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processQueryParams(@NonNull RtspProtoLowMsgStructured msg) throws RtspInvalidRequestException {
		for (Map.Entry<@NonNull String, @NonNull String> entry : msg.queryParams.entrySet()) {
			if (! entry.getKey().equalsIgnoreCase(URL_QUERY_PARAM_SRTP)) {
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
				@NonNull RtspProtoLowMsgStructured msg,
				RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource
			) throws RtspInvalidRequestException, RtspUnsupportedTransportException {
		for (Map.Entry<@NonNull RtspProtoLowHeaderKey, @NonNull RtspProtoLowHeaderEntry> entry : msg.headers.entrySet()) {
			switch (entry.getKey()) {
				case RtspProtoLowHeaderKey.ACCEPT ->
						processHeader_describe_accept(msg.messageType);
				case RtspProtoLowHeaderKey.AUTH ->
						processHeader_com_auth(msg.authUser, entry.getValue());
				case RtspProtoLowHeaderKey.KEYMGMT ->
						processHeader_com_keymgmt(msg.messageType, requestUrlInputOrStreamSource, entry.getValue());
				case RtspProtoLowHeaderKey.RANGE ->
						processHeader_play_range(msg.messageType, entry.getValue());
				case RtspProtoLowHeaderKey.REQUIRE ->
						processHeader_options_require(msg.messageType);
				case RtspProtoLowHeaderKey.TRANSPORT ->
						processHeader_setup_transport(msg.messageType, requestUrlInputOrStreamSource, entry.getValue());
				default -> { }
			}
		}

		//
		if (! msg.headers.containsKey(RtspProtoLowHeaderKey.AUTH)) {
			rtspSessionInfo.authInfo.authUser = msg.authUser;
			rtspSessionInfo.authInfo.authPlainPassword = msg.authPlainPassword;
		}
	}

	private void processHeader_describe_accept(@NonNull RtspProtoMessageType messageType) throws RtspInvalidRequestException {
		if (messageType != RtspProtoMessageType.DESCRIBE) {
			throw new RtspInvalidRequestException("Received ACCEPT header in non-DESCRIBE request");
		}
	}

	private void processHeader_com_auth(
				@NonNull String authUser,
				@NonNull RtspProtoLowHeaderEntry headerEntry
			) {
		// copy parameters - ignore empty values here and reject the request later if necessary
		rtspSessionInfo.authInfo.authUser = authUser;
		rtspSessionInfo.authInfo.authPlainPassword = "";
		rtspSessionInfo.authInfo.authRealmClient = headerEntry.hdValAuth.authRealmClient;
		rtspSessionInfo.authInfo.authNonceClient = headerEntry.hdValAuth.authNonceClient;
		rtspSessionInfo.authInfo.authUri = headerEntry.hdValAuth.authUri;
		rtspSessionInfo.authInfo.authResp = headerEntry.hdValAuth.authResp;
	}

	private void processHeader_com_keymgmt(
				@NonNull RtspProtoMessageType messageType,
				RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull RtspProtoLowHeaderEntry headerEntry
			) throws RtspInvalidRequestException {
		if (messageType != RtspProtoMessageType.SETUP && messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspInvalidRequestException("Received KEYMGMT header in non-SETUP/SET_PARAMETER request");
		}
		if (requestUrlInputOrStreamSource.inputSourceId == null ||
				requestUrlInputOrStreamSource.streamSourceId < 0) {
			throw new RtspInvalidRequestException("No IS/SS in SETUP/SET_PARAMETER request");
		}
		if (headerEntry.hdValKeymgmt.proto != RtspProtoLowHeaderTypeKeymgmt.KeymgmtProto.MIKEY) {
			throw new RtspInvalidRequestException("Unsupported Keymgmt protocol in SETUP/SET_PARAMETER request");
		}

		SrtxpKmd tmpKmd;
		try {
			tmpKmd = MikeyParser.parseMickeyMsgIntoKmd(headerEntry.hdValKeymgmt.dataStr);
		} catch (SrtxpSecurityException e) {
			throw new RtspInvalidRequestException("Failed to set client MIKEY: " + e.getMessage());
		}

		handleKmd(messageType == RtspProtoMessageType.SETUP, requestUrlInputOrStreamSource, tmpKmd);
	}

	private void processHeader_play_range(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoLowHeaderEntry headerEntry
			) throws RtspInvalidRequestException {
		if (messageType != RtspProtoMessageType.PLAY) {
			throw new RtspInvalidRequestException("Received RANGE header in non-PLAY request");
		}
		rtspSessionInfo.clientPlaybackRangeValue = headerEntry.hdValRange.rangeStr;
	}

	private void processHeader_options_require(@NonNull RtspProtoMessageType messageType) throws RtspInvalidRequestException {
		if (messageType != RtspProtoMessageType.OPTIONS) {
			throw new RtspInvalidRequestException("Received REQUIRE header in non-OPTIONS request");
		}
		// ignore value
	}

	private void processHeader_setup_transport(
				@NonNull RtspProtoMessageType messageType,
				RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull RtspProtoLowHeaderEntry headerEntry
			) throws RtspInvalidRequestException, RtspUnsupportedTransportException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_setup_transport()";

		if (messageType != RtspProtoMessageType.SETUP) {
			throw new RtspInvalidRequestException("Received TRANSPORT header in non-SETUP request");
		}
		if (requestUrlInputOrStreamSource.inputSourceId == null ||
				requestUrlInputOrStreamSource.streamSourceId < 0) {
			throw new RtspInvalidRequestException("No IS/SS in SETUP request");
		}
		if (requestUrlInputOrStreamSource.subStreamId == null) {
			throw new RtspInvalidRequestException("No SSID in SETUP request");
		}

		RtspStaticSessionInfo.StreamInfo tmpStreamInfo = RtspStaticSessionInfo.getStreamInfoOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.subStreamId
			);

		// copy settings
		tmpStreamInfo.tpIsUdp = headerEntry.hdValTransport.tpIsUdp;
		tmpStreamInfo.tpIsUnicast = headerEntry.hdValTransport.tpIsUnicast;
		tmpStreamInfo.tpClientDestUdpPortRtp = headerEntry.hdValTransport.tpClientDestUdpPortRtp;
		tmpStreamInfo.tpClientDestUdpPortRtcp = headerEntry.hdValTransport.tpClientDestUdpPortRtcp;
		tmpStreamInfo.tpClientDestTcpChannRtp = headerEntry.hdValTransport.tpClientDestTcpChannRtp;
		tmpStreamInfo.tpClientDestTcpChannRtcp = headerEntry.hdValTransport.tpClientDestTcpChannRtcp;
		tmpStreamInfo.tpIsInterleaved = headerEntry.hdValTransport.tpIsInterleaved;
		tmpStreamInfo.tpIsEncr = headerEntry.hdValTransport.tpIsEncr;

		//
		if (rtspSessionInfo.forceRtpRtcpEncryption) {
			if (! tmpStreamInfo.tpIsEncr) {
				logWarn(FNC_NAME, "Client requested unencrypted Transport but server will force encryption");
			}
			tmpStreamInfo.tpIsEncr = true;
		}

		//
		try {
			tmpStreamInfo.isTransportValid(
					rtspSessionInfo.isRtpRtcpEncryptionRequired,
					rtspSessionInfo.forceRtpRtcpEncryption,
					rtspSessionInfo.isRtspsConnection,
					rtspConfig.getIsDebugDisableTransportUdp()
				);
		} catch (Exception e) {
			throw new RtspUnsupportedTransportException("Invalid Transport: " + e.getMessage());
		}

		rtspSessionInfo.isTransportUdp = tmpStreamInfo.tpIsUdp;
		rtspSessionInfo.isTransportSrtpSrtcp = tmpStreamInfo.tpIsEncr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleKmd(
				boolean isSetup,
				RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull SrtxpKmd kmd
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleKmd()";

		Objects.requireNonNull(
				rtspSessionInfo.clientIpAddr,
				FNC_NAME + ": rtspSessionInfo.clientIpAddr is null"
			);
		Objects.requireNonNull(
				requestUrlInputOrStreamSource.subStreamId,
				FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null"
			);
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				rtspSessionInfo.clientIpAddr,
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

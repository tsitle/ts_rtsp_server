package org.tsitle.rtsp.threads.rtsp.proto.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidSessionIdException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspResponseBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryResponse;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RtspProtoHighResponseProcessor {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final boolean isResponseFromClient;

	private final Set<@NonNull RtspHeaderKey> preProcessedHeaders = new HashSet<>();

	public RtspProtoHighResponseProcessor(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspSessionInfo rtspSessionInfo,
				boolean isResponseFromClient
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
		this.isResponseFromClient = isResponseFromClient;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspResponseBasics processResponse(@NonNull RtspProtoHighMsgStructuredResponse msg) {
		final String FNC_NAME = getClass().getSimpleName() + ".processResponse()";

		System.out.println("<<<<<<<<< <<<<<<<<< " + msg);  // @TODO

		preProcessedHeaders.clear();

		//
		final String logMsgSuffix = " in response for " + msg.messageType + " request";

		//
		try {
			checkRtspProtoVersion(msg);
		} catch (RtspInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}
		try {
			checkCseq(msg);
		} catch (RtspInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}
		try {
			checkSessionId(msg);
		} catch (RtspInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		} catch (RtspInvalidSessionIdException e) {
			logWarn(FNC_NAME, "Invalid Session ID" + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		//
		RtspResponseBasics resObj = RtspResponseBasics.createDefault(msg.statusCode);

		// process headers that haven't been processed yet
		try {
			processRemainingHeaders(msg, resObj);
		} catch (RtspInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		// process body
		// @TODO

		//
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void checkRtspProtoVersion(@NonNull RtspProtoHighMsgStructuredResponse msg) throws RtspInvalidResponseException {
		if (msg.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspInvalidResponseException("Missing RTSP protocol version");
		}
	}

	private void checkCseq(@NonNull RtspProtoHighMsgStructuredResponse msg) throws RtspInvalidResponseException {
		Optional<Integer> tmpOptCseq = msg.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			throw new RtspInvalidResponseException("Missing CSeq header");
		}
		if (tmpOptCseq.get() != rtspSessionInfo.rtspServerSeqNrExpected) {
			throw new RtspInvalidResponseException("Invalid CSeq");
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.CSEQ);
	}

	private void checkSessionId(@NonNull RtspProtoHighMsgStructuredResponse msg)
			throws RtspInvalidResponseException, RtspInvalidSessionIdException {
		if (msg.messageType != RtspMessageType.SETUP) {
			return;
		}
		Optional<String> tmpOptSessionId = msg.getHeaderSessionId();
		if (tmpOptSessionId.isEmpty()) {
			throw new RtspInvalidResponseException("Missing Session header");
		}
		String tmpSessionId = tmpOptSessionId.get();

		if (tmpSessionId.isBlank()) {
			throw new RtspInvalidSessionIdException();
		}
		if (! rtspSessionInfo.rtspSessionId.isBlank() &&
				! tmpSessionId.equalsIgnoreCase(rtspSessionInfo.rtspSessionId)) {
			throw new RtspInvalidSessionIdException();
		}
		if (isResponseFromClient) {
			rtspSessionInfo.rtspSessionId = tmpSessionId;
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.SESSION);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processRemainingHeaders(
				@NonNull RtspProtoHighMsgStructuredResponse msg,
				@NonNull RtspResponseBasics outputRrb
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processRemainingHeaders()";

		for (Map.Entry<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryResponse> entry : msg.headers.entrySet()) {
			switch (entry.getKey()) {
				case RtspHeaderKey.AUTH_SERVER ->
						processHeader_com_auth_server(entry.getValue());
				case RtspHeaderKey.CONTENT_BASE ->
						processHeader_describe_contbase(msg.messageType, entry.getValue());
				case RtspHeaderKey.CONTENT_LEN ->
						processHeader_com_contlen(entry.getValue());
				case RtspHeaderKey.CONTENT_TYPE ->
						processHeader_com_conttype(entry.getValue());
				case RtspHeaderKey.DATE ->
						processHeader_com_date(entry.getValue());
				case RtspHeaderKey.PUBLIC ->
						processHeader_options_public(msg.messageType, entry.getValue());
				case RtspHeaderKey.RANGE ->
						processHeader_play_range(msg.messageType, entry.getValue());
				case RtspHeaderKey.RTPINFO ->
						processHeader_play_rtpinfo(msg.messageType, entry.getValue());
				case RtspHeaderKey.SERVER ->
						processHeader_com_server(entry.getValue());
				case RtspHeaderKey.TRANSPORT ->
						processHeader_setup_transport(msg.messageType, entry.getValue());
				case RtspHeaderKey.UNSUPPORTED ->
						processHeader_com_unsupported(entry.getValue());
				default -> {
					if (! preProcessedHeaders.contains(entry.getKey())) {
						logWarn(FNC_NAME, "Skipping header: " + entry.getKey());
					}
				}
			}
		}
	}

	private void processHeader_com_auth_server(@NonNull RtspProtoHeaderEntryResponse headerEntry)
			throws RtspInvalidResponseException {
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Auth(Server) header from client");
		}
		// @TODO store params
	}

	private void processHeader_describe_contbase(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspInvalidResponseException {
		if (messageType != RtspMessageType.DESCRIBE) {
			throw new RtspInvalidResponseException("Received Content-Base header in non-DESCRIBE request");
		}
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Content-Base header from client");
		}
		// @TODO store params
	}

	private void processHeader_com_contlen(@NonNull RtspProtoHeaderEntryResponse headerEntry)
			throws RtspInvalidResponseException {
		// @TODO store params
	}

	private void processHeader_com_conttype(@NonNull RtspProtoHeaderEntryResponse headerEntry)
			throws RtspInvalidResponseException {
		// @TODO store params
	}

	private void processHeader_com_date(@NonNull RtspProtoHeaderEntryResponse headerEntry)
			throws RtspInvalidResponseException {
		// @TODO store params
	}

	private void processHeader_options_public(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspInvalidResponseException {
		if (messageType != RtspMessageType.OPTIONS) {
			throw new RtspInvalidResponseException("Received Public header in non-OPTIONS request");
		}
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Public header from client");
		}
		rtspSessionInfo.rhSupportedMessageTypes.clear();
		rtspSessionInfo.rhSupportedMessageTypes.addAll(headerEntry.hdValPublic.messageTypes);
	}

	private void processHeader_play_range(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspInvalidResponseException {
		if (messageType != RtspMessageType.PLAY) {
			throw new RtspInvalidResponseException("Received Range header in non-PLAY request");
		}
		// @TODO store params
	}

	private void processHeader_play_rtpinfo(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspInvalidResponseException {
		if (messageType != RtspMessageType.PLAY) {
			throw new RtspInvalidResponseException("Received RTP-Info header in non-PLAY request");
		}
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received RTP-Info header from client");
		}
		// @TODO store params
	}

	private void processHeader_com_server(@NonNull RtspProtoHeaderEntryResponse headerEntry)
			throws RtspInvalidResponseException {
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Server header from client");
		}
		// @TODO store params
	}

	private void processHeader_setup_transport(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspInvalidResponseException {
		if (messageType != RtspMessageType.SETUP) {
			throw new RtspInvalidResponseException("Received Transport header in non-SETUP request");
		}
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Transport header from client");
		}
		// @TODO store params
	}

	private void processHeader_com_unsupported(@NonNull RtspProtoHeaderEntryResponse headerEntry)
			throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_unsupported()";

		if (headerEntry.hdValUnsupported.unsupportedOptionStr.isBlank()) {
			throw new RtspInvalidResponseException("unsupportedOptionStr must be set");
		}
		logWarn(FNC_NAME, "Option '" + headerEntry.hdValUnsupported.unsupportedOptionStr + "' is not supported");
		// @TODO store params
	}

	// -----------------------------------------------------------------------------------------------------------------

	/* @TODO move to response parser
	private void parseHeaderValue_com_auth_server(
				@NonNull String hdValue,
				@NonNull RtspProtoLowHeaderEntryRequest entry
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_auth_server()";

		// e.g. 'WWW-Authenticate: Digest realm="Abcdef Some", nonce="xxx", algorithm="MD5"'
		if (! hdValue.toLowerCase()
				.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.toLowerCase())) {
			throw new RtspInvalidRequestException("Invalid Auth header prefix");
		}
		hdValue = hdValue.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.length()).strip();
		StringTokenizer tokens = new StringTokenizer(hdValue, ",");
		boolean haveRealm = false;
		boolean haveNonce = false;
		boolean haveAlgo = false;
		while (tokens.hasMoreTokens()) {
			String curTokenAsIs = tokens.nextToken().strip();
			String curTokenLc = curTokenAsIs.toLowerCase();
			if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM.toLowerCase())) {
				entry.hdValAuthServer.authRealm =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM);
				haveRealm = (! entry.hdValAuthServer.authRealm.isBlank());
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE.toLowerCase())) {
				entry.hdValAuthServer.authNonce =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE);
				entry.hdValAuthServer.authNonce = entry.hdValAuthServer.authNonce.toLowerCase();
				haveNonce = (! entry.hdValAuthServer.authNonce.isBlank());
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO.toLowerCase())) {
				String tmpAlgo =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO);
				if (! tmpAlgo.equalsIgnoreCase(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_ALGO_MD5)) {
					throw new RtspInvalidRequestException("Unsupported Auth Algorithm: '" + tmpAlgo + "'");
				}
				entry.hdValAuthServer.authAlgo = RtspProtoLowHeaderTypeAuth.AuthAlgo.MD5;
				haveAlgo = true;
			} else {
				logWarn(FNC_NAME, "Unknown Auth parameter: '" + curTokenAsIs + "'");
			}
		}

		if (! (haveRealm && haveNonce && haveAlgo)) {
			throw new RtspInvalidRequestException("Missing required Auth parameters");
		}
		entry.setHdKey(RtspHeaderKey.AUTH_SERVER);
	}
	*/

	/* @TODO move to response parser
	private void parseHeaderValue_options_public(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntryRequest entry) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_options_public()";

		*
		 * Example:
		 *   "Public: SETUP, PLAY, PAUSE, TEARDOWN, DESCRIBE, OPTIONS, SET_PARAMETER"
		 *
		for (String tmpOption : hdValue.split(",")) {
			tmpOption = tmpOption.strip();
			try {
				RtspMessageType tmpEn = RtspMessageType.valueOf(tmpOption);
				entry.hdValPublic.messageTypes.add(tmpEn);
			} catch (IllegalArgumentException e) {
				logWarn(FNC_NAME, "Unknown option: '" + tmpOption + "'");
			}
		}
		entry.setHdKey(RtspHeaderKey.PUBLIC);
	}
	*/

	// -----------------------------------------------------------------------------------------------------------------

	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

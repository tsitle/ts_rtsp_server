package org.tsitle.rtsp.threads.rtsp.proto.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidSessionIdException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspResponseBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspAuthAlgo;
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
		boolean tmpPreflight = preflightChecks(FNC_NAME, logMsgSuffix, msg);
		if (! tmpPreflight) {
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
		try {
			processBody(msg, resObj);
		} catch (RtspInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		//
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean preflightChecks(
				@NonNull String fncName,
				@NonNull String logMsgSuffix,
				@NonNull RtspProtoHighMsgStructuredResponse msg
			) {
		try {
			checkRtspProtoVersion(msg);
			checkCseq(msg);
			checkSessionId(msg);
		} catch (RtspInvalidResponseException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return false;
		} catch (RtspInvalidSessionIdException e) {
			logWarn(fncName, "Invalid Session ID" + logMsgSuffix);
			return false;
		}
		return true;
	}

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
		if (Integer.toUnsignedLong(tmpOptCseq.get()) != rtspSessionInfo.seqNr_respRem_expected) {
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
				case RtspHeaderKey.AUTH_SERVER -> processHeader_com_auth_server(entry.getValue());
				case RtspHeaderKey.CONNECTION -> processHeader_com_connection(entry.getValue());
				case RtspHeaderKey.CONTENT_BASE -> processHeader_describe_contbase(msg.messageType, entry.getValue());
				case RtspHeaderKey.CONTENT_ENC -> processHeader_com_contenc(msg.messageType, entry.getValue());
				case RtspHeaderKey.CONTENT_LANG -> processHeader_com_contlang(msg.messageType, entry.getValue());
				case RtspHeaderKey.CONTENT_LEN -> processHeader_com_contlen(msg.messageType, entry.getValue());
				case RtspHeaderKey.CONTENT_TYPE -> processHeader_com_conttype(msg.messageType, entry.getValue());
				case RtspHeaderKey.DATE -> processHeader_com_date(entry.getValue());
				case RtspHeaderKey.PUBLIC -> processHeader_options_public(msg.messageType, entry.getValue());
				case RtspHeaderKey.RANGE -> processHeader_play_range(msg.messageType, entry.getValue());
				case RtspHeaderKey.RTPINFO -> processHeader_play_rtpinfo(msg.messageType, entry.getValue());
				case RtspHeaderKey.SERVER -> processHeader_com_server(entry.getValue());
				case RtspHeaderKey.TRANSPORT -> processHeader_setup_transport(msg.messageType, entry.getValue());
				case RtspHeaderKey.UNSUPPORTED -> processHeader_com_unsupported(entry.getValue());
				default -> {
					if (! preProcessedHeaders.contains(entry.getKey())) {
						logWarn(FNC_NAME, "Skipping header: " + entry.getKey());
					}
				}
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processHeader_com_auth_server(@NonNull RtspProtoHeaderEntryResponse headerEntry)
			throws RtspInvalidResponseException {
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Auth(Server) header from client");
		}
		if (headerEntry.hdValAuthServer.authAlgo == RtspAuthAlgo.NONE) {
			throw new RtspInvalidResponseException("Auth(Server) Algo must be set");
		}
		rtspSessionInfo.authInfo.authRealmServer = headerEntry.hdValAuthServer.authRealm;
		rtspSessionInfo.authInfo.authNonceServer = headerEntry.hdValAuthServer.authNonce;
	}

	private void processHeader_com_connection(@NonNull RtspProtoHeaderEntryResponse headerEntry) {
		// @TODO store param
	}

	private void processHeader_describe_contbase(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_describe_contbase()";

		if (messageType != RtspMessageType.DESCRIBE) {
			logWarn(FNC_NAME, "Received Content-Base header in non-DESCRIBE response");
			return;
		}
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Content-Base header from client");
		}
		// @TODO store params
	}

	private void processHeader_com_contenc(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contenc()";

		if (! allowOnlyDescribeGetParameter(FNC_NAME, "Content-Encoding", messageType)) {
			return;
		}
		// @TODO store params
	}

	private void processHeader_com_contlang(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlang()";

		if (! allowOnlyDescribeGetParameter(FNC_NAME, "Content-Language", messageType)) {
			return;
		}
		// @TODO store params
	}

	private void processHeader_com_contlen(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlen()";

		if (! allowOnlyDescribeGetParameter(FNC_NAME, "Content-Length", messageType)) {
			return;
		}
		// @TODO store params
	}

	private void processHeader_com_conttype(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_conttype()";

		if (! allowOnlyDescribeGetParameter(FNC_NAME, "Content-Type", messageType)) {
			return;
		}
		// @TODO store params
	}

	private void processHeader_com_date(@NonNull RtspProtoHeaderEntryResponse headerEntry) {
		// @TODO store params
	}

	private void processHeader_options_public(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_options_public()";

		if (messageType != RtspMessageType.OPTIONS) {
			logWarn(FNC_NAME, "Received Public header in non-OPTIONS response");
			return;
		}
		rtspSessionInfo.rhSupportedMessageTypes.clear();
		rtspSessionInfo.rhSupportedMessageTypes.addAll(headerEntry.hdValPublic.messageTypes);
	}

	private void processHeader_play_range(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_play_range()";

		if (messageType != RtspMessageType.PLAY) {
			logWarn(FNC_NAME, "Received Range header in non-PLAY response");
			return;
		}
		// @TODO store params
	}

	private void processHeader_play_rtpinfo(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_play_rtpinfo()";

		if (messageType != RtspMessageType.PLAY) {
			logWarn(FNC_NAME, "Received RTP-Info header in non-PLAY response");
			return;
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
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_setup_transport()";

		if (messageType != RtspMessageType.SETUP) {
			logWarn(FNC_NAME, "Received Transport header in non-SETUP response");
			return;
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

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean allowOnlyDescribeGetParameter(
				@NonNull String fncName,
				@NonNull String hdDesc,
				@NonNull RtspMessageType messageType
			) {
		if (messageType != RtspMessageType.DESCRIBE && messageType != RtspMessageType.GET_PARAMETER) {
			logWarn(fncName, hdDesc + " header is only valid for " +
					"DESCRIBE/GET_PARAMETER responses");
			return false;
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processBody(
				@NonNull RtspProtoHighMsgStructuredResponse msg,
				@NonNull RtspResponseBasics outputRrb
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processBody()";

		// @TODO
	}

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

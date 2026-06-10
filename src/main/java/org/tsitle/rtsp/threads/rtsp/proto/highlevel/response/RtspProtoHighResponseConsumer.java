package org.tsitle.rtsp.threads.rtsp.proto.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoIdSession;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoParameterNotifyInvalidInterface;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoParameterNotifyRcvdInterface;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntCseqRespInp;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataResponse;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidSessionIdException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspSdpException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspResponseBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.SdpConsumerInterface;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RtspProtoHighResponseConsumer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isResponseFromClient;
	private final @NonNull SdpConsumerInterface sdpConsumerInterface;
	private final @Nullable RtspProtoParameterNotifyInvalidInterface parameterNotifyInvalidInterface;
	private final @Nullable RtspProtoParameterNotifyRcvdInterface parameterNotifyRcvdInterface;

	private final Set<@NonNull RtspHeaderKey> preProcessedHeaders = new HashSet<>();

	public RtspProtoHighResponseConsumer(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isResponseFromClient,
				@NonNull SdpConsumerInterface sdpConsumerInterface,
				@Nullable RtspProtoParameterNotifyInvalidInterface parameterNotifyInvalidInterface,
				@Nullable RtspProtoParameterNotifyRcvdInterface parameterNotifyRcvdInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.isResponseFromClient = isResponseFromClient;
		this.sdpConsumerInterface = sdpConsumerInterface;
		this.parameterNotifyInvalidInterface = parameterNotifyInvalidInterface;
		this.parameterNotifyRcvdInterface = parameterNotifyRcvdInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspResponseBasics processResponse(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRespInp cseqRespInp,
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoDataResponse outputDataResp
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processResponse()";

		//System.out.println("<<<<<<<<< <<<<<<<<< " + input);

		preProcessedHeaders.clear();

		currentIdSession.writeProtect();
		outputDataResp.clear();

		//
		final String logMsgSuffix = " in response for " + input.messageType + " request";

		//
		boolean tmpPreflight = preflightChecks(
				FNC_NAME,
				logMsgSuffix,
				currentIdSession,
				cseqRespInp,
				input,
				outputDataResp
			);
		if (! tmpPreflight) {
			return RtspResponseBasics.createInternalServerError();
		}

		// process headers that haven't been processed yet
		try {
			processRemainingHeaders(input, outputDataResp);
		} catch (RtspInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		// process body
		try {
			processBody(input, outputDataResp);
		} catch (RtspInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		// handle body
		try {
			handleBody(input.messageType, outputDataResp);
		} catch (RtspSdpException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		//
		return RtspResponseBasics.createDefault(input.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean preflightChecks(
				@NonNull String fncName,
				@NonNull String logMsgSuffix,
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRespInp cseqRespInp,
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoDataResponse outputDataResp
			) {
		try {
			checkRtspProtoVersion(input);
			checkCseq(cseqRespInp, input);
			checkSessionId(currentIdSession, input, outputDataResp);
		} catch (RtspInvalidResponseException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return false;
		} catch (RtspInvalidSessionIdException e) {
			logWarn(fncName, "Invalid Session ID" + logMsgSuffix);
			return false;
		}
		return true;
	}

	private void checkRtspProtoVersion(@NonNull RtspProtoHighMsgStructuredResponse input) throws RtspInvalidResponseException {
		if (input.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspInvalidResponseException("Missing RTSP protocol version");
		}
	}

	private void checkCseq(
				@NonNull RtspProtoDataCntCseqRespInp cseqRespInp,
				@NonNull RtspProtoHighMsgStructuredResponse input
			) throws RtspInvalidResponseException {
		Optional<Integer> tmpOptCseq = input.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			throw new RtspInvalidResponseException("Missing CSeq header");
		}
		if (Integer.toUnsignedLong(tmpOptCseq.get()) != cseqRespInp.getCseqNrExpected()) {
			throw new RtspInvalidResponseException("Invalid CSeq");
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.CSEQ);
	}

	private void checkSessionId(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspInvalidResponseException, RtspInvalidSessionIdException {
		if (input.messageType != RtspMessageType.SETUP) {
			return;
		}
		Optional<RtspProtoIdSession> tmpOptSessionId = input.getHeaderSessionId();
		if (tmpOptSessionId.isEmpty()) {
			throw new RtspInvalidResponseException("Missing Session header");
		}
		RtspProtoIdSession tmpSessionId = tmpOptSessionId.get();

		if (tmpSessionId.isEmpty()) {
			throw new RtspInvalidSessionIdException();
		}
		if (! currentIdSession.isEmpty() && ! tmpSessionId.equals(currentIdSession)) {
			throw new RtspInvalidSessionIdException();
		}
		if (isResponseFromClient) {
			outputDataResp.respIdSession.copyFrom(tmpSessionId);
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.SESSION);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processRemainingHeaders(
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processRemainingHeaders()";

		for (Map.Entry<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryResponse> entry : input.headers.entrySet()) {
			switch (entry.getKey()) {
				case RtspHeaderKey.AUTH_SERVER -> processHeader_com_auth_server(entry.getValue(), outputDataResp);
				case RtspHeaderKey.CONNECTION -> processHeader_com_connection(entry.getValue(), outputDataResp);
				case RtspHeaderKey.CONTENT_BASE -> processHeader_describe_contbase(input.messageType);
				case RtspHeaderKey.CONTENT_ENC -> processHeader_com_contenc(input.messageType, entry.getValue());
				case RtspHeaderKey.CONTENT_LANG -> processHeader_com_contlang(input.messageType);
				case RtspHeaderKey.CONTENT_LEN -> processHeader_com_contlen(input.messageType);
				case RtspHeaderKey.CONTENT_TYPE -> processHeader_com_conttype(input.messageType);
				case RtspHeaderKey.DATE -> processHeader_com_date();
				case RtspHeaderKey.PUBLIC -> processHeader_options_public(input.messageType, entry.getValue(), outputDataResp);
				case RtspHeaderKey.RANGE -> processHeader_play_range(input.messageType, entry.getValue(), outputDataResp);
				case RtspHeaderKey.RTPINFO -> processHeader_play_rtpinfo(input.messageType, entry.getValue(), outputDataResp);
				case RtspHeaderKey.SERVER -> processHeader_com_server(entry.getValue(), outputDataResp);
				case RtspHeaderKey.TRANSPORT -> processHeader_setup_transport(input.messageType, entry.getValue(), outputDataResp);
				case RtspHeaderKey.UNSUPPORTED -> processHeader_com_unsupported(entry.getValue(), outputDataResp);
				default -> {
					if (! preProcessedHeaders.contains(entry.getKey())) {
						logWarn(FNC_NAME, "Skipping header: " + entry.getKey());
					}
				}
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processHeader_com_auth_server(
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspInvalidResponseException {
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Auth(Server) header from client");
		}
		if (headerEntry.hdValAuthServer.authAlgo == RtspAuthAlgo.NONE) {
			throw new RtspInvalidResponseException("Auth(Server) Algo must be set");
		}
		outputDataResp.respAuthServer.setAuthRealm(headerEntry.hdValAuthServer.authRealm);
		outputDataResp.respAuthServer.setAuthNonce(headerEntry.hdValAuthServer.authNonce);
	}

	private void processHeader_com_connection(
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) {
		// @TODO store param
	}

	private void processHeader_describe_contbase(@NonNull RtspMessageType messageType) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_describe_contbase()";

		if (messageType != RtspMessageType.DESCRIBE) {
			logWarn(FNC_NAME, "Received Content-Base header in non-DESCRIBE response");
			return;
		}
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Content-Base header from client");
		}
	}

	private void processHeader_com_contenc(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contenc()";

		if (! allowOnlyDescribeGetParameter(FNC_NAME, "Content-Encoding", messageType)) {
			return;
		}
		if (headerEntry.hdValContEnc.contentEnc != RtspContentEncoding.NONE) {
			throw new RtspInvalidResponseException("No Content-Encoding other than NONE is supported");
		}
	}

	private void processHeader_com_contlang(@NonNull RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlang()";

		allowOnlyDescribeGetParameter(FNC_NAME, "Content-Language", messageType);
	}

	private void processHeader_com_contlen(@NonNull RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlen()";

		allowOnlyDescribeGetParameter(FNC_NAME, "Content-Length", messageType);
	}

	private void processHeader_com_conttype(@NonNull RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_conttype()";

		allowOnlyDescribeGetParameter(FNC_NAME, "Content-Type", messageType);
	}

	private void processHeader_com_date() {
		// nothing to do
	}

	private void processHeader_options_public(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_options_public()";

		if (messageType != RtspMessageType.OPTIONS) {
			logWarn(FNC_NAME, "Received Public header in non-OPTIONS response");
			return;
		}
		outputDataResp.respSuppMessageTypes.copyFrom(headerEntry.hdValPublic.messageTypes);
	}

	private void processHeader_play_range(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
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
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
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

	private void processHeader_com_server(
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspInvalidResponseException {
		if (isResponseFromClient) {
			throw new RtspInvalidResponseException("Received Server header from client");
		}
		// @TODO store params
	}

	private void processHeader_setup_transport(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
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

	private void processHeader_com_unsupported(
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_unsupported()";

		if (headerEntry.hdValUnsupported.unsupportedFeatureStr.isBlank()) {
			throw new RtspInvalidResponseException("unsupportedFeatureStr must be set");
		}
		logWarn(FNC_NAME, "Option '" + headerEntry.hdValUnsupported.unsupportedFeatureStr + "' is not supported");
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
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspInvalidResponseException {
		/*
		 * Example:
		 *   DESCRIBE:
		 *     "RTSP/1.0 200 OK"
		 *     "Content-Base: rtsp://example.com/fizzle/foo/"
		 *     "Content-Type: application/sdp"
		 *     "Content-Length: 1234"
		 *     ...
		 *     ""
		 *     "v=0"
		 *     "a=tool:TS RTSP Server/1.0"
		 *     ...
		 *   GET_PARAMETER:
		 *     "RTSP/1.0 200 OK"
		 *     "Content-Length: 1234"
		 *     "Content-Type: text/parameters"
		 *     ...
		 *     ""
		 *     "packets_received: 1234"
		 *     "jitter: 0.3838"
		 *   GET_PARAMETER/SET_PARAMETER:
		 *     "RTSP/1.0 451 Invalid Parameter"
		 *     "Content-Length: 1234"
		 *     "Content-Type: text/parameters"
		 *     ...
		 *     ""
		 *     "packets_received"
		 *     "jitter"
		 */

		// Content-Type
		boolean haveHdContTp = input.headers.containsKey(RtspHeaderKey.CONTENT_TYPE);
		if (! haveHdContTp) {
			if (input.messageType == RtspMessageType.DESCRIBE) {
				throw new RtspInvalidResponseException("Content-Type header is required for DESCRIBE message");
			}
			return;
		}
		RtspMimeType contentType = input.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType;
		// Content-Length
		boolean haveHdContLen = input.headers.containsKey(RtspHeaderKey.CONTENT_LEN);
		if (! haveHdContLen) {
			if (input.messageType == RtspMessageType.DESCRIBE) {
				throw new RtspInvalidResponseException("Content-Length header is required for DESCRIBE message");
			}
			return;
		}
		int contentLengthInt = input.headers.get(RtspHeaderKey.CONTENT_LEN).hdValContLen.getContentLen32bit().orElseThrow();
		long contentLengthLong = Integer.toUnsignedLong(contentLengthInt);
		if (contentLengthLong == 0L) {
			if (input.messageType == RtspMessageType.DESCRIBE) {
				throw new RtspInvalidResponseException("Body for DESCRIBE message missing");
			}
			return;
		}

		//
		switch (input.messageType) {
			case DESCRIBE:
				if (contentType != RtspMimeType.SDP) {
					throw new RtspInvalidResponseException("Content-Type for DESCRIBE message must be SDP");
				}
				//
				outputDataResp.respDescribeSdp.copyFrom(input.bodyDescribeSdp);
				// Content-Base
				if (! input.headers.containsKey(RtspHeaderKey.CONTENT_BASE)) {
					throw new RtspInvalidResponseException("Content-Base for DESCRIBE message missing");
				}
				outputDataResp.respDescribeSdp.setContentBase(
						input.headers.get(RtspHeaderKey.CONTENT_BASE).hdValContBase.contentBaseStr
					);
				break;
			case GET_PARAMETER, SET_PARAMETER:
				if (contentType != RtspMimeType.PARAMETERS) {
					throw new RtspInvalidResponseException("Content-Type for GET_PARAMETER message must be PARAMETERS");
				}
				if (! input.bodyGetSetInvalidParams.isParamNamesEmpty()) {
					outputDataResp.respInvalidParamNames.copyFrom(input.bodyGetSetInvalidParams);
				} else if (input.messageType == RtspMessageType.GET_PARAMETER) {
					outputDataResp.respGetParamValues.copyFrom(input.bodyGetParamKv);
				}
				break;
		}

		// Content-Language
		if ((input.messageType == RtspMessageType.DESCRIBE || input.messageType == RtspMessageType.GET_PARAMETER) &&
				input.headers.containsKey(RtspHeaderKey.CONTENT_LANG)) {
			String tmpContLang = input.headers.get(RtspHeaderKey.CONTENT_LANG).hdValContLang.contentLangStr;
			if (input.messageType == RtspMessageType.DESCRIBE) {
				outputDataResp.respDescribeSdp.setContentLang(tmpContLang);
			} else {
				outputDataResp.respGetParamValues.setContentLang(tmpContLang);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleBody(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspSdpException {
		switch (messageType) {
			case RtspMessageType.DESCRIBE:
				handleBody_describe(outputDataResp);
				break;
			case RtspMessageType.GET_PARAMETER, RtspMessageType.SET_PARAMETER:
				if (! outputDataResp.respInvalidParamNames.isParamNamesEmpty()) {
					handleBody_invalidParam(outputDataResp);
				} else if (messageType == RtspMessageType.GET_PARAMETER) {
					handleBody_getParam(outputDataResp);
				}
				break;
		}
	}

	private void handleBody_describe(@NonNull RtspProtoDataResponse outputDataResp) throws RtspSdpException {
		sdpConsumerInterface.parseSdpFromDescribe(outputDataResp.respDescribeSdp);
	}

	private void handleBody_invalidParam(@NonNull RtspProtoDataResponse outputDataResp) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleBody_invalidParam()";

		if (parameterNotifyInvalidInterface == null) {
			logWarn(FNC_NAME, "RtspProtoParameterNotifyInvalidInterface is not set");
			return;
		}
		parameterNotifyInvalidInterface.notifyInvalidRtspParameters(
				outputDataResp.respIdSession,
				outputDataResp.respInvalidParamNames
			);
	}

	private void handleBody_getParam(@NonNull RtspProtoDataResponse outputDataResp) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleBody_getParam()";

		if (outputDataResp.respGetParamValues.isParamKvsEmpty()) {
			return;
		}
		if (parameterNotifyRcvdInterface == null) {
			logWarn(FNC_NAME, "RtspProtoParameterNotifyRcvdInterface is not set");
			return;
		}
		parameterNotifyRcvdInterface.notifyReceivedRtspParameters(
				outputDataResp.respIdSession,
				outputDataResp.respGetParamValues
			);
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

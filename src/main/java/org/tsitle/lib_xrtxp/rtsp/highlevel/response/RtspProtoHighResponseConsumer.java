package org.tsitle.lib_xrtxp.rtsp.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterNotifyInvalidInterface;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoParameterNotifyRcvdInterface;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntCseqRespInp;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataResponse;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidResponseException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidSessionIdException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSdpException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpConsumerInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RtspProtoHighResponseConsumer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isResponseFromClient;
	private final @NonNull RtspProtoSdpConsumerInterface sdpConsumerInterface;
	private final @Nullable RtspProtoParameterNotifyInvalidInterface parameterNotifyInvalidInterface;
	private final @Nullable RtspProtoParameterNotifyRcvdInterface parameterNotifyRcvdInterface;

	private final Set<@NonNull RtspHeaderKey> preProcessedHeaders = new HashSet<>();

	public RtspProtoHighResponseConsumer(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isResponseFromClient,
				@NonNull RtspProtoSdpConsumerInterface sdpConsumerInterface,
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
		} catch (RtspProtoInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		// process body
		try {
			processBody(input, outputDataResp);
		} catch (RtspProtoInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		// handle body
		try {
			handleBody(input.messageType, outputDataResp);
		} catch (RtspProtoSdpException e) {
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
		} catch (RtspProtoInvalidResponseException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return false;
		} catch (RtspProtoInvalidSessionIdException e) {
			logWarn(fncName, "Invalid Session ID" + logMsgSuffix);
			return false;
		}
		return true;
	}

	private void checkRtspProtoVersion(@NonNull RtspProtoHighMsgStructuredResponse input) throws RtspProtoInvalidResponseException {
		if (input.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspProtoInvalidResponseException("Missing RTSP protocol version");
		}
	}

	private void checkCseq(
				@NonNull RtspProtoDataCntCseqRespInp cseqRespInp,
				@NonNull RtspProtoHighMsgStructuredResponse input
			) throws RtspProtoInvalidResponseException {
		Optional<Long> tmpOptCseq = input.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			throw new RtspProtoInvalidResponseException("Missing CSeq header");
		}
		long tmpExp = cseqRespInp.cseqNr_expected.getCseq32bit().orElse(-1L);
		if (! tmpOptCseq.get().equals(tmpExp)) {
			throw new RtspProtoInvalidResponseException("Invalid CSeq (is=" + tmpOptCseq.get() + ", exp=" + tmpExp + ")");
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.CSEQ);
	}

	private void checkSessionId(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException, RtspProtoInvalidSessionIdException {
		if (input.messageType != RtspProtoMessageType.SETUP) {
			return;
		}
		Optional<RtspProtoIdSession> tmpOptSessionId = input.getHeaderSessionId();
		if (tmpOptSessionId.isEmpty()) {
			throw new RtspProtoInvalidResponseException("Missing Session header");
		}
		RtspProtoIdSession tmpSessionId = tmpOptSessionId.get();

		if (tmpSessionId.isEmpty()) {
			throw new RtspProtoInvalidSessionIdException();
		}
		if (! currentIdSession.isEmpty() && ! tmpSessionId.equals(currentIdSession)) {
			throw new RtspProtoInvalidSessionIdException();
		}
		if (isResponseFromClient) {
			outputDataResp.rrIdSession.copyFrom(tmpSessionId);
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.SESSION);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processRemainingHeaders(
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException {
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
			) throws RtspProtoInvalidResponseException {
		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException("Received Auth(Server) header from client");
		}
		if (headerEntry.hdValAuthServer.authAlgo == RtspAuthAlgo.NONE) {
			throw new RtspProtoInvalidResponseException("Auth(Server) Algo must be set");
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

	private void processHeader_describe_contbase(@NonNull RtspProtoMessageType messageType) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_describe_contbase()";

		if (messageType != RtspProtoMessageType.DESCRIBE) {
			logWarn(FNC_NAME, "Received Content-Base header in non-DESCRIBE response");
			return;
		}
		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException("Received Content-Base header from client");
		}
	}

	private void processHeader_com_contenc(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contenc()";

		if (! allowOnlyDescribeGetParameter(FNC_NAME, "Content-Encoding", messageType)) {
			return;
		}
		if (headerEntry.hdValContEnc.contentEnc != RtspContentEncoding.NONE) {
			throw new RtspProtoInvalidResponseException("No Content-Encoding other than NONE is supported");
		}
	}

	private void processHeader_com_contlang(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlang()";

		allowOnlyDescribeGetParameter(FNC_NAME, "Content-Language", messageType);
	}

	private void processHeader_com_contlen(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlen()";

		allowOnlyDescribeGetParameter(FNC_NAME, "Content-Length", messageType);
	}

	private void processHeader_com_conttype(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_conttype()";

		allowOnlyDescribeGetParameter(FNC_NAME, "Content-Type", messageType);
	}

	private void processHeader_com_date() {
		// nothing to do
	}

	private void processHeader_options_public(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_options_public()";

		if (messageType != RtspProtoMessageType.OPTIONS) {
			logWarn(FNC_NAME, "Received Public header in non-OPTIONS response");
			return;
		}
		outputDataResp.respSuppMessageTypes.copyFrom(headerEntry.hdValPublic.messageTypes);
	}

	private void processHeader_play_range(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_play_range()";

		if (messageType != RtspProtoMessageType.PLAY) {
			logWarn(FNC_NAME, "Received Range header in non-PLAY response");
			return;
		}
		// @TODO store params
	}

	private void processHeader_play_rtpinfo(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_play_rtpinfo()";

		if (messageType != RtspProtoMessageType.PLAY) {
			logWarn(FNC_NAME, "Received RTP-Info header in non-PLAY response");
			return;
		}
		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException("Received RTP-Info header from client");
		}
		// @TODO store params
	}

	private void processHeader_com_server(
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException {
		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException("Received Server header from client");
		}
		// @TODO store params
	}

	private void processHeader_setup_transport(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_setup_transport()";

		if (messageType != RtspProtoMessageType.SETUP) {
			logWarn(FNC_NAME, "Received Transport header in non-SETUP response");
			return;
		}
		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException("Received Transport header from client");
		}
		// @TODO store params
	}

	private void processHeader_com_unsupported(
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_unsupported()";

		if (headerEntry.hdValUnsupported.unsupportedFeatureStr.isBlank()) {
			throw new RtspProtoInvalidResponseException("unsupportedFeatureStr must be set");
		}
		logWarn(FNC_NAME, "Option '" + headerEntry.hdValUnsupported.unsupportedFeatureStr + "' is not supported");
		// @TODO store params
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean allowOnlyDescribeGetParameter(
				@NonNull String fncName,
				@NonNull String hdDesc,
				@NonNull RtspProtoMessageType messageType
			) {
		if (messageType != RtspProtoMessageType.DESCRIBE && messageType != RtspProtoMessageType.GET_PARAMETER) {
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
			) throws RtspProtoInvalidResponseException {
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
			if (input.messageType == RtspProtoMessageType.DESCRIBE) {
				throw new RtspProtoInvalidResponseException("Content-Type header is required for DESCRIBE message");
			}
			return;
		}
		RtspMimeType contentType = input.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType;
		// Content-Length
		boolean haveHdContLen = input.headers.containsKey(RtspHeaderKey.CONTENT_LEN);
		if (! haveHdContLen) {
			if (input.messageType == RtspProtoMessageType.DESCRIBE) {
				throw new RtspProtoInvalidResponseException("Content-Length header is required for DESCRIBE message");
			}
			return;
		}
		long contentLengthLong = input.headers.get(RtspHeaderKey.CONTENT_LEN).hdValContLen.contentLen.getLen32bit().orElseThrow();
		if (contentLengthLong == 0L) {
			if (input.messageType == RtspProtoMessageType.DESCRIBE) {
				throw new RtspProtoInvalidResponseException("Body for DESCRIBE message missing");
			}
			return;
		}

		//
		switch (input.messageType) {
			case DESCRIBE:
				if (contentType != RtspMimeType.SDP) {
					throw new RtspProtoInvalidResponseException("Content-Type for DESCRIBE message must be SDP");
				}
				//
				outputDataResp.respDescribeSdpRaw.copyFrom(input.bodyDescribeSdp);
				// Content-Base
				if (! input.headers.containsKey(RtspHeaderKey.CONTENT_BASE)) {
					throw new RtspProtoInvalidResponseException("Content-Base for DESCRIBE message missing");
				}
				outputDataResp.respDescribeSdpRaw.setContentBase(
						input.headers.get(RtspHeaderKey.CONTENT_BASE).hdValContBase.contentBaseStr
					);
				break;
			case GET_PARAMETER, SET_PARAMETER:
				if (contentType != RtspMimeType.PARAMETERS) {
					throw new RtspProtoInvalidResponseException("Content-Type for GET_PARAMETER message must be PARAMETERS");
				}
				if (! input.bodyGetSetInvalidParams.isParamNamesEmpty()) {
					outputDataResp.rrInvalidParamNames.copyFrom(input.bodyGetSetInvalidParams);
				} else if (input.messageType == RtspProtoMessageType.GET_PARAMETER) {
					outputDataResp.respGetParamValues.copyFrom(input.bodyGetParamKv);
				}
				break;
		}

		// Content-Language
		if ((input.messageType == RtspProtoMessageType.DESCRIBE || input.messageType == RtspProtoMessageType.GET_PARAMETER) &&
				input.headers.containsKey(RtspHeaderKey.CONTENT_LANG)) {
			String tmpContLang = input.headers.get(RtspHeaderKey.CONTENT_LANG).hdValContLang.contentLangStr;
			if (input.messageType == RtspProtoMessageType.DESCRIBE) {
				outputDataResp.respDescribeSdpRaw.setContentLang(tmpContLang);
			} else {
				outputDataResp.respGetParamValues.setContentLang(tmpContLang);
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void handleBody(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoSdpException {
		switch (messageType) {
			case RtspProtoMessageType.DESCRIBE:
				handleBody_describe(outputDataResp);
				break;
			case RtspProtoMessageType.GET_PARAMETER, RtspProtoMessageType.SET_PARAMETER:
				if (! outputDataResp.rrInvalidParamNames.isParamNamesEmpty()) {
					handleBody_invalidParam(outputDataResp);
				} else if (messageType == RtspProtoMessageType.GET_PARAMETER) {
					handleBody_getParam(outputDataResp);
				}
				break;
		}
	}

	private void handleBody_describe(@NonNull RtspProtoDataResponse outputDataResp) throws RtspProtoSdpException {
		sdpConsumerInterface.parseSdpFromDescribe(outputDataResp.respDescribeSdpRaw, outputDataResp.respDescribeSdpStc);
	}

	private void handleBody_invalidParam(@NonNull RtspProtoDataResponse outputDataResp) {
		final String FNC_NAME = getClass().getSimpleName() + ".handleBody_invalidParam()";

		if (parameterNotifyInvalidInterface == null) {
			logWarn(FNC_NAME, "RtspProtoParameterNotifyInvalidInterface is not set");
			return;
		}
		parameterNotifyInvalidInterface.notifyInvalidRtspParameters(
				outputDataResp.rrIdSession,
				outputDataResp.rrInvalidParamNames
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
				outputDataResp.rrIdSession,
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

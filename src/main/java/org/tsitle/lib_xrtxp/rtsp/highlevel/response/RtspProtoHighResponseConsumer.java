package org.tsitle.lib_xrtxp.rtsp.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.exceptions.*;
import org.tsitle.lib_xrtxp.rtsp.highlevel.ResourceUrlProcessor;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderTypeRtpinfo;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSession;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntCseqRespInp;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspResponseBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.RtspProtoSdpConsumerInterface;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.*;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoIpAddr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRscUrl;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfoForSubStream;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoSetupInfosStream;

import java.util.*;

public class RtspProtoHighResponseConsumer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean isResponseFromClient;
	private final @NonNull RtspProtoSdpConsumerInterface sdpConsumerInterface;

	private final Set<@NonNull RtspHeaderKey> preProcessedHeaders = new HashSet<>();

	public RtspProtoHighResponseConsumer(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isResponseFromClient,
				@NonNull RtspProtoSdpConsumerInterface sdpConsumerInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.isResponseFromClient = isResponseFromClient;
		this.sdpConsumerInterface = sdpConsumerInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspResponseBasics processResponse(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRespInp inputCseqRespInp,
				@NonNull Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoHighMsgStructuredResponse inputMsgStc,
				@NonNull RtspProtoDataResponse ioDataResp
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".processResponse()";

		//System.out.println("<<<<<<<<< <<<<<<<<< " + inputMsgStc);

		preProcessedHeaders.clear();

		currentIdSession.writeProtect();

		//
		final String logMsgSuffix = " in response for " + inputMsgStc.messageType + " request";

		//
		boolean tmpPreflight = preflightChecks(
				FNC_NAME,
				logMsgSuffix,
				currentIdSession,
				inputCseqRespInp,
				inputMsgStc,
				ioDataResp
			);
		if (! tmpPreflight) {
			return RtspResponseBasics.createInternalServerError();
		}

		// process headers that haven't been processed yet
		try {
			processRemainingHeaders(inputMsgStc, inpAvailableSubStreamIds, ioSetupInfosStream, ioDataResp);
		} catch (RtspProtoInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		// process body
		try {
			processBody(inputMsgStc, ioDataResp);
		} catch (RtspProtoInvalidResponseException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		// handle body
		try {
			handleBody(inputMsgStc.messageType, ioDataResp);
		} catch (RtspProtoSdpException e) {
			logWarn(FNC_NAME, e.getMessage() + logMsgSuffix);
			return RtspResponseBasics.createInternalServerError();
		}

		//
		return RtspResponseBasics.createDefault(inputMsgStc.statusCode);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean preflightChecks(
				@NonNull String fncName,
				@NonNull String logMsgSuffix,
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoDataCntCseqRespInp inputCseqRespInp,
				@NonNull RtspProtoHighMsgStructuredResponse inputMsgStc,
				@NonNull RtspProtoDataResponse outputDataResp
			) {
		try {
			checkRtspProtoVersion(inputMsgStc);
			checkCseq(inputCseqRespInp, inputMsgStc);
			checkSessionId(currentIdSession, inputMsgStc, outputDataResp);
		} catch (RtspProtoInvalidResponseException e) {
			logWarn(fncName, e.getMessage() + logMsgSuffix);
			return false;
		} catch (RtspProtoInvalidSessionIdException e) {
			logWarn(fncName, "Invalid Session ID" + logMsgSuffix);
			return false;
		}
		return true;
	}

	private void checkRtspProtoVersion(@NonNull RtspProtoHighMsgStructuredResponse inputMsgStc)
			throws RtspProtoInvalidResponseException {
		if (inputMsgStc.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspProtoInvalidResponseException("Missing RTSP protocol version");
		}
	}

	private void checkCseq(
				@NonNull RtspProtoDataCntCseqRespInp inputCseqRespInp,
				@NonNull RtspProtoHighMsgStructuredResponse inputMsgStc
			) throws RtspProtoInvalidResponseException {
		Optional<Long> tmpOptCseq = inputMsgStc.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			throw new RtspProtoInvalidResponseException("Missing CSeq header");
		}
		long tmpExp = inputCseqRespInp.cseqNr_expected.getCseq32bit().orElse(-1L);
		if (! tmpOptCseq.get().equals(tmpExp)) {
			throw new RtspProtoInvalidResponseException("Invalid CSeq (is=" + tmpOptCseq.get() + ", exp=" + tmpExp + ")");
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.CSEQ);
	}

	private void checkSessionId(
				@NonNull RtspProtoIdSession currentIdSession,
				@NonNull RtspProtoHighMsgStructuredResponse inputMsgStc,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException, RtspProtoInvalidSessionIdException {
		Optional<RtspProtoIdSession> tmpOptSessionId = inputMsgStc.getHeaderSessionId();
		if (! currentIdSession.isEmpty() && tmpOptSessionId.isEmpty()) {
			throw new RtspProtoInvalidResponseException("Missing Session header");
		}
		if (tmpOptSessionId.isPresent()) {
			RtspProtoIdSession tmpSessionId = tmpOptSessionId.get();

			if (tmpSessionId.isEmpty()) {
				throw new RtspProtoInvalidSessionIdException();
			}
			if (! currentIdSession.isEmpty() && ! tmpSessionId.equals(currentIdSession)) {
				throw new RtspProtoInvalidSessionIdException();
			}
		}
		if (isResponseFromClient || outputDataResp.rrIdSession.isEmpty()) {
			if (currentIdSession.isEmpty() && tmpOptSessionId.isPresent()) {
				outputDataResp.rrIdSession.copyFrom(tmpOptSessionId.get());
			} else {
				outputDataResp.rrIdSession.copyFrom(currentIdSession);
			}
		}

		//
		preProcessedHeaders.add(RtspHeaderKey.SESSION);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processRemainingHeaders(
				@NonNull RtspProtoHighMsgStructuredResponse inputMsgStc,
				@NonNull Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataResponse ioDataResp
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processRemainingHeaders()";

		for (Map.Entry<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryResponse> entry : inputMsgStc.headers.entrySet()) {
			switch (entry.getKey()) {
				case RtspHeaderKey.AUTH_SERVER -> processHeader_com_auth_server(entry.getValue(), ioDataResp);
				case RtspHeaderKey.CONNECTION -> processHeader_com_connection();
				case RtspHeaderKey.CONTENT_BASE -> processHeader_describe_contbase(inputMsgStc.messageType);
				case RtspHeaderKey.CONTENT_ENC -> processHeader_com_contenc(inputMsgStc.messageType, entry.getValue());
				case RtspHeaderKey.CONTENT_LANG -> processHeader_com_contlang(inputMsgStc.messageType);
				case RtspHeaderKey.CONTENT_LEN -> processHeader_com_contlen(inputMsgStc.messageType);
				case RtspHeaderKey.CONTENT_TYPE -> processHeader_com_conttype(inputMsgStc.messageType);
				case RtspHeaderKey.DATE -> processHeader_com_date();
				case RtspHeaderKey.PUBLIC -> processHeader_options_public(inputMsgStc.messageType, entry.getValue(), ioDataResp);
				case RtspHeaderKey.RANGE -> processHeader_play_range(inputMsgStc.messageType, entry.getValue(), ioDataResp);
				case RtspHeaderKey.RTPINFO ->
						processHeader_play_rtpinfo(
								inputMsgStc.messageType,
								entry.getValue(),
								inpAvailableSubStreamIds,
								ioSetupInfosStream
							);
				case RtspHeaderKey.SERVER -> processHeader_com_server(entry.getValue(), ioDataResp);
				case RtspHeaderKey.TRANSPORT -> processHeader_setup_transport(inputMsgStc.messageType, entry.getValue(), ioDataResp);
				case RtspHeaderKey.UNSUPPORTED -> processHeader_com_unsupported(entry.getValue(), ioDataResp);
				case RtspHeaderKey.USERAGENT -> processHeader_com_useragent(entry.getValue(), ioDataResp);
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

	private void processHeader_com_connection() {
		// nothing to do
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

		if (! allowOnlyDescribeGetSetParameter(FNC_NAME, "Content-Encoding", messageType)) {
			return;
		}
		if (headerEntry.hdValContEnc.contentEnc != RtspContentEncoding.NONE) {
			throw new RtspProtoInvalidResponseException("No Content-Encoding other than NONE is supported");
		}
	}

	private void processHeader_com_contlang(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlang()";

		allowOnlyDescribeGetSetParameter(FNC_NAME, "Content-Language", messageType);
	}

	private void processHeader_com_contlen(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_contlen()";

		allowOnlyDescribeGetSetParameter(FNC_NAME, "Content-Length", messageType);
	}

	private void processHeader_com_conttype(@NonNull RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_com_conttype()";

		allowOnlyDescribeGetSetParameter(FNC_NAME, "Content-Type", messageType);
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
		outputDataResp.setPlaybackRangeValue(headerEntry.hdValRange.rangeStr);
	}

	private void processHeader_play_rtpinfo(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull Set<@NonNull RtspProtoIdSubStream> inpAvailableSubStreamIds,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_play_rtpinfo()";

		if (messageType != RtspProtoMessageType.PLAY) {
			logWarn(FNC_NAME, "Received RTP-Info header in non-PLAY response");
			return;
		}
		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException("Received RTP-Info header from client");
		}

		List<RtspProtoHeaderTypeRtpinfo.SubStream> tmpInputRtpInfoSsList = new ArrayList<>();
		Optional<RtspProtoHeaderTypeRtpinfo.SubStream> tmpOptRtpInfoSs = headerEntry.hdValRtpinfo.getSubStream1();
		if (tmpOptRtpInfoSs.isEmpty()) {
			logWarn(FNC_NAME, "Received RTP-Info header without Sub-Stream info");
			return;
		}
		tmpInputRtpInfoSsList.add(tmpOptRtpInfoSs.get());
		headerEntry.hdValRtpinfo.getSubStream2().ifPresent(tmpInputRtpInfoSsList::add);

		// store RTP SeqNr + RTP Timestamp per Sub-Stream
		for (RtspProtoHeaderTypeRtpinfo.SubStream tmpRtpInfoSs : tmpInputRtpInfoSsList) {
			RtspProtoRscUrl tmpRscUrl;
			try {
				tmpRscUrl = ResourceUrlProcessor.parseUrlIntoRscUrlObject(
						null,
						null,
						tmpRtpInfoSs.urlStr,
						RtspProtoIpAddr.ofLoopback(),
						inpAvailableSubStreamIds
					);
			} catch (RtspProtoInvalidUriException e) {
				logWarn(FNC_NAME, "Invalid URI in RTP-Info Sub-Stream info: " + e.getMessage());
				continue;
			} catch (RtspProtoIdInputSourceNotFoundException e) {
				logWarn(FNC_NAME, "Input Source ID in RTP-Info Sub-Stream info not found: " + e.getMessage());
				continue;
			} catch (RtspProtoIdSubStreamNotFoundException e) {
				logWarn(FNC_NAME, "Sub-Stream ID in RTP-Info Sub-Stream info not found: " + e.getMessage());
				continue;
			}
			if (tmpRscUrl.idSubStream.isEmpty()) {
				logWarn(FNC_NAME, "Sub-Stream ID in RTP-Info Sub-Stream info is empty");
				continue;
			}

			Optional<RtspProtoSetupInfoForSubStream> tmpOptSiForSs = ioSetupInfosStream.getSiPtrBySubStreamId(tmpRscUrl.idSubStream);
			if (tmpOptSiForSs.isEmpty()) {
				logWarn(FNC_NAME, "Received RTP-Info Sub-Stream info for unknown Sub-Stream ID: '" +
						tmpRscUrl.idSubStream.getIdStr().orElse("-unset-") + "'");
				continue;
			}
			RtspProtoSetupInfoForSubStream tmpInputSiForSs = tmpOptSiForSs.get();
			RtspProtoSetupInfoForSubStream tmpOutputSiForSs = new RtspProtoSetupInfoForSubStream(tmpInputSiForSs);

			tmpOutputSiForSs.getRtpSeqNrT0Ptr().copyFrom(tmpRtpInfoSs.seqNr);
			tmpOutputSiForSs.getRtpTimestampT0Ptr().copyFrom(tmpRtpInfoSs.rtpTimestamp);

			ioSetupInfosStream.replaceSiForSubStream(tmpRscUrl.idSubStream, tmpOutputSiForSs);
		}
	}

	private void processHeader_com_server(
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException {
		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException("Received Server header from client");
		}
		outputDataResp.setServerSoftware(headerEntry.hdValServer.serverStr);
	}

	private void processHeader_setup_transport(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse ioDataResp
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".processHeader_setup_transport()";

		if (messageType != RtspProtoMessageType.SETUP) {
			logWarn(FNC_NAME, "Received Transport header in non-SETUP response");
			return;
		}
		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException("Received Transport header from client");
		}

		if (headerEntry.hdValTransport.tpSubStream.getIsUdp() != ioDataResp.respSetupSubStreamTp.getIsUdp()) {
			throw new RtspProtoInvalidResponseException("Received invalid Transport type (is=" +
					(headerEntry.hdValTransport.tpSubStream.getIsUdp() ? "UDP" : "TCP") +
					", exp=" +
					(ioDataResp.respSetupSubStreamTp.getIsUdp() ? "UDP" : "TCP") +
					")");
		}
		if (headerEntry.hdValTransport.tpSubStream.getIsEncr() != ioDataResp.respSetupSubStreamTp.getIsEncr()) {
			throw new RtspProtoInvalidResponseException("Received invalid Transport encryption mode (is=" +
					(headerEntry.hdValTransport.tpSubStream.getIsEncr() ? "Encrypted" : "Unencrypted") +
					", exp=" +
					(ioDataResp.respSetupSubStreamTp.getIsEncr() ? "Encrypted" : "Unencrypted") +
					")");
		}

		if (headerEntry.hdValTransport.tpSubStream.getIsUdp()) {
			if (headerEntry.hdValTransport.tpSubStream.getServerUdpPortRtpPtr().isEmpty()) {
				throw new RtspProtoInvalidResponseException("Missing Server UDP RTP Port in Transport header");
			}
			if (headerEntry.hdValTransport.tpSubStream.getServerUdpPortRtcpPtr().isEmpty()) {
				throw new RtspProtoInvalidResponseException("Missing Server UDP RTCP Port in Transport header");
			}
			ioDataResp.respSetupSubStreamTp.getServerUdpPortRtpPtr()
					.copyFrom(headerEntry.hdValTransport.tpSubStream.getServerUdpPortRtpPtr());
			ioDataResp.respSetupSubStreamTp.getServerUdpPortRtcpPtr()
					.copyFrom(headerEntry.hdValTransport.tpSubStream.getServerUdpPortRtcpPtr());
		}

		if (headerEntry.hdValTransport.tpSsrcId.isEmpty()) {
			throw new RtspProtoInvalidResponseException("Missing SSRC in Transport header");
		}
		ioDataResp.respSetupSubStreamSsrc.copyFrom(headerEntry.hdValTransport.tpSsrcId);
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
		outputDataResp.setUnsupportedFeatureName(headerEntry.hdValUnsupported.unsupportedFeatureStr);
	}

	private void processHeader_com_useragent(
				@NonNull RtspProtoHeaderEntryResponse headerEntry,
				@NonNull RtspProtoDataResponse outputDataResp
			) throws RtspProtoInvalidResponseException {
		if (headerEntry.hdValUserAgent.userAgentStr.isBlank()) {
			throw new RtspProtoInvalidResponseException("userAgentStr must be set");
		}
		outputDataResp.setClientUa(headerEntry.hdValUserAgent.userAgentStr);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private boolean allowOnlyDescribeGetSetParameter(
				@NonNull String fncName,
				@NonNull String hdDesc,
				@NonNull RtspProtoMessageType messageType
			) {
		if (messageType != RtspProtoMessageType.DESCRIBE &&
				messageType != RtspProtoMessageType.GET_PARAMETER && messageType != RtspProtoMessageType.SET_PARAMETER) {
			logWarn(fncName, hdDesc + " header is only valid for " +
					"DESCRIBE/GET_PARAMETER/SET_PARAMETER responses");
			return false;
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void processBody(
				@NonNull RtspProtoHighMsgStructuredResponse inputMsgStc,
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
		boolean haveHdContTp = inputMsgStc.headers.containsKey(RtspHeaderKey.CONTENT_TYPE);
		if (! haveHdContTp) {
			if (inputMsgStc.messageType == RtspProtoMessageType.DESCRIBE) {
				throw new RtspProtoInvalidResponseException("Content-Type header is required for DESCRIBE message");
			}
			return;
		}
		RtspMimeType contentType = inputMsgStc.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType;
		// Content-Length
		boolean haveHdContLen = inputMsgStc.headers.containsKey(RtspHeaderKey.CONTENT_LEN);
		if (! haveHdContLen) {
			if (inputMsgStc.messageType == RtspProtoMessageType.DESCRIBE) {
				throw new RtspProtoInvalidResponseException("Content-Length header is required for DESCRIBE message");
			}
			return;
		}
		long contentLengthLong = inputMsgStc.headers.get(RtspHeaderKey.CONTENT_LEN).hdValContLen.contentLen.getLen32bit().orElseThrow();
		if (contentLengthLong == 0L) {
			if (inputMsgStc.messageType == RtspProtoMessageType.DESCRIBE) {
				throw new RtspProtoInvalidResponseException("Body for DESCRIBE message missing");
			}
			return;
		}

		//
		switch (inputMsgStc.messageType) {
			case DESCRIBE:
				if (contentType != RtspMimeType.SDP) {
					throw new RtspProtoInvalidResponseException("Content-Type for DESCRIBE message must be SDP");
				}
				//
				outputDataResp.respDescribeSdpRaw.copyFrom(inputMsgStc.bodyDescribeSdp);
				// Content-Base
				if (! inputMsgStc.headers.containsKey(RtspHeaderKey.CONTENT_BASE)) {
					throw new RtspProtoInvalidResponseException("Content-Base for DESCRIBE message missing");
				}
				outputDataResp.respDescribeSdpRaw.setContentBase(
						inputMsgStc.headers.get(RtspHeaderKey.CONTENT_BASE).hdValContBase.contentBaseStr
					);
				break;
			case GET_PARAMETER, SET_PARAMETER:
				if (contentType != RtspMimeType.PARAMETERS) {
					throw new RtspProtoInvalidResponseException("Content-Type for GET_PARAMETER message must be PARAMETERS");
				}
				if (! inputMsgStc.bodyGetSetInvalidParams.isParamNamesEmpty()) {
					outputDataResp.rrInvalidParamNames.copyFrom(inputMsgStc.bodyGetSetInvalidParams);
				} else if (inputMsgStc.messageType == RtspProtoMessageType.GET_PARAMETER) {
					outputDataResp.respGetParamValues.copyFrom(inputMsgStc.bodyGetParamKv);
				}
				break;
		}

		// Content-Language
		if ((inputMsgStc.messageType == RtspProtoMessageType.DESCRIBE || inputMsgStc.messageType == RtspProtoMessageType.GET_PARAMETER) &&
				inputMsgStc.headers.containsKey(RtspHeaderKey.CONTENT_LANG)) {
			String tmpContLang = inputMsgStc.headers.get(RtspHeaderKey.CONTENT_LANG).hdValContLang.contentLangStr;
			if (inputMsgStc.messageType == RtspProtoMessageType.DESCRIBE) {
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
					handleBody_invalidParam();
				} else if (messageType == RtspProtoMessageType.GET_PARAMETER) {
					handleBody_getParam();
				}
				break;
		}
	}

	private void handleBody_describe(@NonNull RtspProtoDataResponse outputDataResp) throws RtspProtoSdpException {
		sdpConsumerInterface.parseSdpFromDescribe(outputDataResp.respDescribeSdpRaw, outputDataResp.respDescribeSdpStc);
	}

	private void handleBody_invalidParam() {
		// nothing to do
	}

	private void handleBody_getParam() {
		// nothing to do
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

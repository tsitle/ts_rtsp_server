package org.tsitle.lib_xrtxp.rtsp.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.common.exceptions.UdpSocketIoException;
import org.tsitle.lib_xrtxp.common.helpers.RandomHelper;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntSubStreamTp;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.exceptions.*;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighUdpPorts;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdSubStream;
import org.tsitle.lib_xrtxp.rtsp.interfaces.*;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspProtoHighConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.RtspRequestBasics;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderTypeRtpinfo;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspAuthAlgo;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspHeaderKey;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspMimeType;
import org.tsitle.lib_xrtxp.rtsp.misctypes.*;

import java.net.*;
import java.util.*;

public final class RtspProtoHighResponseProducer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull String cfgSenderAppNameAndVersion;
	private final @NonNull String cfgSubStreamIdPrefix;
	private final boolean cfgIsDebugPrintRtspSdpSent;
	private final boolean cfgIsDebugDisableTransportUdp;
	private final @Nullable RtspProtoSdpProducerInterface sdpProducerInterface;
	private final @Nullable RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface;
	private final @Nullable RtspProtoParameterGetterInterface parameterGetterInterface;
	private final boolean isResponseFromClient;

	public RtspProtoHighResponseProducer(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isResponseFromClient,
				@NonNull String cfgSenderAppNameAndVersion,
				@NonNull String cfgSubStreamIdPrefix,
				boolean cfgIsDebugPrintRtspSdpSent,
				boolean cfgIsDebugDisableTransportUdp,
				@Nullable RtspProtoSdpProducerInterface sdpProducerInterface,
				@Nullable RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@Nullable RtspProtoGlobalSessionInfoInterface globalSessionInfoInterface,
				@Nullable RtspProtoParameterGetterInterface parameterGetterInterface
			) {
		if (cfgSenderAppNameAndVersion.isBlank()) {
			throw new IllegalArgumentException("cfgSenderAppNameAndVersion cannot be blank");
		}
		if (! isResponseFromClient && cfgSubStreamIdPrefix.isBlank()) {
			throw new IllegalArgumentException("cfgSubStreamIdPrefix cannot be blank for responses from the server");
		}
		if (! isResponseFromClient && sdpProducerInterface == null) {
			throw new IllegalArgumentException("sdpProducerInterface cannot be null for responses from the server");
		}
		if (! isResponseFromClient && availableStreamsInterface == null) {
			throw new IllegalArgumentException("availableStreamsInterface cannot be null for responses from the server");
		}
		if (! isResponseFromClient && globalSessionInfoInterface == null) {
			throw new IllegalArgumentException("globalSessionInfoInterface cannot be null for responses from the server");
		}

		this.logMsgInterface = logMsgInterface;
		this.cfgSenderAppNameAndVersion = cfgSenderAppNameAndVersion;
		this.cfgSubStreamIdPrefix = cfgSubStreamIdPrefix;
		this.cfgIsDebugPrintRtspSdpSent = cfgIsDebugPrintRtspSdpSent;
		this.cfgIsDebugDisableTransportUdp = cfgIsDebugDisableTransportUdp;
		this.sdpProducerInterface = sdpProducerInterface;
		this.availableStreamsInterface = availableStreamsInterface;
		this.globalSessionInfoInterface = globalSessionInfoInterface;
		this.parameterGetterInterface = parameterGetterInterface;
		this.isResponseFromClient = isResponseFromClient;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredResponse buildResponse(
				@NonNull RtspRequestBasics rtspRequestBasics,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataResponse ioDataResp
			) throws RtspProtoInvalidResponseException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse()";

		if (rtspRequestBasics.statusCode == RtspProtoStatusCode.OK && ioDataResp.rrRscUrl.isEmpty()) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Resource URL must be set");
		}

		//
		RtspProtoHighMsgStructuredResponse resObj = new RtspProtoHighMsgStructuredResponse();

		resObj.rtspProtoVersion = ioDataResp.getRtspProtoVersionToUse();
		resObj.statusCode = rtspRequestBasics.statusCode;
		resObj.messageType = rtspRequestBasics.messageType;

		//
		addCommonHeaders(ioDataResp, resObj);

		//
		if (rtspRequestBasics.statusCode != RtspProtoStatusCode.OK) {
			buildResponse_nack(rtspRequestBasics.statusCode, ioDataResp, resObj);
			return resObj;
		}

		//
		switch (rtspRequestBasics.messageType) {
			case ANNOUNCE, PAUSE, REDIRECT, SET_PARAMETER, TEARDOWN -> buildResponse_ack();
			case DESCRIBE -> buildResponse_describe(ioDataResp, ioSetupInfosStream, resObj);
			case GET_PARAMETER -> buildResponse_getParameter(ioDataResp, resObj);
			case OPTIONS -> buildResponse_options(ioDataResp, resObj);
			case PLAY -> buildResponse_play(ioDataResp, ioSetupInfosStream, resObj);
			case SETUP -> buildResponse_setup(ioDataResp, ioSetupInfosStream, rtspRequestBasics.rscUrl, resObj);
			default -> throw new RtspProtoInvalidResponseException(FNC_NAME + ": Unsupported message type: " +
					rtspRequestBasics.messageType);
		}

		//System.out.println(">>>>>>>>> >>>>>>>>> " + resObj);

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildResponse_ack() {
		/*
		 * Example:
		 *   "RTSP/1.0 200 OK"
		 *   "CSeq: 312"
		 *   ...
		 */
		// nothing to do
	}

	private void buildResponse_nack(
				@NonNull RtspProtoStatusCode statusCode,
				@NonNull RtspProtoDataResponse ioDataResp,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspProtoInvalidResponseException {
		/*
		 * Example:
		 *   "RTSP/1.0 500 Something went wrong"
		 *   "CSeq: 312"
		 *   ...
		 *   plus optionally:
		 *   "WWW-Authenticate: Digest realm=\"Abcdef Some\", nonce=\"xxx\", algorithm=\"MD5\""
		 *   "Unsupported: some_feature"
		 */
		switch (statusCode) {
			case INVALID_PARAMETER:  // from SET_PARAMETER (not GET_PARAMETER)
				output.bodyGetSetInvalidParams.copyFrom(ioDataResp.rrInvalidParamNames);
				// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
				addContentTypeHeader(output);
				break;
			case RtspProtoStatusCode.OPTION_NOT_SUPPORTED:
				// Unsupported
				{
					RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.UNSUPPORTED);
					hdEntry.hdValUnsupported.unsupportedFeatureStr = (ioDataResp.getUnsupportedFeatureName().isBlank()
							? "_unknown_" : ioDataResp.getUnsupportedFeatureName());
					output.headers.put(hdEntry.getHdKey(), hdEntry);
				}
				break;
			case RtspProtoStatusCode.UNAUTHORIZED:
				addAuthServerHeader(ioDataResp, output);
				break;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildResponse_describe(
				@NonNull RtspProtoDataResponse inputDataResp,
				@NonNull RtspProtoSetupInfosStream outputSetupInfosStream,
				@NonNull RtspProtoHighMsgStructuredResponse outputMsgResp
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_describe()";

		/*
		 * Example:
		 *   "RTSP/1.0 200 OK"
		 *   "Content-Base: rtsp://example.com/fizzle/foo/"
		 *   "Content-Type: application/sdp"
		 *   "Content-Length: 1234"
		 *   ...
		 *   ""
		 *   "v=0"
		 *   "a=tool:TS RTSP Server/1.0"
		 *   ...
		 */

		if (sdpProducerInterface == null) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": SDP producer must be set");
		}
		//
		if (inputDataResp.rrRscUrl.getUrlStr().isEmpty()) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Resource URL must be set");
		}
		if (inputDataResp.rrRscUrl.idInputSource.isEmpty()) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Input Source ID must be set");
		}
		final String baseRscUrl = inputDataResp.rrRscUrl.getUrlStr() +
				(inputDataResp.rrRscUrl.getUrlStr().endsWith("/") ? "" : "/");

		//
		try {
			RtspProtoAdSettingsStream outputAdStreamSett = new RtspProtoAdSettingsStream();
			RtspProtoKmdsStream outputKmdsStreamOutbound = new RtspProtoKmdsStream();

			// build SDP
			sdpProducerInterface.buildSdpForDescribe(
					cfgSubStreamIdPrefix,
					inputDataResp.rrStreamTpMain.isSrtpRequired(),
					inputDataResp.rrRscUrl.idInputSource,
					inputDataResp.rrServerIpFromRscUrl,
					inputDataResp.getClientUa(),
					inputDataResp.rrClientIpAddr,
					outputMsgResp.bodyDescribeSdp,
					outputAdStreamSett,
					outputKmdsStreamOutbound
				);

			// copy the stream settings and KMDs
			for (RtspProtoIdSubStream tmpIdSs : outputAdStreamSett.getSubStreamIds()) {
				RtspProtoAdSettingsForSubStream tmpAvSs = outputAdStreamSett.getSettingsBySubStreamId(tmpIdSs)
						.orElseThrow();

				RtspProtoKmdForSubStream tmpKmdForSsOutbound = new RtspProtoKmdForSubStream();
				if (outputKmdsStreamOutbound.containsKmdForSubStreamId(tmpIdSs)) {
					SrtxpKmd tmpSrtxpKmdOutbound = outputKmdsStreamOutbound.getKmdBySubStreamId(tmpIdSs).orElseThrow();
					tmpKmdForSsOutbound.setKmd(tmpSrtxpKmdOutbound, tmpIdSs);
				}

				RtspProtoRscUrl tmpRscUrlSs = new RtspProtoRscUrl();
				tmpRscUrlSs.setUrlStr(baseRscUrl + tmpAvSs.getUrlSubPathForSubStream());
				tmpRscUrlSs.idInputSource.copyFrom(inputDataResp.rrRscUrl.idInputSource);
				tmpRscUrlSs.idSubStream.copyFrom(tmpIdSs);

				outputSetupInfosStream.createAndAddSetupSubStream(
						tmpRscUrlSs,
						tmpAvSs.ssrcOutbound,
						tmpKmdForSsOutbound
					);
			}
		} catch (RtspProtoSdpException e) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Building SDP failed: " + e.getMessage());
		}

		if (cfgIsDebugPrintRtspSdpSent) {
			logDebug(FNC_NAME, "-------- SDP:");
			for (String tmpSingleSdpLine : outputMsgResp.bodyDescribeSdp.getSdpLinesAllRaw()) {
				logDebug(FNC_NAME, "---------------- " + tmpSingleSdpLine);
			}
		}

		// Content-Base
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.CONTENT_BASE);
			hdEntry.hdValContBase.contentBaseStr = baseRscUrl;
			outputMsgResp.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
		addContentTypeHeader(outputMsgResp);
		// Content-Language
		addContentLangHeader(outputMsgResp.bodyDescribeSdp.getContentLang(), outputMsgResp);
	}

	private void buildResponse_getParameter(
				@NonNull RtspProtoDataResponse inputDataResp,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspProtoInvalidResponseException {
		/*
		 * Example:
		 *   Success:
		 *     "RTSP/1.0 200 OK"
		 *     "Content-Length: 1234"
		 *     "Content-Type: text/parameters"
		 *     ...
		 *     ""
		 *     "packets_received: 10"
		 *     "jitter: 0.3838"
		 *   Failure:
		 *     "RTSP/1.0 451 Invalid Parameter"
		 *     "Content-Length: 1234"
		 *     "Content-Type: text/parameters"
		 *     ...
		 *     ""
		 *     "barparam"
		 */

		if (inputDataResp.rrGetParamNames.isParamNamesEmpty()) {
			// nothing to do
			return;
		}

		if (parameterGetterInterface == null) {
			output.statusCode = RtspProtoStatusCode.INVALID_PARAMETER;
			output.bodyGetSetInvalidParams.copyFrom(inputDataResp.rrGetParamNames);
		} else {
			RtspProtoDataCntGetSetParamKvs tmpDataGsp =
					parameterGetterInterface.getAllRtspParameters(inputDataResp.rrIdSession);

			Set<String> tmpMissingParams = new HashSet<>();
			for (String requParam : inputDataResp.rrGetParamNames.getParamNames()) {
				Optional<String> tmpRequParVal = tmpDataGsp.getParamKvsValue(requParam);
				if (tmpRequParVal.isEmpty()) {
					tmpMissingParams.add(requParam);
				} else {
					output.bodyGetParamKv.putParamKvsEntry(requParam, tmpRequParVal.get());
				}
			}

			output.bodyGetParamKv.setContentLang(tmpDataGsp.getContentLang());

			if (! tmpMissingParams.isEmpty()) {
				output.statusCode = RtspProtoStatusCode.INVALID_PARAMETER;
				output.bodyGetParamKv.clear();
				output.bodyGetSetInvalidParams.putAllParamNames(tmpMissingParams);
			}
		}

		// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
		addContentTypeHeader(output);
		// Content-Language
		addContentLangHeader(output.bodyGetParamKv.getContentLang(), output);
	}

	private void buildResponse_options(
				@NonNull RtspProtoDataResponse ioDataResp,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_options";

		/*
		 * Example:
		 *   "RTSP/1.0 200 OK"
		 *   "Date: Sat, 6 Jun 2026 18:27:45 GMT"
		 *   "CSeq: 2"
		 *   ...
		 *   "Public: PLAY, OPTIONS, SET_PARAMETER, PAUSE, TEARDOWN, GET_PARAMETER, SETUP, DESCRIBE"
		 */

		// Public
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.PUBLIC);
			hdEntry.hdValPublic.messageTypes.copyFrom(ioDataResp.respSuppMessageTypes);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Auth
		if (availableStreamsInterface != null && ! ioDataResp.rrRscUrl.idInputSource.isEmpty()) {
			boolean tmpNeedAuth;
			try {
				tmpNeedAuth = availableStreamsInterface.getInputSourceObj(ioDataResp.rrRscUrl.idInputSource)
						.getNeedsAuthentication();
			} catch (RtspProtoIdInputSourceNotFoundException e) {
				throw new RtspProtoInvalidResponseException(FNC_NAME + ": " + e.getMessage());
			}
			if (tmpNeedAuth) {
				addAuthServerHeader(ioDataResp, output);
			}
		}
	}

	/**
	 * The client makes one PLAY request per Input Source
	 */
	private void buildResponse_play(
				@NonNull RtspProtoDataResponse inputDataResp,
				@NonNull RtspProtoSetupInfosStream inputSetupInfosStream,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_play()";

		/*
		 * Example:
		 *   "RTSP/1.0 200 OK"
		 *   "Range: npt=0.000-"
		 *   "RTP-Info: url=rtsp://...;seq=2786;rtptime=1380770927,url=...;seq=22526;rtptime=257277163"
		 *   ...
		 */

		// Range
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.RANGE);
			hdEntry.hdValRange.rangeStr = inputDataResp.getPlaybackRangeValue();
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// RTP-Info
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.RTPINFO);
			int tmpSubStreamNr = 1;
			for (RtspProtoSetupInfoForSubStream tmpSiSs : inputSetupInfosStream.getSis()) {
				RtspProtoHeaderTypeRtpinfo.SubStream tmpStreamInfoOutput = new RtspProtoHeaderTypeRtpinfo.SubStream();
				tmpStreamInfoOutput.urlStr = tmpSiSs.getRscUrlSubStreamPtr().getUrlStr();
				tmpStreamInfoOutput.seqNr.copyFrom(tmpSiSs.getRtpSeqNrT0Ptr());
				tmpStreamInfoOutput.rtpTimestamp.copyFrom(tmpSiSs.getRtpTimestampT0Ptr());
				tmpStreamInfoOutput.ssrcId.copyFrom(tmpSiSs.getSsrcOutboundPtr());
				if (tmpSubStreamNr == 1) {
					hdEntry.hdValRtpinfo.setSubStream1(tmpStreamInfoOutput);
				} else if (tmpSubStreamNr == 2) {
					hdEntry.hdValRtpinfo.setSubStream2(tmpStreamInfoOutput);
				} else {
					throw new RtspProtoInvalidResponseException(FNC_NAME + ": Too many sub-streams");
				}
				++tmpSubStreamNr;
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	/**
	 * The client makes one SETUP request per Stream Source (aka Sub-Stream).<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC-7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC-2326: Real Time Streaming Protocol 1.0</a>
	 */
	private void buildResponse_setup(
				@NonNull RtspProtoDataResponse inputDataResp,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoRscUrl rscUrlObj,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspProtoInvalidResponseException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_setup()";

		/*
		 * Example:
		 *   "RTSP/1.0 200 OK"
		 *   ...
		 *   "Transport: RTP/SAVP/UDP;unicast;source=127.0.0.1;destination=127.0.0.1;client_port=32968-32969;server_port=53270-53271;ssrc=7C9B4196"
		 */

		if (rscUrlObj.idSubStream.isEmpty()) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": rscUrlObj.idSubStream must be set");
		}
		if (! ioSetupInfosStream.containsSiForSubStreamId(rscUrlObj.idSubStream)) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Sub-Stream ID '" +
					rscUrlObj.idSubStream.getIdStr().orElse("-unset-") + "' not found");
		}

		RtspProtoSetupInfoForSubStream tmpSiSs = ioSetupInfosStream.getSiBySubStreamId(rscUrlObj.idSubStream).orElseThrow();
		try {
			tmpSiSs.isTransportValid(
					inputDataResp.rrStreamTpMain.getRtpRtcpEncryptionRequired(),
					inputDataResp.rrStreamTpMain.getForceRtpRtcpEncryption(),
					inputDataResp.rrStreamTpMain.getIsRtspsConnection(),
					cfgIsDebugDisableTransportUdp
				);
		} catch (RtspProtoInvalidTpSettingsException e) {
			// this should never happen
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Transport unsupported: " + e.getMessage());
		}

		// we need to open the UDP sockets now so we can get the port numbers
		try {
			RtspProtoHighUdpPorts.findAndOpenUdpSocketPorts(true, tmpSiSs);
		} catch (RtspProtoCouldNotFindUdpPortsException e) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": " + e.getMessage());
		}

		// Session ID
		{
			// generate RTSP Session ID if necessary
			String tmpOutpIdSession = inputDataResp.rrIdSession.getIdStr().orElse("");
			if (tmpOutpIdSession.isEmpty()) {
				tmpOutpIdSession = buildHexString(RandomHelper.getRandomUint32(false));
				logDebug(FNC_NAME, "New RTSP session ID: " + tmpOutpIdSession);
			}
			//
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.setIdStr(tmpOutpIdSession);
			if (RtspProtoHighConstants.DEFAULT_RTSP_SESSION_TIMEOUT >= 0) {
				try {
					hdEntry.hdValSession.setTimeout32bit(RtspProtoHighConstants.DEFAULT_RTSP_SESSION_TIMEOUT);
				} catch (RtspProtoNumberRangeException e) {
					throw new RtspProtoInvalidResponseException(FNC_NAME + ": Setting Session Timeout failed: " + e.getMessage());
				}
			} else {
				hdEntry.hdValSession.clearTimeout();
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// Transport
		{
			RtspProtoDataCntSubStreamTp tmpInpSubStreamTpPtr = tmpSiSs.getSubStreamTpPtr();

			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.TRANSPORT);
			hdEntry.hdValTransport.tpSubStream.setIsUdp(tmpInpSubStreamTpPtr.getIsUdp());
			hdEntry.hdValTransport.tpSubStream.setIsEncr(tmpInpSubStreamTpPtr.getIsEncr());
			hdEntry.hdValTransport.tpSubStream.setIsUnicast(tmpInpSubStreamTpPtr.getIsUnicast());
			hdEntry.hdValTransport.tpSubStream.setIsInterleaved(tmpInpSubStreamTpPtr.getIsInterleaved());
			hdEntry.hdValTransport.tpSourceIpOrHost = inputDataResp.rrServerIpFromRscUrl.getIpAddrStr().orElseThrow();
			hdEntry.hdValTransport.tpDestIpOrHost = getClientIpAddr(inputDataResp).getHostAddress();
			if (tmpInpSubStreamTpPtr.getIsUdp()) {
				hdEntry.hdValTransport.tpSubStream.getClientUdpPortRtpPtr().copyFrom(tmpInpSubStreamTpPtr.getClientUdpPortRtpPtr());
				hdEntry.hdValTransport.tpSubStream.getClientUdpPortRtcpPtr().copyFrom(tmpInpSubStreamTpPtr.getClientUdpPortRtcpPtr());
				try {
					Objects.requireNonNull(tmpSiSs.getServerUdpSocketRtpPtr());
					Objects.requireNonNull(tmpSiSs.getServerUdpSocketRtcpPtr());
					hdEntry.hdValTransport.tpSubStream.getServerUdpPortRtpPtr().setPort16bit(tmpSiSs.getServerUdpSocketRtpPtr().getLocalPort());
					hdEntry.hdValTransport.tpSubStream.getServerUdpPortRtcpPtr().setPort16bit(tmpSiSs.getServerUdpSocketRtcpPtr().getLocalPort());
				} catch (RtspProtoNumberRangeException e) {
					throw new RtspProtoInvalidResponseException(FNC_NAME + ": Setting Server UDP ports failed: " + e.getMessage());
				}
			} else {
				hdEntry.hdValTransport.tpSubStream.getClientTcpChannRtpPtr().copyFrom(tmpInpSubStreamTpPtr.getClientTcpChannRtpPtr());
				hdEntry.hdValTransport.tpSubStream.getClientTcpChannRtcpPtr().copyFrom(tmpInpSubStreamTpPtr.getClientTcpChannRtcpPtr());
			}
			hdEntry.hdValTransport.tpSsrcId.copyFrom(tmpSiSs.getSsrcOutboundPtr());
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		//
		tmpSiSs.setHaveSetup(true);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHexString(int value) {
		return String.format("%08X", value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void addCommonHeaders(
				@NonNull RtspProtoDataResponse inputDataResp,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) {
		// CSeq
		if (! inputDataResp.rrCseqNrLastRcvd.isEmpty()) {
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.cseqNr.copyFrom(inputDataResp.rrCseqNrLastRcvd);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Date
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.DATE);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Server / UserAgent
		{
			RtspProtoHeaderEntryResponse hdEntry;
			if (isResponseFromClient) {
				hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.USERAGENT);
				hdEntry.hdValUserAgent.userAgentStr = cfgSenderAppNameAndVersion;
			} else {
				hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.SERVER);
				hdEntry.hdValServer.serverStr = cfgSenderAppNameAndVersion;
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Session
		if (! inputDataResp.rrIdSession.isEmpty()) {
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.copyFrom(inputDataResp.rrIdSession);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	private void addAuthServerHeader(
				@NonNull RtspProtoDataResponse ioDataResp,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) {
		/*
		 * Example:
		 *   "WWW-Authenticate: Digest realm=\"Abcdef Some\", nonce=\"xxx\", algorithm=\"MD5\""
		 */

		if (isResponseFromClient) {
			return;
		}
		if (globalSessionInfoInterface != null && ioDataResp.respAuthServer.getAuthNonce().isBlank()) {
			ioDataResp.respAuthServer.setAuthNonce(
					globalSessionInfoInterface.createAuthServerNonce(ioDataResp.rrClientIpAddr)
				);
		}
		if (ioDataResp.respAuthServer.getAuthRealm().isBlank()) {
			ioDataResp.respAuthServer.setAuthRealm(RtspProtoHighConstants.DEFAULT_RTSP_AUTH_REALM);
		}

		RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.AUTH_SERVER);
		hdEntry.hdValAuthServer.authAlgo = RtspAuthAlgo.MD5;
		hdEntry.hdValAuthServer.authRealm = ioDataResp.respAuthServer.getAuthRealm();
		hdEntry.hdValAuthServer.authNonce = ioDataResp.respAuthServer.getAuthNonce();
		output.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	private void addContentTypeHeader(@NonNull RtspProtoHighMsgStructuredResponse output) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentTypeHeader()";

		if (output.messageType != RtspProtoMessageType.DESCRIBE &&
				output.messageType != RtspProtoMessageType.GET_PARAMETER && output.messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Content-Type header only allowed for " +
					"DESCRIBE/GET_PARAMETER/SET_PARAMETER messages");
		}
		RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.CONTENT_TYPE);
		hdEntry.hdValContType.contentType = (output.messageType == RtspProtoMessageType.DESCRIBE ?
				RtspMimeType.SDP : RtspMimeType.PARAMETERS);
		output.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	private void addContentLangHeader(
				@NonNull String contLang,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentLangHeader()";

		if (output.messageType != RtspProtoMessageType.DESCRIBE && output.messageType != RtspProtoMessageType.GET_PARAMETER) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Content-Language header only allowed for " +
					"DESCRIBE/GET_PARAMETER messages");
		}
		if (contLang.isBlank()) {
			return;
		}
		RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.CONTENT_LANG);
		hdEntry.hdValContLang.contentLangStr = contLang;
		output.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull InetAddress getClientIpAddr(@NonNull RtspProtoDataResponse dataResp)
			throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".getClientIpAddr()";

		if (isResponseFromClient) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": The client should not be calling this function");
		}
		return dataResp.rrClientIpAddr.getIpAddrObj()
				.orElseThrow(() -> new RtspProtoInvalidResponseException(FNC_NAME + ": Client IP address is not set"));
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

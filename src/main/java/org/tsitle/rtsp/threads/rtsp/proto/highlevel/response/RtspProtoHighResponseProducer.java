package org.tsitle.rtsp.threads.rtsp.proto.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntAdStreamSett;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.*;
import org.tsitle.rtsp.threads.rtsp.proto.ids.RtspProtoIdSubStream;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoAvailableStreamsInterface;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoParameterGetterInterface;
import org.tsitle.rtsp.threads.rtsp.proto.enums.RtspStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataResponse;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderTypeRtpinfo;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoStaticSessionDataInterface;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.interfaces.RtspProtoSdpProducerInterface;
import org.tsitle.rtsp.threads.rtsp.proto.misctypes.*;

import java.net.*;
import java.util.*;

public final class RtspProtoHighResponseProducer {

	private static final int SOCKET_UDP_RTP_TIMEOUT_MS = 50;
	private static final int SOCKET_UDP_RTCP_TIMEOUT_MS = 2;

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull String cfgServerNameAndVersion;
	private final boolean cfgIsDebugPrintRtspSdpSent;
	private final boolean cfgIsDebugDisableTransportUdp;
	private final @NonNull RtspProtoSdpProducerInterface sdpProducerInterface;
	private final @NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface;
	private final @NonNull RtspProtoStaticSessionDataInterface staticSessionDataInterface;
	private final @Nullable RtspProtoParameterGetterInterface parameterGetterInterface;
	private final boolean isResponseFromClient;

	public RtspProtoHighResponseProducer(
				@NonNull LogMsgInterface logMsgInterface,
				boolean isResponseFromClient,
				@NonNull String cfgServerNameAndVersion,
				boolean cfgIsDebugPrintRtspSdpSent,
				boolean cfgIsDebugDisableTransportUdp,
				@NonNull RtspProtoSdpProducerInterface sdpProducerInterface,
				@NonNull RtspProtoAvailableStreamsInterface availableStreamsInterface,
				@NonNull RtspProtoStaticSessionDataInterface staticSessionDataInterface,
				@Nullable RtspProtoParameterGetterInterface parameterGetterInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.cfgServerNameAndVersion = cfgServerNameAndVersion;
		this.cfgIsDebugPrintRtspSdpSent = cfgIsDebugPrintRtspSdpSent;
		this.cfgIsDebugDisableTransportUdp = cfgIsDebugDisableTransportUdp;
		this.sdpProducerInterface = sdpProducerInterface;
		this.availableStreamsInterface = availableStreamsInterface;
		this.staticSessionDataInterface = staticSessionDataInterface;
		this.parameterGetterInterface = parameterGetterInterface;
		this.isResponseFromClient = isResponseFromClient;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredResponse buildResponse(
				@NonNull RtspRequestBasics rtspRequestBasics,
				@NonNull RtspProtoSetupInfosStream ioSetupInfosStream,
				@NonNull RtspProtoDataResponse ioDataResp
			) throws RtspInvalidResponseException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse()";

		if (rtspRequestBasics.statusCode == RtspStatusCode.OK && ioDataResp.respRscUrl.isEmpty()) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Resource URL must be set");
		}

		//
		RtspProtoHighMsgStructuredResponse resObj = new RtspProtoHighMsgStructuredResponse();

		resObj.rtspProtoVersion = ioDataResp.getRtspProtoVersionToUse();
		resObj.statusCode = rtspRequestBasics.statusCode;
		resObj.messageType = rtspRequestBasics.messageType;

		//
		addCommonHeaders(ioDataResp, resObj);

		//
		if (rtspRequestBasics.statusCode != RtspStatusCode.OK) {
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
			default -> throw new RtspInvalidResponseException(FNC_NAME + ": Unsupported message type: " +
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
				@NonNull RtspStatusCode statusCode,
				@NonNull RtspProtoDataResponse ioDataResp,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspInvalidResponseException {
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
				output.bodyGetSetInvalidParams.copyFrom(ioDataResp.respInvalidParamNames);
				// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
				addContentTypeHeader(output);
				break;
			case RtspStatusCode.OPTION_NOT_SUPPORTED:
				// Unsupported
				{
					RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.UNSUPPORTED);
					hdEntry.hdValUnsupported.unsupportedFeatureStr = (ioDataResp.getUnsupportedFeatureName().isBlank()
							? "_unknown_" : ioDataResp.getUnsupportedFeatureName());
					output.headers.put(hdEntry.getHdKey(), hdEntry);
				}
				break;
			case RtspStatusCode.UNAUTHORIZED:
				addAuthServerHeader(ioDataResp, output);
				break;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildResponse_describe(
				@NonNull RtspProtoDataResponse inputDataResp,
				@NonNull RtspProtoSetupInfosStream outputSetupInfosStream,
				@NonNull RtspProtoHighMsgStructuredResponse outputMsgResp
			) throws RtspInvalidResponseException {
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

		//
		if (inputDataResp.respRscUrl.getUrlStr().isEmpty()) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Resource URL must be set");
		}
		if (inputDataResp.respRscUrl.idInputSource.isEmpty()) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Input Source ID must be set");
		}
		final String baseRscUrl = inputDataResp.respRscUrl.getUrlStr() +
				(inputDataResp.respRscUrl.getUrlStr().endsWith("/") ? "" : "/");

		//
		try {
			RtspProtoDataCntAdStreamSett outputAdStreamSett = new RtspProtoDataCntAdStreamSett();
			RtspProtoKmdsStream outputKmdsOutbound = new RtspProtoKmdsStream();

			// build SDP
			sdpProducerInterface.buildSdpForDescribe(
					inputDataResp.respStreamTpMain.isSrtpRequired(),
					inputDataResp.respRscUrl.idInputSource,
					inputDataResp.respServerIpFromRscUrl,
					inputDataResp.getClientUa(),
					inputDataResp.respClientIpAddr,
					outputMsgResp.bodyDescribeSdp,
					outputAdStreamSett,
					outputKmdsOutbound
				);

			// copy the stream settings and KMDs
			for (RtspProtoIdSubStream tmpIdSs : outputAdStreamSett.getSubStreamIds()) {
				RtspProtoDataCntAdStreamSett.SubStream tmpAvSs = outputAdStreamSett.getSettingsBySubStreamId(tmpIdSs)
						.orElseThrow();

				RtspProtoKmdForSubStream tmpProtoKmdOutbound = new RtspProtoKmdForSubStream();
				if (outputKmdsOutbound.containsKmdForSubStreamId(tmpIdSs)) {
					SrtxpKmd tmpSrtxpKmdOutbound = outputKmdsOutbound.getKmdBySubStreamId(tmpIdSs).orElseThrow();
					tmpProtoKmdOutbound.setKmd(tmpSrtxpKmdOutbound, tmpIdSs);
				}

				RtspProtoRscUrl tmpRscUrlSs = new RtspProtoRscUrl();
				tmpRscUrlSs.setUrlStr(baseRscUrl + tmpAvSs.getUrlSubPathForSubStream());
				tmpRscUrlSs.idInputSource.copyFrom(inputDataResp.respRscUrl.idInputSource);
				tmpRscUrlSs.idStreamSource.copyFrom(tmpAvSs.idStreamSource);
				tmpRscUrlSs.idSubStream.copyFrom(tmpIdSs);

				outputSetupInfosStream.createAndAddSetupSubStream(
						tmpRscUrlSs,
						tmpAvSs.getRtspSsrcId(),
						tmpProtoKmdOutbound
					);
			}
		} catch (RtspSdpException e) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Building SDP failed: " + e.getMessage());
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
			) throws RtspInvalidResponseException {
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

		if (inputDataResp.respGetParamNames.isParamNamesEmpty()) {
			// nothing to do
			return;
		}

		if (parameterGetterInterface == null) {
			output.statusCode = RtspStatusCode.INVALID_PARAMETER;
			output.bodyGetSetInvalidParams.copyFrom(inputDataResp.respGetParamNames);
		} else {
			RtspProtoDataCntGetSetParamKvs tmpDataGsp =
					parameterGetterInterface.getAllRtspParameters(inputDataResp.respIdSession);

			Set<String> tmpMissingParams = new HashSet<>();
			for (String requParam : inputDataResp.respGetParamNames.getParamNames()) {
				Optional<String> tmpRequParVal = tmpDataGsp.getParamKvsValue(requParam);
				if (tmpRequParVal.isEmpty()) {
					tmpMissingParams.add(requParam);
				} else {
					output.bodyGetParamKv.putParamKvsEntry(requParam, tmpRequParVal.get());
				}
			}

			output.bodyGetParamKv.setContentLang(tmpDataGsp.getContentLang());

			if (! tmpMissingParams.isEmpty()) {
				output.statusCode = RtspStatusCode.INVALID_PARAMETER;
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
			) throws RtspInvalidResponseException {
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
		if (! ioDataResp.respRscUrl.idInputSource.isEmpty()) {
			boolean tmpNeedAuth;
			try {
				tmpNeedAuth = availableStreamsInterface.getInputSourceObj(ioDataResp.respRscUrl.idInputSource)
						.getNeedsAuthentication();
			} catch (RtspIdInputSourceNotFoundException e) {
				throw new RtspInvalidResponseException(FNC_NAME + ": " + e.getMessage());
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
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_play()";

		/*
		 * Example:
		 *   "RTSP/1.0 200 OK"
		 *   "Date: Sat, 6 Jun 2026 18:27:45 GMT"
		 *   "Range: npt=0.000-"
		 *   "Server: TS RTSP Server/1.0"
		 *   "RTP-Info: url=rtsp://...;seq=2786;rtptime=1380770927,url=...;seq=22526;rtptime=257277163"
		 *   "CSeq: 7"
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
				tmpStreamInfoOutput.urlStr = tmpSiSs.rscUrlSubStream.getUrlStr();
				try {
					tmpStreamInfoOutput.setSeqNr16bit(tmpSiSs.rtspRtpSeqNrT0);
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting RTP sequence number failed: " + e.getMessage());
				}
				try {
					tmpStreamInfoOutput.setRtpTimestamp32bit(tmpSiSs.rtspRtpTimestampT0);
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting RTP timestamp failed: " + e.getMessage());
				}
				try {
					tmpStreamInfoOutput.setSsrcId32bit(tmpSiSs.rtspSsrcId);
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting SSRC ID failed: " + e.getMessage());
				}
				if (tmpSubStreamNr == 1) {
					hdEntry.hdValRtpinfo.setSubStream1(tmpStreamInfoOutput);
				} else if (tmpSubStreamNr == 2) {
					hdEntry.hdValRtpinfo.setSubStream2(tmpStreamInfoOutput);
				} else {
					throw new RtspInvalidResponseException(FNC_NAME + ": Too many sub-streams");
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
			) throws RtspInvalidResponseException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_setup()";

		/*
		 * Example:
		 *   "RTSP/1.0 200 OK"
		 *   ...
		 *   "Transport: RTP/SAVP/UDP;unicast;source=127.0.0.1;destination=127.0.0.1;client_port=32968-32969;server_port=53270-53271;ssrc=7C9B4196"
		 */

		if (rscUrlObj.idSubStream.isEmpty()) {
			throw new RtspInvalidResponseException(FNC_NAME + ": rscUrlObj.idSubStream must be set");
		}
		if (! ioSetupInfosStream.containsSiForSubStreamId(rscUrlObj.idSubStream)) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Sub-Stream ID '" +
					rscUrlObj.idSubStream.getIdStr() + "' not found");
		}

		RtspProtoSetupInfoForSubStream tmpSiSs = ioSetupInfosStream.getSiBySubStreamId(rscUrlObj.idSubStream).orElseThrow();
		try {
			tmpSiSs.isTransportValid(
					inputDataResp.respStreamTpMain.getRtpRtcpEncryptionRequired(),
					inputDataResp.respStreamTpMain.getForceRtpRtcpEncryption(),
					inputDataResp.respStreamTpMain.getIsRtspsConnection(),
					cfgIsDebugDisableTransportUdp
				);
		} catch (RtspInvalidTpSettingsException e) {
			// this should never happen
			throw new RtspInvalidResponseException(FNC_NAME + ": Transport unsupported: " + e.getMessage());
		}

		// we need to open the UDP sockets now so we can get the port numbers
		findAndOpenUdpSocketPorts(tmpSiSs);

		// Session ID
		{
			// generate RTSP Session ID if necessary
			String tmpOutpIdSession = inputDataResp.respIdSession.getIdStr();
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
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting Session Timeout failed: " + e.getMessage());
				}
			} else {
				hdEntry.hdValSession.clearTimeout();
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// Transport
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.TRANSPORT);
			hdEntry.hdValTransport.tpSubStream.setIsUdp(tmpSiSs.subStreamTp.getIsUdp());
			hdEntry.hdValTransport.tpSubStream.setIsEncr(tmpSiSs.subStreamTp.getIsEncr());
			hdEntry.hdValTransport.tpSubStream.setIsUnicast(tmpSiSs.subStreamTp.getIsUnicast());
			hdEntry.hdValTransport.tpSubStream.setIsInterleaved(tmpSiSs.subStreamTp.getIsInterleaved());
			hdEntry.hdValTransport.tpSourceIpOrHost = inputDataResp.respServerIpFromRscUrl.getIpAddrStr().orElseThrow();
			hdEntry.hdValTransport.tpDestIpOrHost = getClientIpAddr(inputDataResp).getHostAddress();
			if (tmpSiSs.subStreamTp.getIsUdp()) {
				hdEntry.hdValTransport.tpSubStream.tpClientUdpPortRtp.copyFrom(tmpSiSs.subStreamTp.tpClientUdpPortRtp);
				hdEntry.hdValTransport.tpSubStream.tpClientUdpPortRtcp.copyFrom(tmpSiSs.subStreamTp.tpClientUdpPortRtcp);
				try {
					Objects.requireNonNull(tmpSiSs.tpServerUdpSocketRtp);
					Objects.requireNonNull(tmpSiSs.tpServerUdpSocketRtcp);
					hdEntry.hdValTransport.tpSubStream.tpServerUdpPortRtp.setPort16bit(tmpSiSs.tpServerUdpSocketRtp.getLocalPort());
					hdEntry.hdValTransport.tpSubStream.tpServerUdpPortRtcp.setPort16bit(tmpSiSs.tpServerUdpSocketRtcp.getLocalPort());
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting Server UDP ports failed: " + e.getMessage());
				}
			} else {
				hdEntry.hdValTransport.tpSubStream.tpClientTcpChannRtp.copyFrom(tmpSiSs.subStreamTp.tpClientTcpChannRtp);
				hdEntry.hdValTransport.tpSubStream.tpClientTcpChannRtcp.copyFrom(tmpSiSs.subStreamTp.tpClientTcpChannRtcp);
			}
			try {
				hdEntry.hdValTransport.setSsrcId32bit(tmpSiSs.rtspSsrcId);
			} catch (RtspNumberRangeException e) {
				throw new RtspInvalidResponseException(FNC_NAME + ": Setting SSRC ID failed: " + e.getMessage());
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		//
		tmpSiSs.haveSetup = true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Find and open UDP sockets for RTP and RTCP in accordance with RFC-3551 Section 8
	 */
	private void findAndOpenUdpSocketPorts(@NonNull RtspProtoSetupInfoForSubStream siSs)
			throws UdpSocketIoException, RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".findAndOpenUdpSocketPorts()";

		if (! siSs.subStreamTp.getIsUdp()) {
			return;
		}

		int loopCnt = 0;
		boolean isOk = false;
		while (++loopCnt <= 1000) {
			if (siSs.tpServerUdpSocketRtp != null) {
				siSs.tpServerUdpSocketRtp.close();
			}
			if (siSs.tpServerUdpSocketRtcp != null) {
				siSs.tpServerUdpSocketRtcp.close();
			}
			try {
				siSs.tpServerUdpSocketRtp = new DatagramSocket();
				if (siSs.tpServerUdpSocketRtp.getLocalPort() % 2 != 0) {
					continue;
				}
				siSs.tpServerUdpSocketRtcp = new DatagramSocket(
						siSs.tpServerUdpSocketRtp.getLocalPort() + 1
					);
				isOk = true;
				break;
			} catch (SocketException e) {
				// keep going until we find a free port pair
			}
		}
		if (! isOk) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Could not find proper UDP sockets");
		}
		try {
			siSs.tpServerUdpSocketRtp.setSoTimeout(SOCKET_UDP_RTP_TIMEOUT_MS);
			siSs.tpServerUdpSocketRtp.setSendBufferSize(1024 * 1024);  // this is only a hint, not the actual buffer size
			siSs.tpServerUdpSocketRtcp.setSoTimeout(SOCKET_UDP_RTCP_TIMEOUT_MS);
			siSs.tpServerUdpSocketRtcp.setSendBufferSize(1024 * 64);  // this is only a hint, not the actual buffer size
		} catch (SocketException e) {
			throw new UdpSocketIoException(FNC_NAME + ": Could not configure UDP sockets: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHexString(int value) {
		return String.format("%08X", value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void addCommonHeaders(
				@NonNull RtspProtoDataResponse inputDataResp,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".addCommonHeaders()";

		// CSeq
		if (inputDataResp.getCseqNrLastRcvd() >= 0L) {
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.CSEQ);
			try {
				hdEntry.hdValCseq.setCseqNr32bit(inputDataResp.getCseqNrLastRcvd());
			} catch (RtspNumberRangeException e) {
				throw new RtspInvalidResponseException(FNC_NAME + ": Setting CSeq failed: " + e.getMessage());
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Date
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.DATE);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Server
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.SERVER);
			hdEntry.hdValServer.serverStr = cfgServerNameAndVersion;
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Session
		if (! inputDataResp.respIdSession.isEmpty()) {
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.idSession.copyFrom(inputDataResp.respIdSession);
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
		if (ioDataResp.respAuthServer.getAuthNonce().isBlank()) {
			ioDataResp.respAuthServer.setAuthNonce(
					staticSessionDataInterface.createAuthServerNonce(ioDataResp.respClientIpAddr)
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

	private void addContentTypeHeader(@NonNull RtspProtoHighMsgStructuredResponse output) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentTypeHeader()";

		if (output.messageType != RtspMessageType.DESCRIBE && output.messageType != RtspMessageType.GET_PARAMETER) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Content-Type header only allowed for " +
					"DESCRIBE/GET_PARAMETER messages");
		}
		RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.CONTENT_TYPE);
		hdEntry.hdValContType.contentType = (output.messageType == RtspMessageType.DESCRIBE ?
				RtspMimeType.SDP : RtspMimeType.PARAMETERS);
		output.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	private void addContentLangHeader(
				@NonNull String contLang,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentLangHeader()";

		if (output.messageType != RtspMessageType.DESCRIBE && output.messageType != RtspMessageType.GET_PARAMETER) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Content-Language header only allowed for " +
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
			throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".getClientIpAddr()";

		if (isResponseFromClient) {
			throw new RtspInvalidResponseException(FNC_NAME + ": The client should not be calling this function");
		}
		return dataResp.respClientIpAddr.getIpAddrObj()
				.orElseThrow(() -> new RtspInvalidResponseException(FNC_NAME + ": Client IP address is not set"));
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

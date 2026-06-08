package org.tsitle.rtsp.threads.rtsp.proto.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoParameterGetterInterface;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataResponse;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.*;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspSdpException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspProtoHighConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderTypeRtpinfo;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;

import java.net.*;
import java.util.*;

public final class RtspProtoHighResponseProducer {

	private static final int SOCKET_UDP_RTP_TIMEOUT_MS = 50;
	private static final int SOCKET_UDP_RTCP_TIMEOUT_MS = 2;

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull String cfgServerNameAndVersion;
	private final @NonNull RtspSessionInfo rtspSessionInfo;
	private final @Nullable RtspProtoParameterGetterInterface rtspProtoParameterGetterInterface;

	public RtspProtoHighResponseProducer(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull String cfgServerNameAndVersion,
				@NonNull RtspSessionInfo rtspSessionInfo,
				@Nullable RtspProtoParameterGetterInterface rtspProtoParameterGetterInterface
			) {
		if (cfgServerNameAndVersion.isBlank()) {
			throw new IllegalArgumentException("cfgServerNameAndVersion cannot be blank");
		}
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.cfgServerNameAndVersion = cfgServerNameAndVersion;
		this.rtspSessionInfo = rtspSessionInfo;
		this.rtspProtoParameterGetterInterface = rtspProtoParameterGetterInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredResponse buildResponse(
				@NonNull RtspRequestBasics rtspRequestBasics,
				@NonNull RtspProtoDataResponse inputDataResp
			) throws RtspInvalidResponseException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse()";

		RtspProtoHighMsgStructuredResponse resObj = new RtspProtoHighMsgStructuredResponse();

		resObj.rtspProtoVersion = rtspSessionInfo.rtspProtoVersionToUse;
		resObj.statusCode = rtspRequestBasics.statusCode;
		resObj.messageType = rtspRequestBasics.messageType;

		//
		addCommonHeaders(resObj);

		//
		if (rtspRequestBasics.statusCode != RtspStatusCode.OK) {
			buildResponse_nack(rtspRequestBasics.statusCode, inputDataResp, resObj);
			return resObj;
		}

		//
		switch (rtspRequestBasics.messageType) {
			case ANNOUNCE, PAUSE, RECORD, REDIRECT, SET_PARAMETER, TEARDOWN -> buildResponse_ack();
			case DESCRIBE -> buildResponse_describe(resObj);
			case GET_PARAMETER -> buildResponse_getParameter(inputDataResp, resObj);
			case OPTIONS -> buildResponse_options(resObj);
			case PLAY -> buildResponse_play(resObj);
			case SETUP -> buildResponse_setup(rtspRequestBasics.requestUrlInputOrStreamSource, resObj);
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
				@NonNull RtspProtoDataResponse inputDataResp,
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
				output.bodyGetSetInvalidParams.addAll(inputDataResp.respInvalidParamNames.paramNames);
				// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
				addContentTypeHeader(output);
				break;
			case RtspStatusCode.OPTION_NOT_SUPPORTED:
				// Unsupported
				{
					RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.UNSUPPORTED);
					hdEntry.hdValUnsupported.unsupportedFeatureStr = (inputDataResp.respUnsupportedFeatureName.isBlank()
							? "_unknown_" : inputDataResp.respUnsupportedFeatureName);
					output.headers.put(hdEntry.getHdKey(), hdEntry);
				}
				break;
			case RtspStatusCode.UNAUTHORIZED:
				addAuthServerHeader(output);
				break;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildResponse_describe(@NonNull RtspProtoHighMsgStructuredResponse output) throws RtspInvalidResponseException {
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

		RtspInputSource rtspInputSource = rtspSessionInfo.getInputSourceObjForMt_nonSetup(RtspMessageType.DESCRIBE)
				.orElseThrow(() -> new RtspInvalidResponseException(FNC_NAME + ": Input Source not found"));
		String resourceUrl = rtspSessionInfo.getResourceUrlForMt_nonSetup(RtspMessageType.DESCRIBE)
				.orElseThrow(() -> new RtspInvalidResponseException(FNC_NAME + ": Resource URL not found"));

		if (! checkStreamsForInputSource(FNC_NAME, rtspInputSource)) {
			output.statusCode = RtspStatusCode.BAD_REQUEST;
			return;
		}

		// @TODO fetch SDP from SDP-Producer
		// @TODO fetch Content-Language from SDP-Producer
		String sdpContentLanguage = "";
		SdpBuilder sdpBuilder = new SdpBuilder(rtspConfig, cfgServerNameAndVersion, rtspSessionInfo);
		List<@NonNull String> tmpSdpLines;
		try {
			tmpSdpLines = sdpBuilder.buildSdp(
					rtspInputSource,
					findRtspHostIp(RtspMessageType.DESCRIBE, "")
				);
		} catch (RtspSdpException e) {
			logError(FNC_NAME, "Building SDP failed: " + e.getMessage());
			output.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
			return;
		}
		output.bodyDescribeSdp.addAll(tmpSdpLines);

		if (rtspConfig.getIsDebugPrintRtspSdpSent()) {
			logDebug(FNC_NAME, "-------- SDP:");
			for (String tmpSingleSdpLine : tmpSdpLines) {
				logDebug(FNC_NAME, "---------------- " + tmpSingleSdpLine);
			}
		}

		// Content-Base
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.CONTENT_BASE);
			hdEntry.hdValContBase.contentBaseStr = resourceUrl + (resourceUrl.endsWith("/") ? "" : "/");
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
		addContentTypeHeader(output);
		// Content-Language
		addContentLangHeader(sdpContentLanguage, output);
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

		if (inputDataResp.respGetParamNames.paramNames.isEmpty()) {
			// nothing to do
			return;
		}

		String tmpContLang = "";
		if (rtspProtoParameterGetterInterface == null) {
			output.statusCode = RtspStatusCode.INVALID_PARAMETER;
			output.bodyGetSetInvalidParams.addAll(inputDataResp.respGetParamNames.paramNames);
		} else {
			RtspProtoDataCntGetSetParamKvs tmpDataGsp =
					rtspProtoParameterGetterInterface.getAllRtspParameters(rtspSessionInfo.rtspSessionId);

			Set<String> tmpMissingParams = new HashSet<>();
			for (String requParam : inputDataResp.respGetParamNames.paramNames) {
				if (! tmpDataGsp.paramKvs.containsKey(requParam)) {
					tmpMissingParams.add(requParam);
				} else {
					output.bodyGetParamKv.put(requParam, tmpDataGsp.paramKvs.get(requParam));
				}
			}

			tmpContLang = tmpDataGsp.contentLang;

			if (! tmpMissingParams.isEmpty()) {
				output.statusCode = RtspStatusCode.INVALID_PARAMETER;
				output.bodyGetParamKv.clear();
				output.bodyGetSetInvalidParams.addAll(tmpMissingParams);
			}
		}

		// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
		addContentTypeHeader(output);
		// Content-Language
		addContentLangHeader(tmpContLang, output);
	}

	private void buildResponse_options(@NonNull RtspProtoHighMsgStructuredResponse output) throws RtspInvalidResponseException {
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
			hdEntry.hdValPublic.messageTypes.addAll(RtspProtoHighConstants.LH_SUPPORTED_MESSAGE_TYPES);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Auth
		if (rtspSessionInfo.existsInputSourceObjForMt_nonSetup(RtspMessageType.OPTIONS)) {
			boolean tmpNeedAuth = rtspSessionInfo.getInputSourceObjForMt_nonSetup(RtspMessageType.OPTIONS)
					.orElseThrow().getNeedsAuthentication();
			if (tmpNeedAuth) {
				addAuthServerHeader(output);
			}
		}
	}

	/**
	 * The client makes one PLAY request per Input Source
	 */
	private void buildResponse_play(@NonNull RtspProtoHighMsgStructuredResponse output) throws RtspInvalidResponseException {
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
			hdEntry.hdValRange.rangeStr = rtspSessionInfo.clientPlaybackRangeValue;
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// RTP-Info
		{
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.RTPINFO);
			int tmpSubStreamNr = 1;
			for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
				RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStreamInp = RtspStaticSessionInfo.getSetupSubStreamOrThrow(
						FNC_NAME,
						tmpSubStreamId
					);
				RtspProtoHeaderTypeRtpinfo.SubStream tmpStreamInfoOutput = new RtspProtoHeaderTypeRtpinfo.SubStream();
				tmpStreamInfoOutput.urlStr = tmpSetupSubStreamInp.inputSourceUrlSetup;
				try {
					tmpStreamInfoOutput.setSeqNr16bit(tmpSetupSubStreamInp.rtspRtpSeqNrT0);
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting RTP sequence number failed: " + e.getMessage());
				}
				try {
					tmpStreamInfoOutput.setRtpTimestamp32bit(tmpSetupSubStreamInp.rtspRtpTimestampT0);
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting RTP timestamp failed: " + e.getMessage());
				}
				try {
					tmpStreamInfoOutput.setSsrcId32bit(tmpSetupSubStreamInp.rtspSsrcId);
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
				RtspRequestBasics.@Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspInvalidResponseException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_setup()";

		/*
		 * Example:
		 *   "RTSP/1.0 200 OK"
		 *   "Date: Sat, 6 Jun 2026 18:27:45 GMT"
		 *   "Transport: RTP/SAVP/UDP;unicast;source=127.0.0.1;destination=127.0.0.1;client_port=32968-32969;server_port=53270-53271;ssrc=7C9B4196"
		 *   "Server: TS RTSP Server/1.0"
		 *   "CSeq: 6"
		 *   "Session: 2BA52D87;timeout=20"
		 */

		if (requestUrlInputOrStreamSource == null) {
			throw new RtspInvalidResponseException(FNC_NAME + ": requestUrlInputOrStreamSource is null");
		}
		if (requestUrlInputOrStreamSource.subStreamId == null) {
			throw new RtspInvalidResponseException(FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null");
		}

		RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStream = RtspStaticSessionInfo.getSetupSubStreamOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.subStreamId
			);
		try {
			tmpSetupSubStream.isTransportValid(
					rtspSessionInfo.isRtpRtcpEncryptionRequired,
					rtspSessionInfo.forceRtpRtcpEncryption,
					rtspSessionInfo.isRtspsConnection,
					rtspConfig.getIsDebugDisableTransportUdp()
				);
		} catch (Exception e) {
			// this should never happen
			throw new RtspInvalidResponseException(FNC_NAME + ": Transport unsupported: " + e.getMessage());
		}

		// Session ID
		{
			// generate RTSP Session ID
			if (rtspSessionInfo.rtspSessionId.isBlank()) {
				rtspSessionInfo.rtspSessionId = buildHexString(RandomHelper.getRandomUint32(false));
				logDebug(FNC_NAME, "New RTSP session ID: " + rtspSessionInfo.rtspSessionId);
			}
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.sessionIdStr = rtspSessionInfo.rtspSessionId;
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

		// we need to open the sockets now so we can get the port numbers
		findAndOpenUdpSocketPorts(tmpSetupSubStream);
		Objects.requireNonNull(tmpSetupSubStream.tpServerUdpSocketRtp);
		Objects.requireNonNull(tmpSetupSubStream.tpServerUdpSocketRtcp);

		// Transport
		{
			String tmpRtspHostIp = findRtspHostIp(
					RtspMessageType.SETUP,
					requestUrlInputOrStreamSource.subStreamId
				);

			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.TRANSPORT);
			hdEntry.hdValTransport.tpIsUdp = tmpSetupSubStream.tpIsUdp;
			hdEntry.hdValTransport.tpIsEncr = tmpSetupSubStream.tpIsEncr;
			hdEntry.hdValTransport.tpIsUnicast = tmpSetupSubStream.tpIsUnicast;
			hdEntry.hdValTransport.tpIsInterleaved = tmpSetupSubStream.tpIsInterleaved;
			hdEntry.hdValTransport.tpSourceIpOrHost = tmpRtspHostIp;
			hdEntry.hdValTransport.tpDestIpOrHost = getClientIpAddr().getHostAddress();
			if (tmpSetupSubStream.tpIsUdp) {
				try {
					hdEntry.hdValTransport.setClientUdpPortRtp16bit(tmpSetupSubStream.tpClientUdpPortRtp);
					hdEntry.hdValTransport.setClientUdpPortRtcp16bit(tmpSetupSubStream.tpClientUdpPortRtcp);
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting Client UDP ports failed: " + e.getMessage());
				}
				try {
					hdEntry.hdValTransport.setServerUdpPortRtp16bit(tmpSetupSubStream.tpServerUdpSocketRtp.getLocalPort());
					hdEntry.hdValTransport.setServerUdpPortRtcp16bit(tmpSetupSubStream.tpServerUdpSocketRtcp.getLocalPort());
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting Server UDP ports failed: " + e.getMessage());
				}
			} else {
				try {
					hdEntry.hdValTransport.setClientTcpChannRtp16bit(tmpSetupSubStream.tpClientTcpChannRtp);
					hdEntry.hdValTransport.setClientTcpChannRtcp16bit(tmpSetupSubStream.tpClientTcpChannRtcp);
				} catch (RtspNumberRangeException e) {
					throw new RtspInvalidResponseException(FNC_NAME + ": Setting Client TCP channels failed: " + e.getMessage());
				}
			}
			try {
				hdEntry.hdValTransport.setSsrcId32bit(tmpSetupSubStream.rtspSsrcId);
			} catch (RtspNumberRangeException e) {
				throw new RtspInvalidResponseException(FNC_NAME + ": Setting SSRC ID failed: " + e.getMessage());
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean checkStreamsForInputSource(@NonNull String fncName, @NonNull RtspInputSource rtspInputSource) {
		Optional<RtspStreamSource> optSsObjVideo =
				rtspConfig.getInputSourcesFirstOfKindStreamSourceObj(rtspInputSource.getId(), true);
		Optional<RtspStreamSource> optSsObjAudio =
				rtspConfig.getInputSourcesFirstOfKindStreamSourceObj(rtspInputSource.getId(), false);
		if (! (optSsObjVideo.isPresent() || optSsObjAudio.isPresent())) {
			logError(fncName, "No valid Stream Source found for Input Source '" + rtspInputSource.getId() + "'");
			return false;
		}
		return true;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String findRtspHostIp(
				@NonNull RtspMessageType messageType,
				@NonNull String subStreamId
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".findRtspHostIp()";

		if (messageType == RtspMessageType.SETUP && subStreamId.isBlank()) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Sub-Stream ID is blank for SETUP message type");
		}

		String tmpRtspHostname;
		try {
			Optional<String> tmpOptRscUrl = (messageType == RtspMessageType.SETUP ?
					rtspSessionInfo.getResourceUrlForMt_onlySetup(subStreamId)
					: rtspSessionInfo.getResourceUrlForMt_nonSetup(messageType));
			if (tmpOptRscUrl.isEmpty()) {
				throw new RtspInvalidResponseException(FNC_NAME + ": No Resource URL found for message type: " + messageType);
			}
			URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(tmpOptRscUrl.get());
			tmpRtspHostname = rscUriObj.getHost();
		} catch (HostnameHelperInvalidUriException e) {
			// this should never happen
			throw new RtspInvalidResponseException(FNC_NAME + ": Could not parse URL: " + e.getMessage());
		}
		if (tmpRtspHostname.isBlank()) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Could not determine RTSP hostname");
		}
		try {
			Optional<InetAddress> optRtspHostIp = HostnameHelper.firstAvailableLocalIpv4AddressForHostname(
					tmpRtspHostname,
					true
				);
			if (optRtspHostIp.isEmpty()) {
				throw new RtspInvalidResponseException(FNC_NAME + ": Could not determine IPv4 address for RTSP hostname '" +
						tmpRtspHostname + "'");
			}
			return optRtspHostIp.get().getHostAddress();
		} catch (UnknownHostException | SocketException e) {
			throw new RtspInvalidResponseException(FNC_NAME + ": Unknown RTSP hostname '" + tmpRtspHostname + "'");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Find and open UDP sockets for RTP and RTCP in accordance with RFC-3551 Section 8
	 */
	private void findAndOpenUdpSocketPorts(RtspStaticSessionInfo.@NonNull SetupSubStreamInfo setupSubStreamInfo)
			throws UdpSocketIoException, RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".findAndOpenUdpSocketPorts()";

		int loopCnt = 0;
		boolean isOk = false;
		while (++loopCnt <= 1000) {
			if (setupSubStreamInfo.tpServerUdpSocketRtp != null) {
				setupSubStreamInfo.tpServerUdpSocketRtp.close();
			}
			if (setupSubStreamInfo.tpServerUdpSocketRtcp != null) {
				setupSubStreamInfo.tpServerUdpSocketRtcp.close();
			}
			try {
				setupSubStreamInfo.tpServerUdpSocketRtp = new DatagramSocket();
				if (setupSubStreamInfo.tpServerUdpSocketRtp.getLocalPort() % 2 != 0) {
					continue;
				}
				setupSubStreamInfo.tpServerUdpSocketRtcp = new DatagramSocket(
						setupSubStreamInfo.tpServerUdpSocketRtp.getLocalPort() + 1
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
			setupSubStreamInfo.tpServerUdpSocketRtp.setSoTimeout(SOCKET_UDP_RTP_TIMEOUT_MS);
			setupSubStreamInfo.tpServerUdpSocketRtp.setSendBufferSize(1024 * 1024);  // this is only a hint, not the actual buffer size
			setupSubStreamInfo.tpServerUdpSocketRtcp.setSoTimeout(SOCKET_UDP_RTCP_TIMEOUT_MS);
			setupSubStreamInfo.tpServerUdpSocketRtcp.setSendBufferSize(1024 * 64);  // this is only a hint, not the actual buffer size
		} catch (SocketException e) {
			throw new UdpSocketIoException(FNC_NAME + ": Could not configure UDP sockets: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHexString(int value) {
		return String.format("%08X", value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void addCommonHeaders(@NonNull RtspProtoHighMsgStructuredResponse output) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".addCommonHeaders()";

		// CSeq
		if (rtspSessionInfo.seqNr_requRem_lastRcvd >= 0) {
			RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.CSEQ);
			try {
				hdEntry.hdValCseq.setCseqNr32bit(rtspSessionInfo.seqNr_requRem_lastRcvd);
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
	}

	private void addAuthServerHeader(@NonNull RtspProtoHighMsgStructuredResponse output) throws RtspInvalidResponseException {
		/*
		 * Example:
		 *   "WWW-Authenticate: Digest realm=\"Abcdef Some\", nonce=\"xxx\", algorithm=\"MD5\""
		 */
		if (rtspSessionInfo.permAuthServer.authNonce.isBlank()) {
			rtspSessionInfo.permAuthServer.authNonce = RtspStaticSessionInfo.addAuthServerNonce(getClientIpAddr());
		}
		if (rtspSessionInfo.permAuthServer.authRealm.isBlank()) {
			rtspSessionInfo.permAuthServer.authRealm = RtspProtoHighConstants.DEFAULT_RTSP_AUTH_REALM;
		}

		RtspProtoHeaderEntryResponse hdEntry = new RtspProtoHeaderEntryResponse(RtspHeaderKey.AUTH_SERVER);
		hdEntry.hdValAuthServer.authAlgo = RtspAuthAlgo.MD5;
		hdEntry.hdValAuthServer.authRealm = rtspSessionInfo.permAuthServer.authRealm;
		hdEntry.hdValAuthServer.authNonce = rtspSessionInfo.permAuthServer.authNonce;
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

	private @NonNull InetAddress getClientIpAddr() throws RtspInvalidResponseException {
		return rtspSessionInfo.getClientIpAddr()
				.orElseThrow(() -> new RtspInvalidResponseException("Client IP address is not set"));
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

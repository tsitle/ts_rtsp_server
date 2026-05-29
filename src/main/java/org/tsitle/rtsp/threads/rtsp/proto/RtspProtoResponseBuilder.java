package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.exceptions.TcpSocketIoException;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtsp.*;

import java.io.StringWriter;
import java.net.*;
import java.util.*;

import static org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants.*;

public final class RtspProtoResponseBuilder extends RtspProtoBuilderBase {

	private static final int SOCKET_UDP_RTP_TIMEOUT_MS = 50;
	private static final int SOCKET_UDP_RTCP_TIMEOUT_MS = 2;

	private final RtspConfig rtspConfig;

	public RtspProtoResponseBuilder(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtxpTcpReadWrite rtxpTcpReadWriteInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		super(logMsgInterface, rtxpTcpReadWriteInterface, rtspSessionInfo);

		this.rtspConfig = rtspConfig;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void sendResponse(@NonNull RequestBasicInfo requestBasicInfo) throws TcpSocketIoException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponse()";

		if (requestBasicInfo.statusCode != RtspProtoStatusCode.OK) {
			sendResponseNack(requestBasicInfo.statusCode);
			return;
		}
		switch (requestBasicInfo.messageType) {
			case GET_PARAMETER:
				sendResponseGetParameter();
				break;
			case SET_PARAMETER:
				sendResponseSetParameter();
				break;
			case OPTIONS:
				sendResponseOptions();
				break;
			case DESCRIBE:
				sendResponseDescribe();
				break;
			case SETUP:
				sendResponseSetup(Objects.requireNonNull(requestBasicInfo.requestUrlInputOrStreamSource));
				break;
			case PLAY:
				sendResponsePlay();
				break;
			case PAUSE, TEARDOWN:
				try {
					sendResponseAck();
				} catch (TcpSocketIoException e) {
					//logWarn(FNC_NAME, ": Failed to send response ack: " + e.getMessage());
					// fail silently
				}
				break;
			default:
				throw new IllegalStateException(FNC_NAME + ": Unsupported message type: " +
						requestBasicInfo.messageType);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void sendResponseNack(@NonNull RtspProtoStatusCode statusCode) throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseNack()";

		List<String> contents = new ArrayList<>();
		if (statusCode == RtspProtoStatusCode.UNAUTHORIZED) {
			addAuthInfoToResponse(contents);
		}
		contents.add("");
		internalSendResponse(statusCode, contents);
		logDebug(FNC_NAME, "Sent response '" + statusCode +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspClientSeqNrResponse + ")\n");
	}

	private void sendResponseAck() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseAck()";

		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_SESSION + " " + rtspSessionInfo.rtspSessionId);
		contents.add("");
		internalSendResponse(contents);
		logDebug(FNC_NAME, "Sent response '" + RtspProtoStatusCode.OK +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspClientSeqNrResponse + ")\n");
	}

	private void sendResponseGetParameter() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseGetParameter()";

		List<String> contents = new ArrayList<>();
		contents.add("");
		internalSendResponse(contents);
		logDebug(FNC_NAME, "Sent response '" + RtspProtoStatusCode.OK +
				"' to Client (<" +
				(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId) +
				">, CSeq=" + rtspSessionInfo.rtspClientSeqNrResponse + ")\n");
	}

	private void sendResponseSetParameter() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseSetParameter()";

		List<String> contents = new ArrayList<>();
		contents.add("");
		internalSendResponse(contents);
		logDebug(FNC_NAME, "Sent response '" + RtspProtoStatusCode.OK +
				"' to Client (<" +
				(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId) +
				">, CSeq=" + rtspSessionInfo.rtspClientSeqNrResponse + ")\n");
	}

	private void sendResponseOptions() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseOptions()";

		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_SERVER + " " + SERVER_NAME);
		List<String> tmpList = SUPPORTED_MESSAGE_TYPES_SERVER.stream()
				.filter(tmpType -> tmpType != RtspProtoMessageType.UNKNOWN)
				.map(Enum::name)
				.toList();
		contents.add(RTSP_RR_HEADER_TOKEN_OPT_PUBLIC + " " + String.join(", ", tmpList));
		if (rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspProtoMessageType.OPTIONS)) {
			boolean tmpNeedAuth = rtspSessionInfo.inputSourceObjPerMtMap.get(RtspProtoMessageType.OPTIONS).getNeedsAuthentication();
			if (tmpNeedAuth) {
				addAuthInfoToResponse(contents);
			}
		}
		contents.add("");
		internalSendResponse(contents);
		logDebug(FNC_NAME, "Sent response '" + RtspProtoStatusCode.OK +
				"' to Client (<" +
				(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId) +
				">, CSeq=" + rtspSessionInfo.rtspClientSeqNrResponse + ")\n");
	}

	private void sendResponseDescribe() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseDescribe()";

		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspProtoMessageType.DESCRIBE)) {
			throw new IllegalStateException(FNC_NAME + ": Input Source not found");
		}
		RtspInputSource rtspInputSource = rtspSessionInfo.inputSourceObjPerMtMap.get(RtspProtoMessageType.DESCRIBE);

		if (! checkStreamsForInputSource(FNC_NAME, rtspInputSource)) {
			sendResponseNack(RtspProtoStatusCode.BAD_REQUEST);
			return;
		}

		List<String> contents = new ArrayList<>();
		String des = buildResponseDescribe(rtspInputSource);
		contents.add(des);
		internalSendResponse(contents);
		logDebug(FNC_NAME, "Sent response '" + RtspProtoStatusCode.OK +
				"' to Client (CSeq=" + rtspSessionInfo.rtspClientSeqNrResponse + ")\n");
	}

	/**
	 * The client makes one SETUP request per Stream Source (aka Sub-Stream).<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC-7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC-2326: Real Time Streaming Protocol 1.0</a>
	 */
	private void sendResponseSetup(RequestBasicInfo.@NonNull RequestUrlInputOrStreamSource requestUrlInputOrStreamSource)
			throws TcpSocketIoException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseSetup()";

		Objects.requireNonNull(
				requestUrlInputOrStreamSource.subStreamId,
				FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null"
			);
		RtspStaticSessionInfo.StreamInfo tmpStreamInfo = RtspStaticSessionInfo.getStreamInfoOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.subStreamId
			);
		try {
			tmpStreamInfo.isTransportValid(
					rtspSessionInfo.isRtpRtcpEncryptionRequired,
					rtspSessionInfo.forceRtpRtcpEncryption,
					rtspSessionInfo.isRtspsConnection,
					rtspConfig.getIsDebugDisableTransportUdp()
				);
		} catch (Exception e) {
			// this should never happen
			throw new IllegalStateException(FNC_NAME + ": Transport unsupported: " + e.getMessage());
		}

		// generate RTSP Session ID
		if (rtspSessionInfo.rtspSessionId.isBlank()) {
			rtspSessionInfo.rtspSessionId = buildHexString(RandomHelper.getRandomUint32(false));
			logDebug(FNC_NAME, "New RTSP session ID: " + rtspSessionInfo.rtspSessionId);
		}

		//
		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_SESSION + " " + rtspSessionInfo.rtspSessionId + ";" +
				RTSP_RR_HEADER_PARAM_KEY_SET_TIMEOUT + RtspConstants.RTSP_SESSION_TIMEOUT);

		// we need to open the sockets now so we can get the port numbers
		findAndOpenUdpSocketPorts(tmpStreamInfo);

		/*
		 * Transport:
		 *   RTP/AVP;unicast;destination=10.55.0.5;source=192.168.5.20;client_port=38852-38853;server_port=6970-6971;ssrc=DEADBEEF
		 * See https://datatracker.ietf.org/doc/html/rfc7826#section-13.3
		 */
		Objects.requireNonNull(rtspSessionInfo.clientIpAddr);
		Objects.requireNonNull(tmpStreamInfo.tpServerSrcUdpSocketRtp);
		Objects.requireNonNull(tmpStreamInfo.tpServerUdpSocketRtcp);
		String tmpRtspHostIp = findRtspHostIp(RtspProtoMessageType.SETUP);
		String tmpLine = RTSP_RR_HEADER_TOKEN_SET_TRANSPORT + " ";
		if (tmpStreamInfo.tpIsUdp) {
			tmpLine += (tmpStreamInfo.tpIsEncr ? RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP2 : RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP2);
		} else {
			tmpLine += (tmpStreamInfo.tpIsEncr ? RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPTCP : RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP);
		}
		tmpLine += ";" +
				RTSP_RR_HEADER_PARAM_VAL_SET_TP_UNICAST + ";" +
				RTSP_RR_HEADER_PARAM_KEY_SET_TP_DESTIP + rtspSessionInfo.clientIpAddr.getHostAddress() + ";" +
				RTSP_RR_HEADER_PARAM_KEY_SET_TP_SOURCEIP + tmpRtspHostIp + ";";
		if (tmpStreamInfo.tpIsUdp) {
			tmpLine += RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT +
					Integer.toUnsignedString(tmpStreamInfo.tpClientDestUdpPortRtp) + "-" +
					Integer.toUnsignedString(tmpStreamInfo.tpClientDestUdpPortRtcp) + ";" +
				RTSP_RR_HEADER_PARAM_KEY_SET_TP_SERVERPORT +
					Integer.toUnsignedString(tmpStreamInfo.tpServerSrcUdpSocketRtp.getLocalPort()) + "-" +
					Integer.toUnsignedString(tmpStreamInfo.tpServerUdpSocketRtcp.getLocalPort());
		} else {
			tmpLine += RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED + tmpStreamInfo.tpClientDestTcpChannRtp + "-" +
					tmpStreamInfo.tpClientDestTcpChannRtcp;
		}
		if (rtspSessionInfo.lastRequestRtspProtoVersion.equals(RTSP_RR_CMD_PROTOCOL_VERSION_2)) {
			tmpLine += ";" + RTSP_RR_HEADER_PARAM_KEY_SET_TP_SSRC + buildHexString(tmpStreamInfo.rtspSsrcId);  // only valid for unicast transmission
		}
		contents.add(tmpLine);
		contents.add("");
		internalSendResponse(contents);
		logDebug(FNC_NAME, "Sent response '" + RtspProtoStatusCode.OK +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspClientSeqNrResponse + ")\n");
	}

	private void sendResponsePlay() throws TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponsePlay()";

		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_SESSION + " " + rtspSessionInfo.rtspSessionId);
		contents.add(RTSP_RR_HEADER_TOKEN_PLA_RANGE + " " + rtspSessionInfo.clientPlaybackRangeValue);

		StringBuilder tmpRtpInfoSb = new StringBuilder();
		for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
			RtspStaticSessionInfo.StreamInfo tmpStreamInfo = RtspStaticSessionInfo.getStreamInfoOrThrow(FNC_NAME, tmpSubStreamId);
			if (! tmpRtpInfoSb.isEmpty()) {
				tmpRtpInfoSb.append(",");
			}
			tmpRtpInfoSb
					.append(RTSP_RR_HEADER_PARAM_KEY_PLA_RI_URL).append(tmpStreamInfo.inputSourceUrlSetup)
					.append(";")
					.append(RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SEQ).append(Integer.toUnsignedString(tmpStreamInfo.rtspRtpSeqNrT0))
					.append(";")
					.append(RTSP_RR_HEADER_PARAM_KEY_PLA_RI_RTPTIME).append(Integer.toUnsignedString(tmpStreamInfo.rtspRtpTimestampT0));
		}
		contents.add(RTSP_RR_HEADER_TOKEN_PLA_RTPINFO + " " + tmpRtpInfoSb);
		contents.add("");
		internalSendResponse(contents);
		logDebug(FNC_NAME, "Sent response '" + RtspProtoStatusCode.OK +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspClientSeqNrResponse + ")\n");
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHexString(int value) {
		return String.format("%08X", value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String findRtspHostIp(RtspProtoMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".findRtspHostIp()";

		String tmpRtspHostname;
		try {
			URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(rtspSessionInfo.inputSourceUrlPerMtMap.get(messageType));
			tmpRtspHostname = rscUriObj.getHost();
		} catch (RtspInvalidUriException e) {
			// this should never happen
			throw new RuntimeException(e);
		}
		if (tmpRtspHostname.isBlank()) {
			throw new IllegalStateException(FNC_NAME + ": Could not determine RTSP hostname");
		}
		try {
			Optional<InetAddress> optRtspHostIp = HostnameHelper.firstAvailableLocalIpv4AddressForHostname(tmpRtspHostname, true);
			if (optRtspHostIp.isEmpty()) {
				throw new IllegalStateException(FNC_NAME + ": Could not determine IPv4 address for RTSP hostname '" + tmpRtspHostname + "'");
			}
			return optRtspHostIp.get().getHostAddress();
		} catch (UnknownHostException | SocketException e) {
			throw new IllegalStateException(FNC_NAME + ": Unknown RTSP hostname '" + tmpRtspHostname + "'");
		}
	}

	/**
	 * Find and open UDP sockets for RTP and RTCP in accordance with RFC-3551 Section 8
	 */
	private void findAndOpenUdpSocketPorts(RtspStaticSessionInfo.StreamInfo tmpStreamInfo) throws UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".findAndOpenUdpSocketPorts()";

		int loopCnt = 0;
		boolean isOk = false;
		while (++loopCnt <= 1000) {
			if (tmpStreamInfo.tpServerSrcUdpSocketRtp != null) {
				tmpStreamInfo.tpServerSrcUdpSocketRtp.close();
			}
			if (tmpStreamInfo.tpServerUdpSocketRtcp != null) {
				tmpStreamInfo.tpServerUdpSocketRtcp.close();
			}
			try {
				tmpStreamInfo.tpServerSrcUdpSocketRtp = new DatagramSocket();
				if (tmpStreamInfo.tpServerSrcUdpSocketRtp.getLocalPort() % 2 != 0) {
					continue;
				}
				tmpStreamInfo.tpServerUdpSocketRtcp = new DatagramSocket(tmpStreamInfo.tpServerSrcUdpSocketRtp.getLocalPort() + 1);
				isOk = true;
				break;
			} catch (SocketException e) {
				// keep going until we find a free port pair
			}
		}
		if (! isOk) {
			throw new IllegalStateException(FNC_NAME + ": Could not find proper UDP sockets");
		}
		try {
			tmpStreamInfo.tpServerSrcUdpSocketRtp.setSoTimeout(SOCKET_UDP_RTP_TIMEOUT_MS);
			tmpStreamInfo.tpServerSrcUdpSocketRtp.setSendBufferSize(1024 * 1024);  // this is only a hint, not the actual buffer size
			tmpStreamInfo.tpServerUdpSocketRtcp.setSoTimeout(SOCKET_UDP_RTCP_TIMEOUT_MS);
			tmpStreamInfo.tpServerUdpSocketRtcp.setSendBufferSize(1024 * 64);  // this is only a hint, not the actual buffer size
		} catch (SocketException e) {
			throw new UdpSocketIoException(FNC_NAME + ": Could not configure UDP sockets: " + e.getMessage());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private boolean checkStreamsForInputSource(String fncName, RtspInputSource rtspInputSource) {
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

	/**
	 * Builds a DESCRIBE response string for the current media<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC-7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC-2326: Real Time Streaming Protocol 1.0</a>
	 * @return Response string
	 */
	private @NonNull String buildResponseDescribe(RtspInputSource rtspInputSource) {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponseDescribe()";

		SdpBuilder sdpBuilder = new SdpBuilder(rtspConfig, rtspSessionInfo);
		final String body = sdpBuilder.buildSdp(
				rtspInputSource,
				findRtspHostIp(RtspProtoMessageType.DESCRIBE)
			);

		if (rtspConfig.getIsDebugPrintRtspSdpSent()) {
			logDebug(FNC_NAME, "SDP: '" + body + "'");
		}

		//
		StringWriter sw = new StringWriter();
		String tmpUrlBase = rtspSessionInfo.inputSourceUrlPerMtMap.get(RtspProtoMessageType.DESCRIBE);
		sw.write(String.format("%s %s/%s", RTSP_RR_HEADER_TOKEN_DES_CONTBASE, tmpUrlBase, CRLF));
		sw.write(String.format("%s %s%s", RTSP_RR_HEADER_TOKEN_XXX_CONTTYPE, RTSP_RR_HEADER_PARAM_VAL_XXX_CT_SDP, CRLF));
		sw.write(String.format("%s %d%s", RTSP_RR_HEADER_TOKEN_XXX_CONTLEN, (body.length() + CRLF.length()), CRLF));
		sw.write(CRLF);
		sw.write(body);

		return sw.toString();
	}

	private void internalSendResponse(final List<String> contents) throws TcpSocketIoException {
		internalSendResponse(RtspProtoStatusCode.OK, contents);
	}

	private void internalSendResponse(RtspProtoStatusCode errorCode, final List<String> contents)
			throws TcpSocketIoException {
		List<String> outputLines = new ArrayList<>();

		outputLines.add(rtspSessionInfo.lastRequestRtspProtoVersion + " " +
				errorCode.getValue() + " " + errorCode.getReasonPhrase() + CRLF);
		//
		outputLines.add(RTSP_RR_HEADER_TOKEN_XXX_CSEQ + " " + rtspSessionInfo.rtspClientSeqNrResponse + CRLF);
		outputLines.add(RTSP_RR_HEADER_TOKEN_XXX_DATE + " " + buildDateString() + CRLF);
		//
		for (String entry : contents) {
			outputLines.add(entry + CRLF);
		}
		rtxpTcpReadWriteInterface.writeRtspLines(outputLines);
	}

	private void addAuthInfoToResponse(@NonNull List<@NonNull String> contents) {
		if (rtspSessionInfo.authInfo.authNonceServer.isBlank()) {
			Objects.requireNonNull(rtspSessionInfo.clientIpAddr, "rtspSessionInfo.clientIpAddr is null");
			rtspSessionInfo.authInfo.authNonceServer = RtspStaticSessionInfo.addAuthServerNonce(rtspSessionInfo.clientIpAddr);
		}
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_WWWAUTH + " " + RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX +
				RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM + "\"" + RTSP_AUTH_REALM + "\", " +
				RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE + "\"" + rtspSessionInfo.authInfo.authNonceServer + "\", " +
				RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO + "\"" + RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_ALGO_MD5 + "\"");
	}

}

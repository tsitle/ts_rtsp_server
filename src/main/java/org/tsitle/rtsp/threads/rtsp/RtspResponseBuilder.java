package org.tsitle.rtsp.threads.rtsp;

import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.RtspInvalidUriException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.helpers.RandomHelper;

import java.io.StringWriter;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

import static org.tsitle.rtsp.threads.rtsp.RtspPrivateConstants.*;

public class RtspResponseBuilder {

	private static final int SOCKET_UDP_RTP_TIMEOUT_MS = 50;
	private static final int SOCKET_UDP_RTCP_TIMEOUT_MS = 2;

	private static final String CRLF = "\r\n";

	private static final String SERVER_NAME = "TSITLE_RTSP Server";
	private static final String SDP_ENCODER_NAME = "TSITLE_RTSP 1.0";  // @TODO make at least version dynamic?
	private static final String SESSION_NAME = "Just A Session";

	private final RtspConfig rtspConfig;
	private final RtspSessionInfo rtspSessionInfo;

	private Consumer<List<String>> cbWriteDataLines = null;

	public RtspResponseBuilder(RtspConfig rtspConfig, RtspSessionInfo rtspSessionInfo) {
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public void setCbWriteDataLines(Consumer<List<String>> cbWriteDataLines) {
		this.cbWriteDataLines = cbWriteDataLines;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public void sendResponse(RequestBasicInfo requestBasicInfo) throws SocketException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponse()";

		if (requestBasicInfo.statusCode != ServerResponseStatusCode.OK) {
			sendResponseNack(requestBasicInfo.statusCode);
			return;
		}
		switch (requestBasicInfo.serverMessageType) {
			case OPTIONS:
				sendResponseOptions();
				break;
			case DESCRIBE:
				sendResponseDescribe();
				break;
			case SETUP:
				sendResponseSetup(requestBasicInfo.requestUrlInputOrStreamSource);
				break;
			case PLAY:
				sendResponsePlay();
				break;
			case PAUSE, TEARDOWN:
				sendResponseAck();
				break;
			default:
				throw new IllegalStateException(FNC_NAME + ": Unsupported server message type: " +
						requestBasicInfo.serverMessageType);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void sendResponseNack(ServerResponseStatusCode statusCode) {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseNack()";

		List<String> contents = new ArrayList<>();
		contents.add("");
		internalSendResponse(statusCode, contents);
		debugPrintMsg(FNC_NAME, "Sent response '" + statusCode +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspSeqNr + ")\n");
	}

	private void sendResponseAck() {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseAck()";

		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_SESSION + " " + rtspSessionInfo.rtspSessionId);
		contents.add("");
		internalSendResponse(contents);
		debugPrintMsg(FNC_NAME, "Sent response '" + ServerResponseStatusCode.OK +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspSeqNr + ")\n");
	}

	private void sendResponseOptions() {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseOptions()";

		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_SERVER + " " + SERVER_NAME);
		List<String> tmpList = Arrays.stream(ServerMessageType.values())
				.filter(tmpType -> tmpType != ServerMessageType.UNKNOWN)
				.map(Enum::name)
				.toList();
		contents.add(RTSP_RR_HEADER_TOKEN_OPT_PUBLIC + " " + String.join(", ", tmpList));
		contents.add("");
		internalSendResponse(contents);
		debugPrintMsg(FNC_NAME, "Sent response '" + ServerResponseStatusCode.OK +
				"' to Client (<" +
				(rtspSessionInfo.rtspSessionId.isEmpty() ? "-" : rtspSessionInfo.rtspSessionId) +
				">, CSeq=" + rtspSessionInfo.rtspSeqNr + ")\n");
	}

	private void sendResponseDescribe() {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseDescribe()";

		RtspInputSource rtspInputSource = rtspSessionInfo.inputSourceObjPerSmtMap.getOrDefault(
				ServerMessageType.DESCRIBE,
				null
			);
		if (rtspInputSource == null) {
			throw new IllegalStateException(FNC_NAME + ": Input Source not found");
		}

		List<String> contents = new ArrayList<>();
		String des = buildResponseDescribe(rtspInputSource);
		contents.add(des);
		internalSendResponse(contents);
		debugPrintMsg(FNC_NAME, "Sent response '" + ServerResponseStatusCode.OK +
				"' to Client (CSeq=" + rtspSessionInfo.rtspSeqNr + ")\n");
	}

	/**
	 * The client makes one SETUP request per stream.<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC2326: Real Time Streaming Protocol 1.0</a>
	 */
	private void sendResponseSetup(RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource)
			throws SocketException {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponseSetup()";

		RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.getStreamInfoOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.streamSourceId
			);
		if (! tmpStreamInfo.isTransportValid()) {
			throw new IllegalStateException(FNC_NAME + ": Transport unsupported");
		}

		// generate RTSP Session ID
		if (rtspSessionInfo.rtspSessionId.isBlank()) {
			rtspSessionInfo.rtspSessionId = buildHexString(RandomHelper.getRandomUint32());
			System.out.println(FNC_NAME + ": New RTSP session ID: " + rtspSessionInfo.rtspSessionId);
		}

		//
		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_SESSION + " " + rtspSessionInfo.rtspSessionId + ";" +
				RTSP_RR_HEADER_VALUE_SET_TIMEOUT + RtspConstants.RTSP_SESSION_TIMEOUT);

		// we need to open the sockets now so we can get the port numbers
		findAndOpenUdpSocketPorts(tmpStreamInfo);

		/*
		 * Transport:
		 *   RTP/AVP;unicast;destination=10.55.0.5;source=192.168.5.20;client_port=38852-38853;server_port=6970-6971;ssrc=DEADBEEF
		 * See https://datatracker.ietf.org/doc/html/rfc7826#section-13.3
		 */
		String tmpRtspHostIp = findRtspHostIp(ServerMessageType.SETUP);
		String tmpLine = RTSP_RR_HEADER_TOKEN_SET_TRANSPORT + " " +
				RTSP_RR_HEADER_VALUE_SET_TP_RTPAVPUDP + ";" +
				RTSP_RR_HEADER_VALUE_SET_TP_UNICAST + ";" +
				RTSP_RR_HEADER_VALUE_SET_TP_DESTIP + rtspSessionInfo.clientIpAddr.getHostAddress() + ";" +
				RTSP_RR_HEADER_VALUE_SET_TP_SOURCEIP + tmpRtspHostIp + ";" +
				RTSP_RR_HEADER_VALUE_SET_TP_CLIENTPORT +
					Integer.toUnsignedString(tmpStreamInfo.tpClientDestPortRtp) + "-" +
					Integer.toUnsignedString(tmpStreamInfo.tpClientDestPortRtcp) + ";" +
				RTSP_RR_HEADER_VALUE_SET_TP_SERVERPORT +
					Integer.toUnsignedString(tmpStreamInfo.tpServerSrcSocketRtp.getLocalPort()) + "-" +
					Integer.toUnsignedString(tmpStreamInfo.tpServerSocketRtcp.getLocalPort()) +
				(rtspSessionInfo.lastRequestRtspProtoVersion.equals(RTSP_RR_CMD_PROTOCOL_VERSION_2) ?
							";" + RTSP_RR_HEADER_VALUE_SET_TP_SSRC + buildHexString(tmpStreamInfo.rtspSsrcId)  // only valid for unicast transmission
							: ""
						);
		contents.add(tmpLine);
		contents.add("");
		internalSendResponse(contents);
		debugPrintMsg(FNC_NAME, "Sent response '" + ServerResponseStatusCode.OK +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspSeqNr + ")\n");
	}

	private void sendResponsePlay() {
		final String FNC_NAME = getClass().getSimpleName() + ".sendResponsePlay()";

		List<String> contents = new ArrayList<>();
		contents.add(RTSP_RR_HEADER_TOKEN_XXX_SESSION + " " + rtspSessionInfo.rtspSessionId);
		contents.add(RTSP_RR_HEADER_TOKEN_PLA_RANGE + " " + rtspSessionInfo.clientPlaybackRangeValue);

		StringBuilder tmpRtpInfoSb = new StringBuilder();
		Set<Integer> tmpSsIds =
				rtspSessionInfo.getInputSourceForSmtOrThrow(FNC_NAME, ServerMessageType.PLAY).getStreamSourceIds();
		for (int tmpSsId : tmpSsIds) {
			RtspStreamSource tmpSsObj = rtspConfig.getStreamSourceObj(tmpSsId).orElseThrow();
			RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.getStreamInfoOrThrow(FNC_NAME, tmpSsObj.getId());
			if (! tmpRtpInfoSb.isEmpty()) {
				tmpRtpInfoSb.append(",");
			}
			tmpRtpInfoSb
					.append(RTSP_RR_HEADER_VALUE_PLA_RI_URL).append(tmpStreamInfo.inputSourceUrlSetup)
					.append(";")
					.append(RTSP_RR_HEADER_VALUE_PLA_RI_SEQ).append(Integer.toUnsignedString(tmpStreamInfo.rtspRtpSeqNrT0))
					.append(";")
					.append(RTSP_RR_HEADER_VALUE_PLA_RI_RTPTIME).append(Integer.toUnsignedString(tmpStreamInfo.rtspRtpTimestampT0));
		}
		contents.add(RTSP_RR_HEADER_TOKEN_PLA_RTPINFO + " " + tmpRtpInfoSb.toString());
		contents.add("");
		internalSendResponse(contents);
		debugPrintMsg(FNC_NAME, "Sent response '" + ServerResponseStatusCode.OK +
				"' to Client (<" + rtspSessionInfo.rtspSessionId + ">, CSeq=" + rtspSessionInfo.rtspSeqNr + ")\n");
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void debugPrintMsg(String fncName, String msg) {
		System.out.println(fncName + ": RTSP Server - " + msg);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static String buildHexString(int value) {
		return String.format("%08X", value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private String findRtspHostIp(ServerMessageType serverMessageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".findRtspHostIp()";

		String tmpRtspHostname;
		try {
			URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(rtspSessionInfo.inputSourceUrlPerSmtMap.get(serverMessageType));
			tmpRtspHostname = rscUriObj.getHost();
		} catch (RtspInvalidUriException e) {
			// this should never happen
			throw new RuntimeException(e);
		}
		if (tmpRtspHostname.isBlank()) {
			throw new IllegalStateException(FNC_NAME + ": Could not determine RTSP hostname");
		}
		try {
			Optional<InetAddress> optRtspHostIp = HostnameHelper.firstAvailableLocalIpv4AddressForHostname(tmpRtspHostname);
			if (optRtspHostIp.isEmpty()) {
				throw new IllegalStateException(FNC_NAME + ": Could not determine IPv4 address for RTSP hostname '" + tmpRtspHostname + "'");
			}
			return optRtspHostIp.get().getHostAddress();
		} catch (UnknownHostException | SocketException e) {
			throw new IllegalStateException(FNC_NAME + ": Unknown RTSP hostname '" + tmpRtspHostname + "'");
		}
	}

	/**
	 * Find and open UDP sockets for RTP and RTCP in accordance with RFC3551 Section 8
	 */
	private void findAndOpenUdpSocketPorts(RtspSessionInfo.StreamInfo tmpStreamInfo) throws SocketException {
		final String FNC_NAME = getClass().getSimpleName() + ".findAndOpenUdpSocketPorts()";

		int loopCnt = 0;
		boolean isOk = false;
		while (++loopCnt <= 1000) {
			if (tmpStreamInfo.tpServerSrcSocketRtp != null) {
				tmpStreamInfo.tpServerSrcSocketRtp.close();
			}
			if (tmpStreamInfo.tpServerSocketRtcp != null) {
				tmpStreamInfo.tpServerSocketRtcp.close();
			}
			try {
				tmpStreamInfo.tpServerSrcSocketRtp = new DatagramSocket();
				if (tmpStreamInfo.tpServerSrcSocketRtp.getLocalPort() % 2 != 0) {
					continue;
				}
				tmpStreamInfo.tpServerSocketRtcp = new DatagramSocket(tmpStreamInfo.tpServerSrcSocketRtp.getLocalPort() + 1);
				isOk = true;
				break;
			} catch (SocketException e) {
				// keep going until we find a free port pair
			}
		}
		if (! isOk) {
			throw new IllegalStateException(FNC_NAME + ": Could not find proper UDP sockets");
		}
		tmpStreamInfo.tpServerSrcSocketRtp.setSoTimeout(SOCKET_UDP_RTP_TIMEOUT_MS);
		tmpStreamInfo.tpServerSocketRtcp.setSoTimeout(SOCKET_UDP_RTCP_TIMEOUT_MS);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void checkCallbackFncs() {
		final String FNC_NAME = getClass().getSimpleName() + ".checkCallbackFncs()";

		if (cbWriteDataLines == null) {
			throw new IllegalStateException(FNC_NAME + ": Callback functions not set");
		}
	}

	private void buildResponseDescribe_sdp_stream(
				RtspInputSource rtspInputSource,
				boolean useVideo,
				StringWriter sw
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponseDescribe_sdp_stream()";

		Optional<RtspStreamSource> optSsObj =
				rtspConfig.getInputSourcesFirstOfKindStreamSourceObj(rtspInputSource.getId(), useVideo);
		if (optSsObj.isEmpty()) {
			return;
		}
		RtspStreamSource tmpSsObj = optSsObj.get();
		if (! RTSP_SDP_TAG_A_CODEC_MAPPING.containsKey(tmpSsObj.getCodec())) {
			throw new IllegalStateException(FNC_NAME + ": Unsupported codec: " + tmpSsObj.getCodec());
		}
		// m: Media Description with available codec(s)
		final int tmpM_port = 0;
		sw.write(String.format("m=%s %d RTP/AVP %d%s",
				(useVideo ? "video" : "audio"), tmpM_port, tmpSsObj.getCodec().getValue(), CRLF));
		// c: Connection Information (can be an IP address or a hostname)
		sw.write(String.format("c=IN IP4 0.0.0.0%s", CRLF));
		//
		if (! useVideo) {
			if (tmpSsObj.getCodec().getAudioBitsPerSample().isPresent()) {
				// b: Bandwidth Information
				sw.write(String.format("b=AS:%d%s",
						tmpSsObj.getAudioChannelCount() * tmpSsObj.getAudioSampleRateHz() *
								tmpSsObj.getCodec().getAudioBitsPerSample().get(),
						CRLF));
			}
			/*
			 * a: Session Attribute: Packetization interval (in milliseconds)
			 *    Length of time in milliseconds represented by the media in a packet.
			 *    This is probably only meaningful for audio data. It should not be necessary
			 *    to know ptime to decode RTP or vat audio, and it is intended
			 *    as a recommendation for the encoding/packetisation of audio.
			 */
			sw.write(String.format("a=ptime:%d%s", RtspConstants.RTP_SEND_INTERVAL_AUDIO_MS, CRLF));
		}
		// a: Session Attribute: map the codec number from the 'm' attribute to an actual codec and its clock rate
		final String tmpA_Map = RTSP_SDP_TAG_A_CODEC_MAPPING.get(tmpSsObj.getCodec()) +
				"/" +
				(useVideo ?
						RtspConstants.RTP_CODEC_CLOCKRATE_MAPPING.get(tmpSsObj.getCodec()) :
						tmpSsObj.getAudioSampleRateHz()) +
				(useVideo ? "" : "/" + tmpSsObj.getAudioChannelCount());
		sw.write(String.format("a=rtpmap:%d %s%s", tmpSsObj.getCodec().getValue(), tmpA_Map, CRLF));
		// a: Session Attribute: URL to be used for controlling that particular media stream (RFC7826 Section D.1.1)
		sw.write(String.format("a=control:%s%02d%s", STREAM_ID_PREFIX, tmpSsObj.getId(), CRLF));
	}

	/**
	 * Builds a SDP response string<br />
	 * SDP: Session Description Protocol (for some examples see
	 * <a href="https://datatracker.ietf.org/doc/html/rfc2327">RFC2327: Session Description Protocol</a> and
	 * <a href="https://datatracker.ietf.org/doc/html/rfc4317">RFC4317: SDP Offer/Answer Examples</a>)
	 * @return SDP formatted string
	 */
	private String buildResponseDescribe_sdp(RtspInputSource rtspInputSource) {
		StringWriter sw = new StringWriter();

		// SDP Specification (RFC2327 Section 6)
		/// v: Protocol Version
		sw.write(String.format("v=0%s", CRLF));
		/// o: Origin
		final String tmpO_Username = "-";
		final String tmpO_Id = "" + System.currentTimeMillis();
		final String tmpO_Version = "1";
		final String tmpO_NetworkType = "IN";
		final String tmpO_AddressType = "IP4";
		final String tmpO_UnicastAddress = findRtspHostIp(ServerMessageType.DESCRIBE);  // can be an IP address or a hostname
		sw.write(String.format("o=%s %s %s %s %s %s%s",
				tmpO_Username, tmpO_Id, tmpO_Version, tmpO_NetworkType, tmpO_AddressType, tmpO_UnicastAddress, CRLF));
		/// s: Session Name
		sw.write(String.format("s=%s%s", SESSION_NAME, CRLF));
		/// i: Session Information
		sw.write(String.format("i=%s%s", rtspInputSource.getId(), CRLF));
		/// t: Time Active
		sw.write(String.format("t=0 0%s", CRLF));
		/// a: Session Attribute: Name and version number of the tool used to create the session description
		sw.write(String.format("a=tool:%s%s", SDP_ENCODER_NAME, CRLF));
		/// a: Session Attribute: Type of the conference
		sw.write(String.format("a=type:broadcast%s", CRLF));
		/// a: Session Attribute: URL to be used for controlling that particular media stream (RFC7826 Section D.1.1)
		sw.write(String.format("a=control:*%s", CRLF));
		/// a: Session Attribute: Range of presentation (RFC7826 Section D.1.6)
		sw.write(String.format("a=range:npt=0-%s", CRLF));

		// optional Video Stream
		buildResponseDescribe_sdp_stream(rtspInputSource, true, sw);
		// optional Audio Stream
		buildResponseDescribe_sdp_stream(rtspInputSource, false, sw);

		return sw.toString().substring(0, sw.toString().length() - CRLF.length());
	}

	/**
	 * Builds a DESCRIBE response string for the current media<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC2326: Real Time Streaming Protocol 1.0</a>
	 * @return Response string
	 */
	private String buildResponseDescribe(RtspInputSource rtspInputSource) {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponseDescribe()";

		final String body = buildResponseDescribe_sdp(rtspInputSource);

		if (rtspConfig.getIsDebugPrintSDP()) {
			System.out.println(FNC_NAME + ": SDP: '" + body + "'");
		}

		//
		StringWriter sw = new StringWriter();
		String tmpUrlBase = rtspSessionInfo.inputSourceUrlPerSmtMap.get(ServerMessageType.DESCRIBE);
		sw.write(String.format("%s %s/%s", RTSP_RR_HEADER_TOKEN_DES_CONTBASE, tmpUrlBase, CRLF));
		sw.write(String.format("%s %s%s", RTSP_RR_HEADER_TOKEN_DES_CONTTYPE, RTSP_RR_HEADER_VALUE_DES_ACCEPT, CRLF));
		sw.write(String.format("%s %d%s", RTSP_RR_HEADER_TOKEN_XXX_CONTLEN, (body.length() + CRLF.length()), CRLF));
		sw.write(CRLF);
		sw.write(body);

		return sw.toString();
	}

	private void internalSendResponse(final List<String> contents) {
		internalSendResponse(ServerResponseStatusCode.OK, contents);
	}

	private void internalSendResponse(ServerResponseStatusCode errorCode, final List<String> contents) {
		checkCallbackFncs();
		//
		List<String> outputLines = new ArrayList<>();
		outputLines.add(rtspSessionInfo.lastRequestRtspProtoVersion + " " +
				errorCode.getValue() + " " + RTSP_RR_SC_MAP_TO_STR.get(errorCode) + CRLF);
		outputLines.add(RTSP_RR_HEADER_TOKEN_XXX_CSEQ + " " + rtspSessionInfo.rtspSeqNr + CRLF);
		for (String entry : contents) {
			outputLines.add(entry + CRLF);
		}
		cbWriteDataLines.accept(outputLines);
	}

}

package org.tsitle.rtsp.threads.rtsp.proto.highlevel.response;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.exceptions.UdpSocketIoException;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.*;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.RtspRequestBasics;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header.*;

import java.net.*;
import java.util.*;

public final class RtspProtoHighResponseBuilder {

	private static final int SOCKET_UDP_RTP_TIMEOUT_MS = 50;
	private static final int SOCKET_UDP_RTCP_TIMEOUT_MS = 2;

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspConfig rtspConfig;
	private final @NonNull RtspSessionInfo rtspSessionInfo;

	public RtspProtoHighResponseBuilder(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspConfig rtspConfig,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspConfig = rtspConfig;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredResponse buildResponse(@NonNull RtspRequestBasics rtspRequestBasics)
			throws RtspInvalidResponseException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse()";

		RtspProtoHighMsgStructuredResponse resObj = new RtspProtoHighMsgStructuredResponse();

		resObj.rtspProtoVersion = rtspSessionInfo.lastRequestRtspProtoVersion;
		resObj.statusCode = rtspRequestBasics.statusCode;
		resObj.messageType = rtspRequestBasics.messageType;

		//
		addCommonHeaders(resObj);

		//
		if (rtspRequestBasics.statusCode != RtspStatusCode.OK) {
			buildResponse_nack(rtspRequestBasics.statusCode, rtspRequestBasics.unsupportedOptionName, resObj);
			return resObj;
		}

		//
		switch (rtspRequestBasics.messageType) {
			case GET_PARAMETER -> buildResponse_getParameter();
			case SET_PARAMETER -> buildResponse_setParameter();
			case OPTIONS -> buildResponse_options(resObj);
			case DESCRIBE -> buildResponse_describe(resObj);
			case SETUP -> buildResponse_setup(rtspRequestBasics.requestUrlInputOrStreamSource, resObj);
			case PLAY -> buildResponse_play(resObj);
			case PAUSE, TEARDOWN -> buildResponse_ack();
			default -> throw new RtspInvalidResponseException(FNC_NAME + ": Unsupported message type: " +
					rtspRequestBasics.messageType);
		}

		//
		if (! resObj.body.isBlank()) {
			if (! resObj.body.endsWith(RtspProtoLowMsgConstants.CRLF)) {
				resObj.body += RtspProtoLowMsgConstants.CRLF;
			}
			addContentLengthHeader(resObj);
		}

		System.out.println(">>>>>>>>> >>>>>>>>> " + resObj);  // @TODO

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildResponse_nack(
				@NonNull RtspStatusCode statusCode,
				@NonNull String unsupportedOptionName,
				@NonNull RtspProtoHighMsgStructuredResponse msg
			) {
		if (statusCode == RtspStatusCode.UNAUTHORIZED) {
			addAuthServerInfo(msg);
		}
		if (statusCode == RtspStatusCode.OPTION_NOT_SUPPORTED) {
			// Unsupported
			{
				RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.UNSUPPORTED);
				hdEntry.hdValUnsupported.unsupportedOptionStr = (unsupportedOptionName.isBlank() ? "_unknown_" : unsupportedOptionName);
				msg.headers.put(hdEntry.getHdKey(), hdEntry);
			}
		}
	}

	private void buildResponse_ack() {
		// nothing to do
	}

	private void buildResponse_getParameter() {
		// nothing to do
	}

	private void buildResponse_setParameter() {
		// nothing to do
	}

	private void buildResponse_options(@NonNull RtspProtoHighMsgStructuredResponse msg) {
		// Public
		{
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.PUBLIC);
			hdEntry.hdValPublic.messageTypes.addAll(RtspProtoConstants.SUPPORTED_MESSAGE_TYPES_SERVER);
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Auth
		if (rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspMessageType.OPTIONS)) {
			boolean tmpNeedAuth = rtspSessionInfo.inputSourceObjPerMtMap.get(RtspMessageType.OPTIONS).getNeedsAuthentication();
			if (tmpNeedAuth) {
				addAuthServerInfo(msg);
			}
		}
	}

	private void buildResponse_describe(@NonNull RtspProtoHighMsgStructuredResponse msg) {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_describe()";

		if (! rtspSessionInfo.inputSourceObjPerMtMap.containsKey(RtspMessageType.DESCRIBE)) {
			throw new IllegalStateException(FNC_NAME + ": Input Source not found");
		}
		RtspInputSource rtspInputSource = rtspSessionInfo.inputSourceObjPerMtMap.get(RtspMessageType.DESCRIBE);

		if (! checkStreamsForInputSource(FNC_NAME, rtspInputSource)) {
			buildResponse_nack(RtspStatusCode.BAD_REQUEST, "", msg);
			return;
		}

		SdpBuilder sdpBuilder = new SdpBuilder(rtspConfig, rtspSessionInfo);
		List<@NonNull String> tmpSdpLines = sdpBuilder.buildSdp(
				rtspInputSource,
				findRtspHostIp(RtspMessageType.DESCRIBE)
			);
		msg.body = String.join(RtspProtoLowMsgConstants.CRLF, tmpSdpLines);

		if (rtspConfig.getIsDebugPrintRtspSdpSent()) {
			logDebug(FNC_NAME, "-------- SDP:");
			for (String tmpSingleSdpLine : tmpSdpLines) {
				logDebug(FNC_NAME, "---------------- " + tmpSingleSdpLine);
			}
		}

		// Content-Base
		{
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.CONTENT_BASE);
			String tmpUrlBase = rtspSessionInfo.inputSourceUrlPerMtMap.get(RtspMessageType.DESCRIBE);
			hdEntry.hdValContBase.contentBaseStr = tmpUrlBase + "/";
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Content-Type
		{
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.CONTENT_TYPE);
			hdEntry.hdValContType.contentType = RtspMimeType.SDP;
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	/**
	 * The client makes one SETUP request per Stream Source (aka Sub-Stream).<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC-7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC-2326: Real Time Streaming Protocol 1.0</a>
	 */
	private void buildResponse_setup(
				RtspRequestBasics.@Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull RtspProtoHighMsgStructuredResponse msg
			) throws RtspInvalidResponseException, UdpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_setup()";

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
			throw new IllegalStateException(FNC_NAME + ": Transport unsupported: " + e.getMessage());
		}

		// Session ID
		{
			// generate RTSP Session ID
			if (rtspSessionInfo.rtspSessionId.isBlank()) {
				rtspSessionInfo.rtspSessionId = buildHexString(RandomHelper.getRandomUint32(false));
				logDebug(FNC_NAME, "New RTSP session ID: " + rtspSessionInfo.rtspSessionId);
			}
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.sessionIdStr = rtspSessionInfo.rtspSessionId;
			hdEntry.hdValSession.timeout = RtspConstants.RTSP_SESSION_TIMEOUT;
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}

		// we need to open the sockets now so we can get the port numbers
		findAndOpenUdpSocketPorts(tmpSetupSubStream);
		Objects.requireNonNull(tmpSetupSubStream.tpServerUdpSocketRtp);
		Objects.requireNonNull(tmpSetupSubStream.tpServerUdpSocketRtcp);

		// Transport
		{
			String tmpRtspHostIp = findRtspHostIp(RtspMessageType.SETUP);

			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.TRANSPORT);
			hdEntry.hdValTransport.tpIsUdp = tmpSetupSubStream.tpIsUdp;
			hdEntry.hdValTransport.tpIsEncr = tmpSetupSubStream.tpIsEncr;
			hdEntry.hdValTransport.tpIsUnicast = tmpSetupSubStream.tpIsUnicast;
			hdEntry.hdValTransport.tpIsInterleaved = tmpSetupSubStream.tpIsInterleaved;
			hdEntry.hdValTransport.tpSourceIpOrHost = tmpRtspHostIp;
			hdEntry.hdValTransport.tpDestIpOrHost = rtspSessionInfo.getClientIpAddr().getHostAddress();
			if (tmpSetupSubStream.tpIsUdp) {
				hdEntry.hdValTransport.setClientUdpPortRtp16bit(tmpSetupSubStream.tpClientUdpPortRtp);
				hdEntry.hdValTransport.setClientUdpPortRtcp16bit(tmpSetupSubStream.tpClientUdpPortRtcp);
				hdEntry.hdValTransport.setServerUdpPortRtp16bit(tmpSetupSubStream.tpServerUdpSocketRtp.getLocalPort());
				hdEntry.hdValTransport.setServerUdpPortRtcp16bit(tmpSetupSubStream.tpServerUdpSocketRtcp.getLocalPort());
			} else {
				hdEntry.hdValTransport.setClientTcpChannRtp16bit(tmpSetupSubStream.tpClientTcpChannRtp);
				hdEntry.hdValTransport.setClientTcpChannRtcp16bit(tmpSetupSubStream.tpClientTcpChannRtcp);
			}
			hdEntry.hdValTransport.setSsrcId32bit(tmpSetupSubStream.rtspSsrcId);
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	/**
	 * The client makes one PLAY request per Input Source
	 */
	private void buildResponse_play(@NonNull RtspProtoHighMsgStructuredResponse msg) {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse_play()";

		// Range
		{
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.RANGE);
			hdEntry.hdValRange.rangeStr = rtspSessionInfo.clientPlaybackRangeValue;
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// RTP-Info
		{
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.RTPINFO);
			int tmpSubStreamNr = 1;
			for (String tmpSubStreamId : rtspSessionInfo.subStreamIdsSetup) {
				RtspStaticSessionInfo.SetupSubStreamInfo tmpSetupSubStreamInp = RtspStaticSessionInfo.getSetupSubStreamOrThrow(
						FNC_NAME,
						tmpSubStreamId
					);
				RtspProtoLowHeaderTypeRtpinfo.SubStream tmpStreamInfoOutput = new RtspProtoLowHeaderTypeRtpinfo.SubStream();
				tmpStreamInfoOutput.urlStr = tmpSetupSubStreamInp.inputSourceUrlSetup;
				tmpStreamInfoOutput.setSeqNr16bit(tmpSetupSubStreamInp.rtspRtpSeqNrT0);
				tmpStreamInfoOutput.setRtpTimestamp32bit(tmpSetupSubStreamInp.rtspRtpTimestampT0);
				tmpStreamInfoOutput.setSsrcId32bit(tmpSetupSubStreamInp.rtspSsrcId);
				if (tmpSubStreamNr == 1) {
					hdEntry.hdValRtpinfo.setSubStream1(tmpStreamInfoOutput);
				} else if (tmpSubStreamNr == 2) {
					hdEntry.hdValRtpinfo.setSubStream2(tmpStreamInfoOutput);
				} else {
					throw new IllegalStateException(FNC_NAME + ": Too many sub-streams");
				}
				++tmpSubStreamNr;
			}
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
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

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String findRtspHostIp(RtspMessageType messageType) {
		final String FNC_NAME = getClass().getSimpleName() + ".findRtspHostIp()";

		String tmpRtspHostname;
		try {
			URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(rtspSessionInfo.inputSourceUrlPerMtMap.get(messageType));
			tmpRtspHostname = rscUriObj.getHost();
		} catch (HostnameHelperInvalidUriException e) {
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

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Find and open UDP sockets for RTP and RTCP in accordance with RFC-3551 Section 8
	 */
	private void findAndOpenUdpSocketPorts(RtspStaticSessionInfo.@NonNull SetupSubStreamInfo setupSubStreamInfo)
			throws UdpSocketIoException {
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
			throw new IllegalStateException(FNC_NAME + ": Could not find proper UDP sockets");
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

	private void addCommonHeaders(@NonNull RtspProtoHighMsgStructuredResponse msg) {
		// CSeq
		if (rtspSessionInfo.rtspClientSeqNrResponse >= 0) {
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.CSEQ);
			hdEntry.hdValCseq.setCseqNr32bit(rtspSessionInfo.rtspClientSeqNrResponse);
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Date
		{
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.DATE);
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Server
		{
			RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.SERVER);
			hdEntry.hdValServer.serverStr = RtspProtoConstants.SERVER_NAME;
			msg.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	private void addAuthServerInfo(@NonNull RtspProtoHighMsgStructuredResponse msg) {
		if (rtspSessionInfo.authInfo.authNonceServer.isBlank()) {
			rtspSessionInfo.authInfo.authNonceServer = RtspStaticSessionInfo.addAuthServerNonce(rtspSessionInfo.getClientIpAddr());
		}

		RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.AUTH_SERVER);
		hdEntry.hdValAuthServer.authAlgo = RtspAuthAlgo.MD5;
		hdEntry.hdValAuthServer.authRealm = RtspProtoConstants.RTSP_AUTH_REALM;
		hdEntry.hdValAuthServer.authNonce = rtspSessionInfo.authInfo.authNonceServer;
		msg.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	private void addContentLengthHeader(@NonNull RtspProtoHighMsgStructuredResponse msg) {
		RtspProtoLowHeaderEntryResponse hdEntry = new RtspProtoLowHeaderEntryResponse(RtspHeaderKey.CONTENT_LEN);
		hdEntry.hdValContLen.setContentLen32bit(msg.body.length());
		msg.headers.put(hdEntry.getHdKey(), hdEntry);
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

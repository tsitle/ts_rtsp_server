package org.tsitle.rtsp.threads.rtsp.proto;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.helpers.RandomHelper;
import org.tsitle.rtsp.security.MikeyParser;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.RtxpTcpReadWrite;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.rtsp.*;

import java.net.*;
import java.util.*;

import static org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants.*;

public final class RtspProtoRequestParser extends RtspProtoParserBase {

	private static final String URL_QUERY_PARAM_SRTP = "srtp";

	private final RtspConfig rtspConfig;

	public RtspProtoRequestParser(
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

	public RequestBasicInfo parseRequest() throws InputStreamNotReadyException, TcpSocketIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseRequest()";

		rtspSessionInfo.authInfo.resetPerRequest();

		// parse request lines and extract the requestType:
		String requestLine;
		try {
			requestLine = readOneLine(true);
			if (rtspConfig.getIsDebugPrintRtspRcvd()) {
				logDebug(FNC_NAME, "-- BEG --------------------------------------------------------------------------");
				logDebug(FNC_NAME, "-------- requestLine: " + requestLine);
			}
		} catch (InputStreamEosException e) {
			logError(FNC_NAME, "EOS reached");
			return RequestBasicInfo.createUnknown();
		}
		if (requestLine.isBlank()) {
			logError(FNC_NAME, "received empty line");
			return RequestBasicInfo.createUnknown();
		}

		// read requestType from the requestLine
		final RtspProtoMessageType requestType = parseMessageType(requestLine);
		if (requestType == RtspProtoMessageType.UNKNOWN) {
			logError(FNC_NAME, "Unknown request type in requestLine '" + requestLine + "'");
			return RequestBasicInfo.createUnsupportedMethod();
		}

		//
		RtspProtoStatusCode respStatusCode = RtspProtoStatusCode.OK;

		// read resource URL from the requestLine
		Optional<String> optResourceUrl = parseServerMessageResourceUrl(requestLine);
		if (optResourceUrl.isEmpty()) {
			respStatusCode = RtspProtoStatusCode.BAD_REQUEST;
		}
		final String resourceUrl;
		if (respStatusCode == RtspProtoStatusCode.OK) {
			resourceUrl = optResourceUrl.get();
			if (resourceUrl.length() > RTSP_MAX_RESOURCE_URL_LENGTH) {
				logError(FNC_NAME, String.format("Resource URL too long (is=%d, max=%d), rejecting request",
						resourceUrl.length(), RTSP_MAX_RESOURCE_URL_LENGTH));
				respStatusCode = RtspProtoStatusCode.URI_TOO_LONG;
			}
		} else {
			resourceUrl = "-none-";
		}

		// handle resource URL
		RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource = null;
		if (respStatusCode == RtspProtoStatusCode.OK) {
			try {
				requestUrlInputOrStreamSource = handleResourceUrl(requestType, resourceUrl);
			} catch (RtspInvalidUriException e) {
				logError(FNC_NAME, "Invalid Resource URL '" + resourceUrl + "' (" + e.getMessage() +
						"), rejecting request");
				respStatusCode = RtspProtoStatusCode.FORBIDDEN;
			} catch (RtspInputSourceIdNotFoundException e) {
				logError(FNC_NAME, "Invalid Input Source ID in Resource URL '" + resourceUrl + "' (" + e.getMessage() +
						"), rejecting request");
				respStatusCode = RtspProtoStatusCode.NOT_FOUND;
			} catch (RtspSubStreamIdNotFoundException e) {
				logError(FNC_NAME, "Invalid Sub-Stream ID in Resource URL '" + resourceUrl + "' (" + e.getMessage() +
						"), rejecting request");
				respStatusCode = RtspProtoStatusCode.NOT_FOUND;
			}
		}

		// parse header lines
		String headerLine = "";
		int timeoutCnt = 0;
		do {
			try {
				headerLine = readOneLine(false);
				timeoutCnt = 0;
				if (rtspConfig.getIsDebugPrintRtspRcvd()) {
					logDebug(FNC_NAME, "---------------- headerLine: " + headerLine);
				}
				parseHeaderLineRequest(requestType, requestUrlInputOrStreamSource, headerLine);
			} catch (InputStreamNotReadyException | InputStreamEosException e) {
				if (timeoutCnt++ > 10) {
					break;
				}
				try {
					//noinspection BusyWait
					Thread.sleep(50);
				} catch (InterruptedException e2) {
					Thread.currentThread().interrupt();  // restore flag
					break;
				}
				headerLine = "xxx";  // keep the loop going
			} catch (RtspInvalidRequestException e) {
				logError(FNC_NAME, "InvalidRtspRequestException: " + e.getMessage());
				respStatusCode = RtspProtoStatusCode.BAD_REQUEST;
			} catch (RtspInvalidSessionIdException e) {
				logError(FNC_NAME, "Invalid Session ID, rejecting request (URL='" + resourceUrl + "')");
				respStatusCode = RtspProtoStatusCode.SESSION_NOT_FOUND;
			} catch (RtspUnsupportedAcceptTypeException e) {
				logError(FNC_NAME, "Unsupported Accept Type, rejecting request (URL='" + resourceUrl + "')");
				respStatusCode = RtspProtoStatusCode.BAD_REQUEST;
			} catch (RtspUnsupportedTransportException e) {
				logError(FNC_NAME, "Unsupported Transport, rejecting request (URL='" + resourceUrl + "')");
				respStatusCode = RtspProtoStatusCode.UNSUPPORTED_TRANSPORT;
			} catch (RtspMissingEncryptionParamsException e) {
				logError(FNC_NAME, "Missing encryption parameters, rejecting request (URL='" + resourceUrl + "')");
				respStatusCode = RtspProtoStatusCode.BAD_REQUEST;
			} catch (RtspMissingAuthParamsException e) {
				logError(FNC_NAME, "Missing authentication parameters, rejecting request (URL='" + resourceUrl + "')");
				respStatusCode = RtspProtoStatusCode.BAD_REQUEST;
			}
		} while (! headerLine.isBlank());

		if (respStatusCode != RtspProtoStatusCode.OK) {
			return RequestBasicInfo.createKnownWithError(requestType, respStatusCode);
		}
		//
		if (requestType == RtspProtoMessageType.SETUP) {
			Objects.requireNonNull(
					requestUrlInputOrStreamSource.subStreamId,
					FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null"
				);
			if (! rtspSessionInfo.subStreamIdsSetup.contains(requestUrlInputOrStreamSource.subStreamId)) {
				rtspSessionInfo.subStreamIdsSetup.add(requestUrlInputOrStreamSource.subStreamId);
			}
		}
		//
		return RequestBasicInfo.createOk(requestType, requestUrlInputOrStreamSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private RtspProtoMessageType parseMessageType(String requestLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMessageType()";

		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			String requestTypeStr = tokens.nextToken();
			//
			RtspProtoMessageType resEn = SUPPORTED_MESSAGE_TYPES_SERVER.stream()
					.filter(tmpType -> tmpType != RtspProtoMessageType.UNKNOWN)
					.filter(tmpType -> tmpType.name().equals(requestTypeStr))
					.findFirst()
					.orElse(RtspProtoMessageType.UNKNOWN);
			if (resEn != RtspProtoMessageType.UNKNOWN) {
				tokens.nextToken();  // URL
				// we shall be tolerant here and ignore a missing PROTOCOL/VERSION token
				if (tokens.hasMoreTokens()) {
					String proto = tokens.nextToken();
					if (! (proto.equals(RTSP_RR_CMD_PROTOCOL_VERSION_1) || proto.equals(RTSP_RR_CMD_PROTOCOL_VERSION_2))) {
						rtspSessionInfo.lastRequestRtspProtoVersion = "-";
						resEn = RtspProtoMessageType.UNKNOWN;
						logError(FNC_NAME, "invalid protocol/version '" + proto + "'");
					} else {
						rtspSessionInfo.lastRequestRtspProtoVersion = proto;
					}
				}
			}
			return resEn;
		} catch (NoSuchElementException e) {
			logError(FNC_NAME, "NoSuchElementException caught: " + e);
			return RtspProtoMessageType.UNKNOWN;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private Optional<String> parseServerMessageResourceUrl(String requestLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseServerMessageResourceUrl()";

		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			tokens.nextToken();  // requestType
			String resS = tokens.nextToken();
			if (rtspSessionInfo.isRtspsConnection && ! resS.startsWith(RTSPS_URL_PROTOCOL + "://")) {
				logError(FNC_NAME, "invalid URL for RTSPS '" + resS + "'");
				return Optional.empty();
			}
			if (! rtspSessionInfo.isRtspsConnection && ! resS.startsWith(RTSP_URL_PROTOCOL + "://")) {
				logError(FNC_NAME, "invalid URL for RTSP '" + resS + "'");
				return Optional.empty();
			}
			// rewrite the URL to get rid of any query parameters or fragments or userinfo (username + password)
			URI tmpUri = URI.create(resS);
			if (tmpUri.getUserInfo() != null && tmpUri.getUserInfo().split(":").length == 2) {
				rtspSessionInfo.authInfo.authUser = tmpUri.getUserInfo().split(":")[0];
				rtspSessionInfo.authInfo.authPlainPassword = tmpUri.getUserInfo().split(":")[1];
			}
			int tmpPort = tmpUri.getPort();
			resS = (rtspSessionInfo.isRtspsConnection ? RTSPS_URL_PROTOCOL : RTSP_URL_PROTOCOL) +
					"://" + tmpUri.getHost() +
					(tmpPort != -1 ? ":" + tmpUri.getPort() : "") + tmpUri.getPath();
			if (tmpUri.getQuery() != null) {
				try {
					rtspSessionInfo.forceRtpRtcpEncryption =
							hasResourceUrlQueryParam(resS + "?" + tmpUri.getQuery(), URL_QUERY_PARAM_SRTP, "1");
				} catch (RtspInvalidUriException e) {
					return Optional.empty();
				}
			}
			return Optional.of(resS);
		} catch (NoSuchElementException e) {
			logError(FNC_NAME, "NoSuchElementException caught: " + e);
			return Optional.empty();
		}
	}

	private RequestBasicInfo.RequestUrlInputOrStreamSource handleResourceUrl(
				@NonNull RtspProtoMessageType requestType,
				@NonNull String resourceUrl
			) throws RtspInvalidUriException, RtspInputSourceIdNotFoundException, RtspSubStreamIdNotFoundException {
		final String FNC_NAME = getClass().getSimpleName() + ".handleResourceUrl()";

		final String rscUrlPathOrg = extractResourceUrlPath(resourceUrl);
		String rscUrlPathMod = rscUrlPathOrg;

		/*
		 * Extract Input Source ID from the requestLine.
		 * For DESCRIBE/PLAY/PAUSE/TEARDOWN requests, the Resource URL needs to contain only the Input Source ID (== SDP name):
		 *   rtsp://localhost:1051/movie.sdp
		 * For SETUP requests, the Resource URL can contain the Input Source ID and the Stream ID, or it only contains the Stream ID:
		 *   rtsp://localhost:1051/movie.sdp/streamid0
		 *   or
		 *   rtsp://localhost:1051/streamid0
		 */
		String tmpRscStreamIdStr = rscUrlPathOrg;
		int tmpIdxA = tmpRscStreamIdStr.lastIndexOf("/" + STREAM_ID_PREFIX);
		if (tmpIdxA > 0) {
			// the Resource URL Path contains the Input Source ID and the Stream ID
			tmpRscStreamIdStr = tmpRscStreamIdStr.substring(tmpIdxA + 1 + STREAM_ID_PREFIX.length());
			rscUrlPathMod = rscUrlPathMod.substring(0, tmpIdxA);
		} else if (tmpRscStreamIdStr.startsWith(STREAM_ID_PREFIX)) {
			tmpRscStreamIdStr = tmpRscStreamIdStr.substring(STREAM_ID_PREFIX.length());
		} else {
			tmpRscStreamIdStr = "";
		}
		String rscSubStreamId = "";
		RtspStreamSource rtspStreamSource = null;
		if (! tmpRscStreamIdStr.isEmpty()) {
			rscSubStreamId = tmpRscStreamIdStr;
			if (rscSubStreamId.isBlank()) {
				throw new RtspInvalidUriException(FNC_NAME + ": (rt=" + requestType + ") " +
						"Invalid Stream Source ID in URL path: '" + rscUrlPathOrg + "'");
			}
			//
			Objects.requireNonNull(rtspSessionInfo.clientIpAddr, FNC_NAME + ": rtspSessionInfo.clientIpAddr is null");
			Optional<RtspStaticSessionInfo.SubStreamInfo> tmpSubStreamInfo =
					RtspStaticSessionInfo.getSubStreamInfo(rtspSessionInfo.clientIpAddr, rscSubStreamId);
			if (tmpSubStreamInfo.isEmpty()) {
				throw new RtspSubStreamIdNotFoundException("rt=" + requestType + ", " +
						"Sub-Stream ID='" + rscSubStreamId + "'");
			}
			//
			rtspStreamSource = rtspConfig.getStreamSourceObj(tmpSubStreamInfo.get().streamSourceId()).orElse(null);
			if (rtspStreamSource == null) {  // sanity check
				throw new RtspSubStreamIdNotFoundException("rt=" + requestType + ", " +
						"Non-existing Stream Source ID in Sub-Stream ID '" + rscSubStreamId + "'");
			}
		}

		//
		RequestBasicInfo.RequestUrlInputOrStreamSource resObj = new RequestBasicInfo.RequestUrlInputOrStreamSource();

		//
		if (requestType == RtspProtoMessageType.SETUP) {
			if (rscSubStreamId.isBlank()) {  // sanity check
				throw new RtspInvalidUriException(FNC_NAME + ": (rt=" + requestType + ") " +
						"Missing Sub-Stream ID in URL path: '" + rscUrlPathOrg + "'");
			}
			//
			Objects.requireNonNull(rtspSessionInfo.clientIpAddr, FNC_NAME + ": rtspSessionInfo.clientIpAddr is null");
			final String tmpErrMsgSsid = rscSubStreamId;
			RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getStreamKmds(
					rtspSessionInfo.clientIpAddr,
					rscSubStreamId
				).orElseThrow(() -> new RtspInvalidUriException(FNC_NAME + ": (rt=" + requestType + ") " +
						"No StreamKmds for Sub-Stream ID '" + tmpErrMsgSsid + "'"));
			RtspStaticSessionInfo.addStreamInfo(
					rscSubStreamId,
					tmpStreamKmds,
					rtspStreamSource,
					resourceUrl
				);

			//
			rtspSessionInfo.inputSourceUrlPerMtMap.put(RtspProtoMessageType.SETUP, resourceUrl);

			Objects.requireNonNull(rtspSessionInfo.clientIpAddr, FNC_NAME + ": rtspSessionInfo.clientIpAddr is null");
			Optional<RtspStaticSessionInfo.SubStreamInfo> tmpSubStreamInfo =
					RtspStaticSessionInfo.getSubStreamInfo(rtspSessionInfo.clientIpAddr, rscSubStreamId);
			resObj.subStreamId = rscSubStreamId;
			resObj.inputSourceId = tmpSubStreamInfo.orElseThrow().inputSourceId();
			resObj.streamSourceId = tmpSubStreamInfo.orElseThrow().streamSourceId();

			// preliminary setting
			rtspSessionInfo.isRtpRtcpEncryptionRequired =
					rtspConfig.getInputSourceObj(resObj.inputSourceId).orElseThrow().getNeedsEncryption();
			return resObj;
		}

		//
		if (rscUrlPathMod.endsWith("/")) {
			rscUrlPathMod = rscUrlPathMod.substring(0, rscUrlPathMod.length() - 1);
		}
		Optional<RtspInputSource> optInputSource = rtspConfig.getInputSourceObj(rscUrlPathMod);
		if (optInputSource.isEmpty()) {
			throw new RtspInputSourceIdNotFoundException("rt=" + requestType + ", " +
					"URL path: '" + rscUrlPathMod + "'");
		}
		if (! optInputSource.get().getEnabled()) {
			throw new RtspInputSourceIdNotFoundException("rt=" + requestType + ", " +
					"Disabled Input Source used in URL path: '" + rscUrlPathMod + "'");
		}
		rtspSessionInfo.inputSourceUrlPerMtMap.put(requestType, resourceUrl);
		rtspSessionInfo.inputSourceObjPerMtMap.put(requestType, optInputSource.get());
		// preliminary setting
		rtspSessionInfo.isRtpRtcpEncryptionRequired = optInputSource.get().getNeedsEncryption();

		resObj.inputSourceId = optInputSource.get().getId();
		return resObj;
	}

	/**
	 * Returns the resource URL path from the given resource URL string
	 * @param resourceUrlStr Full resource URL string (e.g. 'rtsp://localhost:1051/movie.sdp/streamid0')
	 * @return The resource URL path (e.g. 'movie.sdp/streamid0')
	 * @throws RtspInvalidUriException If the resource URL is invalid
	 */
	private static @NonNull String extractResourceUrlPath(@NonNull String resourceUrlStr) throws RtspInvalidUriException {
		URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(resourceUrlStr);
		String tmpPath = rscUriObj.getPath();
		tmpPath = tmpPath.strip();
		if (tmpPath.startsWith("/")) {
			tmpPath = tmpPath.substring(1).strip();
		}
		if (tmpPath.startsWith("../") || tmpPath.contains("/../")) {
			throw new RtspInvalidUriException("Path contains '../'");
		}
		if (tmpPath.isBlank()) {
			throw new RtspInvalidUriException("Empty path");
		}
		return tmpPath;
	}

	/**
	 * Checks if the resource URL query from the given resource URL string contains the given parameter with the
	 * given value.
	 * @param resourceUrlStr Full resource URL string (e.g. 'rtsp://localhost:1051/movie.sdp/streamid0?srtp=1')
	 * @param param Parameter to check for
	 * @param value Value to check for - can be null to check for presence of parameter only
	 * @return True if the parameter is present with the given value, false otherwise
	 * @throws RtspInvalidUriException If the resource URL is invalid
	 */
	@SuppressWarnings("SameParameterValue")
	private static boolean hasResourceUrlQueryParam(
				@NonNull String resourceUrlStr,
				@NonNull String param,
				@Nullable String value
			) throws RtspInvalidUriException {
		URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(resourceUrlStr);
		String tmpQuery = rscUriObj.getQuery();
		if (tmpQuery == null) {
			return false;
		}
		for (String tmpParam : tmpQuery.split("&")) {
			String[] tmpKv = tmpParam.split("=");
			if (! tmpKv[0].equalsIgnoreCase(param)) {
				continue;
			}
			if (tmpKv.length == 1 && value == null) {
				return true;
			}
			if (tmpKv.length == 2 && tmpKv[1].equalsIgnoreCase(value)) {
				return true;
			}
		}
		return false;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseHeaderLineRequest(
				@NonNull RtspProtoMessageType requestType,
				RequestBasicInfo.@Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull String headerLine
			) throws RtspInvalidSessionIdException, RtspUnsupportedAcceptTypeException,
				RtspInvalidRequestException, RtspUnsupportedTransportException,
				RtspMissingEncryptionParamsException, RtspMissingAuthParamsException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLineRequest()";

		if (parseHeaderLineCommon(headerLine)) {
			return;
		}

		if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_CSEQ)) {
			parseHeaderLine_cseq(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_AUTH)) {
			parseHeaderLine_auth(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_OPT_REQUIRE)) {
			if (requestType != RtspProtoMessageType.OPTIONS) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received REQUIRE header in non-OPTIONS request");
			}
			parseHeaderLine_options_require(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_DES_ACCEPT)) {
			if (requestType != RtspProtoMessageType.DESCRIBE) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received ACCEPT header in non-DESCRIBE request");
			}
			parseHeaderLine_describe_accept(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_SET_TRANSPORT)) {
			if (requestType != RtspProtoMessageType.SETUP) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received TRANSPORT header in non-SETUP request");
			}
			if (requestUrlInputOrStreamSource == null) {
				throw new RtspInvalidRequestException(FNC_NAME + ": No IS/SS in SETUP request");
			}
			parseHeaderLine_setup_transport(requestUrlInputOrStreamSource, headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_SET_KEYMGMT)) {
			if (requestType != RtspProtoMessageType.SETUP) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received KEYMGMT header in non-SETUP request");
			}
			if (requestUrlInputOrStreamSource == null) {
				throw new RtspInvalidRequestException(FNC_NAME + ": No IS/SS in SETUP request");
			}
			parseHeaderLine_setup_keymgmt(requestUrlInputOrStreamSource, headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_PLA_RANGE)) {
			if (requestType != RtspProtoMessageType.PLAY) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received RANGE header in non-PLAY request");
			}
			parseHeaderLine_range(headerLine);
		} else {
			logWarn(FNC_NAME, "Received unknown header: '" + headerLine + "'");
		}
	}

	private void parseHeaderLine_cseq(@NonNull String headerLine) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_cseq()";

		String tmpCseqStr = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_CSEQ.length()).strip();
		try {
			rtspSessionInfo.rtspClientSeqNrLastRcvd = Integer.parseInt(tmpCseqStr);
		} catch (NumberFormatException e) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Invalid CSeq format: '" + tmpCseqStr + "'");
		}
		if (rtspSessionInfo.rtspClientSeqNrLastRcvd > rtspSessionInfo.rtspClientSeqNrExpected) {
			rtspSessionInfo.rtspClientSeqNrExpected = rtspSessionInfo.rtspClientSeqNrLastRcvd;
		} else if (rtspSessionInfo.rtspClientSeqNrLastRcvd < rtspSessionInfo.rtspClientSeqNrExpected) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Invalid CSeq value");
		}
		rtspSessionInfo.rtspClientSeqNrResponse = rtspSessionInfo.rtspClientSeqNrExpected++;
	}

	@SuppressWarnings("unused")
	private void parseHeaderLine_options_require(String headerLine) {
		// ignore
	}

	private void parseHeaderLine_describe_accept(String headerLine) throws RtspUnsupportedAcceptTypeException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_describe_accept()";

		String tmpDataType = headerLine.substring(RTSP_RR_HEADER_TOKEN_DES_ACCEPT.length()).strip();
		if (! RTSP_RR_HEADER_PARAM_VAL_XXX_CT_SDP.equals(tmpDataType)) {
			logError(FNC_NAME, "Invalid Accept header value: '" + tmpDataType + "'");
			throw new RtspUnsupportedAcceptTypeException();
		}
	}

	private void parseHeaderLine_setup_transport(
				RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				String headerLine
			) throws RtspUnsupportedTransportException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_setup_transport()";

		Objects.requireNonNull(
				requestUrlInputOrStreamSource.subStreamId,
				FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null"
			);
		RtspStaticSessionInfo.StreamInfo tmpStreamInfo = RtspStaticSessionInfo.getStreamInfoOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.subStreamId
			);

		tmpStreamInfo.tpIsUdp = false;
		tmpStreamInfo.tpIsUnicast = false;
		tmpStreamInfo.tpClientDestUdpPortRtp = 0;
		tmpStreamInfo.tpClientDestUdpPortRtcp = 0;
		tmpStreamInfo.tpClientDestTcpChannRtp = -1;
		tmpStreamInfo.tpClientDestTcpChannRtcp = -1;
		tmpStreamInfo.tpIsInterleaved = false;
		tmpStreamInfo.tpIsEncr = false;
		//
		String tmpTransp = headerLine.substring(RTSP_RR_HEADER_TOKEN_SET_TRANSPORT.length()).strip();
		//logDebug(FNC_NAME, "Transport='" + tmpTransp + "'");
		// e.g. 'RTP/AVP;unicast;client_port=1050-1051'
		StringTokenizer tokens = new StringTokenizer(tmpTransp, ";");
		while (tokens.hasMoreTokens()) {
			String curToken = tokens.nextToken();
			if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP1.equals(curToken) ||
					RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP2.equals(curToken)) {
				tmpStreamInfo.tpIsUdp = true;
				tmpStreamInfo.tpIsEncr = false;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP1.equals(curToken) ||
					RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP2.equals(curToken)) {
				tmpStreamInfo.tpIsUdp = true;
				tmpStreamInfo.tpIsEncr = true;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP.equals(curToken)) {
				tmpStreamInfo.tpIsUdp = false;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPTCP.equals(curToken)) {
				tmpStreamInfo.tpIsUdp = false;
				tmpStreamInfo.tpIsEncr = true;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_UNICAST.equals(curToken)) {
				tmpStreamInfo.tpIsUnicast = true;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_MULTICAST.equals(curToken)) {
				tmpStreamInfo.tpIsUnicast = false;
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT)) {
				String tmpSub = curToken.substring(RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT.length());
				String[] tmpPorts = tmpSub.split("-");
				if (tmpPorts.length == 2) {
					try {
						tmpStreamInfo.tpClientDestUdpPortRtp = Integer.parseInt(tmpPorts[0]);
						tmpStreamInfo.tpClientDestUdpPortRtcp = Integer.parseInt(tmpPorts[1]);
					} catch (NumberFormatException e) {
						logWarn(FNC_NAME, "Invalid Transport parameter: '" + curToken + "' - " +
								"cannot parse ports, invalid format");
					}
					tmpStreamInfo.tpIsInterleaved = false;
				} else {
					logWarn(FNC_NAME, "Invalid Transport parameter: '" + curToken + "' - " +
							"cannot parse ports, expected two ports");
				}
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED)) {
				String tmpSub = curToken.substring(RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED.length());
				String[] tmpPorts = tmpSub.split("-");
				if (tmpPorts.length == 2) {
					try {
						tmpStreamInfo.tpClientDestTcpChannRtp = Integer.parseInt(tmpPorts[0]);
						tmpStreamInfo.tpClientDestTcpChannRtcp = Integer.parseInt(tmpPorts[1]);
					} catch (NumberFormatException e) {
						logWarn(FNC_NAME, "Invalid Transport parameter: '" + curToken + "' - " +
								"cannot parse ports, invalid format");
					}
					tmpStreamInfo.tpIsInterleaved = true;
					/*logDebug(FNC_NAME, "interleaved RTP=" + tmpStreamInfo.tpClientDestTcpChannRtp +
							", RTCP=" + tmpStreamInfo.tpClientDestTcpChannRtcp);*/
				} else {
					logWarn(FNC_NAME, "Invalid Transport parameter: '" + curToken + "' - " +
							"cannot parse ports, expected two ports");
				}
			} else {
				logWarn(FNC_NAME, "Unknown Transport parameter: '" + curToken + "'");
			}
		}

		//
		if (rtspSessionInfo.forceRtpRtcpEncryption) {
			if (! tmpStreamInfo.tpIsEncr) {
				logWarn(FNC_NAME, "Client requested unencrypted Transport but server will force encryption");
			}
			tmpStreamInfo.tpIsEncr = true;
		}

		//
		try {
			tmpStreamInfo.isTransportValid(
					rtspSessionInfo.isRtpRtcpEncryptionRequired,
					rtspSessionInfo.forceRtpRtcpEncryption,
					rtspSessionInfo.isRtspsConnection,
					rtspConfig.getIsDebugDisableTransportUdp()
				);
		} catch (Exception e) {
			logError(FNC_NAME, "Invalid Transport: " + e.getMessage());
			throw new RtspUnsupportedTransportException();
		}

		rtspSessionInfo.isTransportUdp = tmpStreamInfo.tpIsUdp;
		rtspSessionInfo.isTransportSrtpSrtcp = tmpStreamInfo.tpIsEncr;
	}

	private void parseHeaderLine_setup_keymgmt(
				RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				String headerLine
			) throws RtspMissingEncryptionParamsException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_setup_keymgmt()";

		Objects.requireNonNull(
				rtspSessionInfo.clientIpAddr,
				FNC_NAME + ": rtspSessionInfo.clientIpAddr is null"
			);
		Objects.requireNonNull(
				requestUrlInputOrStreamSource.subStreamId,
				FNC_NAME + ": requestUrlInputOrStreamSource.subStreamId is null"
			);
		RtspStaticSessionInfo.StreamKmds tmpStreamKmds = RtspStaticSessionInfo.getOrAddStreamKmds(
				rtspSessionInfo.clientIpAddr,
				requestUrlInputOrStreamSource.subStreamId,
				RandomHelper.getRandomUint32(false)
			);

		boolean haveKeyData = false;
		//
		String tmpKeymgmt = headerLine.substring(RTSP_RR_HEADER_TOKEN_SET_KEYMGMT.length()).strip();
		// e.g. 'KeyMgmt: prot=mikey; uri="rtsp://.../streamid00"; data="[BASE64 ENCODED DATA]"'
		StringTokenizer tokens = new StringTokenizer(tmpKeymgmt, ";");
		while (tokens.hasMoreTokens()) {
			String curToken = tokens.nextToken().strip();
			if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_KM_PROT)) {
				String tmpSub = curToken.substring(RTSP_RR_HEADER_PARAM_KEY_SET_KM_PROT.length());
				if (! tmpSub.equals(RTSP_RR_HEADER_PARAM_VAL_SET_KM_MIKEY)) {
					throw new RtspMissingEncryptionParamsException();
				}
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_KM_DATA)) {
				String tmpSub = extractKeyValue(curToken, RTSP_RR_HEADER_PARAM_KEY_SET_KM_DATA);
				try {
					SrtxpKmd kmdRcvd = MikeyParser.parseMickeyMsgIntoKmd(tmpSub);
					tmpStreamKmds.kmdInbound = kmdRcvd.clone();
					haveKeyData = true;
					//System.out.println("<<<<<<<<<<<<<<<< " + kmdRcvd);
				} catch (SrtxpSecurityException e) {
					logError(FNC_NAME, "Failed to set client MIKEY: " + e.getMessage());
					throw new RtspMissingEncryptionParamsException();
				}
			} else if (! curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_KM_URI)) {
				logWarn(FNC_NAME, "Unknown Keymgmt parameter: '" + curToken + "'");
			}
		}

		if (! haveKeyData) {
			throw new RtspMissingEncryptionParamsException();
		}
	}

	private void parseHeaderLine_range(String headerLine) {
		//final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_range()";

		rtspSessionInfo.clientPlaybackRangeValue = headerLine.substring(RTSP_RR_HEADER_TOKEN_PLA_RANGE.length()).strip();
		//logDebug(FNC_NAME, "Range='" + rtspVariables.clientPlaybackRangeValue + "'");
	}

	private void parseHeaderLine_auth(String headerLine) throws RtspMissingAuthParamsException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_auth()";

		String tmpHdLine = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_AUTH.length()).strip();
		if (tmpHdLine.isBlank()) {
			logError(FNC_NAME, "Invalid empty Auth header value - ignoring");
			return;
		}
		// e.g. 'Authorization: Digest username="admin", realm="Abcdef Some", nonce="xxx", uri="rtsp://xxx:88/videoMain", response="xxx"'
		if (! tmpHdLine.startsWith(RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX)) {
			throw new RtspMissingAuthParamsException();
		}
		tmpHdLine = tmpHdLine.substring(RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.length()).strip();
		StringTokenizer tokens = new StringTokenizer(tmpHdLine, ",");
		boolean haveUser = false;
		boolean haveRealm = false;
		boolean haveNonce = false;
		boolean haveUri = false;
		boolean haveResp = false;
		while (tokens.hasMoreTokens()) {
			String curToken = tokens.nextToken().strip();
			if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER)) {
				rtspSessionInfo.authInfo.authUser = extractKeyValue(curToken, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER);
				haveUser = true;  // tolerate empty username now and reject it later
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM)) {
				rtspSessionInfo.authInfo.authRealmClient = extractKeyValue(curToken, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM);
				haveRealm = (! rtspSessionInfo.authInfo.authRealmClient.isBlank());
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE)) {
				rtspSessionInfo.authInfo.authNonceClient = extractKeyValue(curToken, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE);
				rtspSessionInfo.authInfo.authNonceClient = rtspSessionInfo.authInfo.authNonceClient.toLowerCase();
				haveNonce = (! rtspSessionInfo.authInfo.authNonceClient.isBlank());
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI)) {
				rtspSessionInfo.authInfo.authUri = extractKeyValue(curToken, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI);
				haveUri = (! rtspSessionInfo.authInfo.authUri.isBlank());
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP)) {
				rtspSessionInfo.authInfo.authResp = extractKeyValue(curToken, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP);
				rtspSessionInfo.authInfo.authResp = rtspSessionInfo.authInfo.authResp.toLowerCase();
				haveResp = true;  // tolerate empty challenge-response now and reject it later
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO)) {
				String tmpAlgo = extractKeyValue(curToken, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO);
				if (! tmpAlgo.equalsIgnoreCase(RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_ALGO_MD5)) {
					logWarn(FNC_NAME, "Unsupported Auth Algorithm: '" + tmpAlgo + "'");
					throw new RtspMissingAuthParamsException();
				}
			} else {
				logWarn(FNC_NAME, "Unknown Auth parameter: '" + curToken + "'");
			}
		}

		if (! (haveUser && haveRealm && haveNonce && haveUri && haveResp)) {
			throw new RtspMissingAuthParamsException();
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private @NonNull String extractKeyValue(@NonNull String inputStr, @NonNull String key) {
		return inputStr.substring(key.length())
				.replace("\"", "").replace("'", "").strip();
	}

}

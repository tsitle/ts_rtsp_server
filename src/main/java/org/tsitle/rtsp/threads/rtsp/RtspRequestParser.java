package org.tsitle.rtsp.threads.rtsp;

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
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.LogMsgInterface;

import java.net.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.tsitle.rtsp.threads.rtsp.RtspPrivateConstants.*;

public class RtspRequestParser {

	private static final boolean DEBUG_REQUESTS_ENABLED = false;

	private static final Pattern patternIllegalChars = Pattern.compile("[\\P{Print}$]");

	private final @NonNull LogMsgInterface logMsgInterface;
	private final RtspConfig rtspConfig;
	private final RtspSessionInfo rtspSessionInfo;

	private BooleanSupplier cbCanReadData = null;
	private Supplier<Optional<String>> cbReadDataLine = null;

	public RtspRequestParser(
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

	public void setCbCanReadData(BooleanSupplier cbCanReadData) {
		this.cbCanReadData = cbCanReadData;
	}

	public void setCbReadDataLine(Supplier<Optional<String>> cbReadDataLine) {
		this.cbReadDataLine = cbReadDataLine;
	}

	// -----------------------------------------------------------------------------------------------------------------

	public RequestBasicInfo parseRequest() throws InputStreamNotReadyException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseRequest()";

		checkCallbackFncs();

		//
		rtspSessionInfo.authInfo.resetPerRequest();

		// parse request lines and extract the requestType:
		String requestLine;
		try {
			requestLine = readOneLine(true);
			if (DEBUG_REQUESTS_ENABLED) {
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
		final ServerMessageType requestType = parseServerMessageType(requestLine);
		if (requestType == ServerMessageType.UNKNOWN) {
			logError(FNC_NAME, "Unknown request type in requestLine '" + requestLine + "'");
			return RequestBasicInfo.createUnsupportedMethod();
		}

		//
		ServerResponseStatusCode respStatusCode = ServerResponseStatusCode.OK;

		// read resource URL from the requestLine
		Optional<String> optResourceUrl = parseServerMessageResourceUrl(requestLine);
		if (optResourceUrl.isEmpty()) {
			return RequestBasicInfo.createKnownWithError(requestType, ServerResponseStatusCode.BAD_REQUEST);
		}
		final String resourceUrl = optResourceUrl.get();
		if (resourceUrl.length() > RTSP_MAX_RESOURCE_URL_LENGTH) {
			logError(FNC_NAME, String.format("Resource URL too long (is=%d, max=%d), rejecting request",
					resourceUrl.length(), RTSP_MAX_RESOURCE_URL_LENGTH));
			respStatusCode = ServerResponseStatusCode.URI_TOO_LONG;
		}

		// handle resource URL
		RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource = null;
		if (respStatusCode == ServerResponseStatusCode.OK && requestType != ServerMessageType.OPTIONS) {
			try {
				requestUrlInputOrStreamSource = handleResourceUrl(requestType, resourceUrl);
			} catch (RtspInvalidUriException e) {
				logError(FNC_NAME, "Invalid Resource URL '" + resourceUrl + "' (" + e.getMessage() +
						"), rejecting request");
				respStatusCode = ServerResponseStatusCode.FORBIDDEN;
			} catch (RtspInputSourceIdNotFoundException e) {
				logError(FNC_NAME, "Invalid Stream ID in Resource URL '" + resourceUrl + "' (" + e.getMessage() +
						"), rejecting request");
				respStatusCode = ServerResponseStatusCode.NOT_FOUND;
			}
		}

		// parse header lines
		String headerLine = "";
		int timeoutCnt = 0;
		do {
			try {
				headerLine = readOneLine(false);
				timeoutCnt = 0;
				if (DEBUG_REQUESTS_ENABLED) {
					logDebug(FNC_NAME, "---------------- headerLine: " + headerLine);
				}
				if (respStatusCode == ServerResponseStatusCode.OK) {
					parseHeaderLine(requestType, requestUrlInputOrStreamSource, headerLine);
				}
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
				respStatusCode = ServerResponseStatusCode.BAD_REQUEST;
			} catch (RtspInvalidSessionIdException e) {
				logError(FNC_NAME, "Invalid Session ID, rejecting request");
				respStatusCode = ServerResponseStatusCode.SESSION_NOT_FOUND;
			} catch (RtspUnsupportedAcceptTypeException e) {
				logError(FNC_NAME, "Unsupported Accept Type, rejecting request");
				respStatusCode = ServerResponseStatusCode.BAD_REQUEST;
			} catch (RtspUnsupportedTransportException e) {
				logError(FNC_NAME, "Unsupported Transport, rejecting request");
				respStatusCode = ServerResponseStatusCode.UNSUPPORTED_TRANSPORT;
			} catch (RtspMissingEncryptionParamsException e) {
				logError(FNC_NAME, "Missing encryption parameters, rejecting request");
				respStatusCode = ServerResponseStatusCode.BAD_REQUEST;
			} catch (RtspMissingAuthParamsException e) {
				logError(FNC_NAME, "Missing authentication parameters, rejecting request");
				respStatusCode = ServerResponseStatusCode.BAD_REQUEST;
			}
		} while (! headerLine.isBlank());

		if (respStatusCode != ServerResponseStatusCode.OK) {
			return RequestBasicInfo.createKnownWithError(requestType, respStatusCode);
		}
		return RequestBasicInfo.createOk(requestType, requestUrlInputOrStreamSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private String readOneLine(boolean isFirst) throws InputStreamNotReadyException, InputStreamEosException {
		if (! cbCanReadData.getAsBoolean()) {
			throw new InputStreamNotReadyException();
		}
		Optional<String> optLine = cbReadDataLine.get();
		if (optLine.isEmpty()) {
			throw new InputStreamEosException();
		}
		String resS = optLine.get();
		// remove forbidden characters from the line
		Matcher matcher = patternIllegalChars.matcher(resS);
		resS = matcher.replaceAll("");
		if (isFirst) {
			// strip occasionally occurring nonsense from the beginning of the line
			for (ServerMessageType tmpType : ServerMessageType.values()) {
				int tmpIx = resS.indexOf(tmpType.name());
				if (tmpIx < 0) {
					continue;
				}
				if (tmpIx == 0) {
					break;
				}
				resS = resS.substring(tmpIx).strip();
				break;
			}
		}
		return resS;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private ServerMessageType parseServerMessageType(String requestLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseServerMessageType()";

		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			String requestTypeStr = tokens.nextToken();
			//
			ServerMessageType resEn = Arrays.stream(ServerMessageType.values())
					.filter(tmpType -> tmpType != ServerMessageType.UNKNOWN)
					.filter(tmpType -> tmpType.name().equals(requestTypeStr))
					.findFirst()
					.orElse(ServerMessageType.UNKNOWN);
			if (resEn != ServerMessageType.UNKNOWN) {
				tokens.nextToken();  // URL
				// we shall be tolerant here and ignore a missing PROTOCOL/VERSION token
				if (tokens.hasMoreTokens()) {
					String proto = tokens.nextToken();
					if (! (proto.equals(RTSP_RR_CMD_PROTOCOL_VERSION_1) || proto.equals(RTSP_RR_CMD_PROTOCOL_VERSION_2))) {
						rtspSessionInfo.lastRequestRtspProtoVersion = "-";
						resEn = ServerMessageType.UNKNOWN;
						logError(FNC_NAME, "invalid protocol/version '" + proto + "'");
					} else {
						rtspSessionInfo.lastRequestRtspProtoVersion = proto;
					}
				}
			}
			return resEn;
		} catch (NoSuchElementException e) {
			logError(FNC_NAME, "NoSuchElementException caught: " + e);
			return ServerMessageType.UNKNOWN;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private Optional<String> parseServerMessageResourceUrl(String requestLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseServerMessageResourceUrl()";

		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			tokens.nextToken();  // requestType
			String resS = tokens.nextToken();
			if (! resS.startsWith(RtspConstants.RTSP_URL_PROTOCOL + "://")) {
				logError(FNC_NAME, "invalid URL '" + resS + "'");
				return Optional.empty();
			}
			//
			URI tmpUri = URI.create(resS);
			int tmpPort = tmpUri.getPort();
			resS = RtspConstants.RTSP_URL_PROTOCOL + "://" + tmpUri.getHost() +
					(tmpPort != -1 ? ":" + tmpUri.getPort() : "") + tmpUri.getPath();
			return Optional.of(resS);
		} catch (NoSuchElementException e) {
			logError(FNC_NAME, "NoSuchElementException caught: " + e);
			return Optional.empty();
		}
	}

	private RequestBasicInfo.RequestUrlInputOrStreamSource handleResourceUrl(
				ServerMessageType requestType,
				String resourceUrl
			) throws RtspInvalidUriException, RtspInputSourceIdNotFoundException {
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
		int rscStreamSourceId = -1;
		RtspStreamSource rtspStreamSource = null;
		if (! tmpRscStreamIdStr.isEmpty()) {
			try {
				rscStreamSourceId = Integer.parseInt(tmpRscStreamIdStr);
			} catch (NumberFormatException e) {
				// ignore
			}
			if (rscStreamSourceId == -1) {
				throw new RtspInvalidUriException(FNC_NAME + ": (rt=" + requestType + ") " +
						"Invalid Stream Source ID in URL path: '" + rscUrlPathOrg + "'");
			}
			rtspStreamSource = rtspConfig.getStreamSourceObj(rscStreamSourceId).orElse(null);
			if (rtspStreamSource == null) {
				throw new RtspInputSourceIdNotFoundException(FNC_NAME + ": (rt=" + requestType + ") " +
						"Non-existing Stream Source ID #" + rscStreamSourceId);
			}
		}

		//
		RequestBasicInfo.RequestUrlInputOrStreamSource resObj = new RequestBasicInfo.RequestUrlInputOrStreamSource();

		//
		if (requestType == ServerMessageType.SETUP) {
			if (rscStreamSourceId == -1) {
				throw new RtspInvalidUriException(FNC_NAME + ": (rt=" + requestType + ") " +
						"Missing Stream Source ID in URL path: '" + rscUrlPathOrg + "'");
			}
			RtspSessionInfo.StreamInfo streamInfo;
			if (rtspSessionInfo.streamsMapSetup.containsKey(rscStreamSourceId)) {
				// if the DESCRIBE request already created the stream info object
				streamInfo = rtspSessionInfo.streamsMapSetup.get(rscStreamSourceId);
			} else {
				streamInfo = new RtspSessionInfo.StreamInfo();
			}
			streamInfo.rtspStreamSource = rtspStreamSource;
			streamInfo.inputSourceUrlSetup = resourceUrl;
			streamInfo.rtspRtpSeqNrT0 = RandomHelper.getRandomUint16();
			streamInfo.rtspRtpTimestampT0 = RandomHelper.getRandomUint32();
			streamInfo.rtspRtpGenTsT0Ns = System.nanoTime();
			rtspSessionInfo.streamsMapSetup.put(rscStreamSourceId, streamInfo);

			rtspSessionInfo.inputSourceUrlPerSmtMap.put(ServerMessageType.SETUP, resourceUrl);

			resObj.inputSourceId = rtspConfig.getInputSourceIdForStreamSourceId(rscStreamSourceId).orElse(null);
			resObj.streamSourceId = rscStreamSourceId;
			return resObj;
		}

		//
		if (rscUrlPathMod.endsWith("/")) {
			rscUrlPathMod = rscUrlPathMod.substring(0, rscUrlPathMod.length() - 1);
		}
		Optional<RtspInputSource> optInputSource = rtspConfig.getInputSourceObj(rscUrlPathMod);
		if (optInputSource.isEmpty()) {
			throw new RtspInputSourceIdNotFoundException(FNC_NAME + ": (rt=" + requestType + ") " +
					"Non-existing Input Source ID in URL path: '" + rscUrlPathMod + "'");
		}
		if (! optInputSource.get().getEnabled()) {
			throw new RtspInputSourceIdNotFoundException(FNC_NAME + ": (rt=" + requestType + ") " +
					"Disabled Input Source used in URL path: '" + rscUrlPathMod + "'");
		}
		rtspSessionInfo.inputSourceUrlPerSmtMap.put(requestType, resourceUrl);
		rtspSessionInfo.inputSourceObjPerSmtMap.put(requestType, optInputSource.get());

		resObj.inputSourceId = optInputSource.get().getId();
		return resObj;
	}

	/**
	 * Returns the resource URL path from the given resource URL string
	 * @param resourceUrlStr Full resource URL string (e.g. 'rtsp://localhost:1051/movie.sdp/streamid0')
	 * @return The resource URL path (e.g. 'movie.sdp/streamid0')
	 * @throws RtspInvalidUriException If the resource URL is invalid
	 */
	private static String extractResourceUrlPath(String resourceUrlStr) throws RtspInvalidUriException {
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

	// -----------------------------------------------------------------------------------------------------------------

	private void parseHeaderLine(
				@NonNull ServerMessageType requestType,
				RequestBasicInfo.@Nullable RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				@NonNull String headerLine
			) throws RtspInvalidSessionIdException, RtspUnsupportedAcceptTypeException,
				RtspInvalidRequestException, RtspUnsupportedTransportException,
				RtspMissingEncryptionParamsException, RtspMissingAuthParamsException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine()";

		if (headerLine.isBlank()) {
			return;
		}

		if (headerLine.equals(" " + RTSP_RR_CMD_PROTOCOL_VERSION_1) ||
				headerLine.equals(" " + RTSP_RR_CMD_PROTOCOL_VERSION_2)) {
			// ignore this non-standard header line
			return;
		}
		if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_CSEQ)) {
			parseHeaderLine_cseq(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_USERAGENT)) {
			parseHeaderLine_useragent(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_SESSION)) {
			parseHeaderLine_session(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_DATE)) {
			parseHeaderLine_date(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_XXX_AUTH)) {
			parseHeaderLine_auth(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_DES_ACCEPT)) {
			if (requestType != ServerMessageType.DESCRIBE) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received ACCEPT header in non-DESCRIBE request");
			}
			parseHeaderLine_describe_accept(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_SET_TRANSPORT)) {
			if (requestType != ServerMessageType.SETUP) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received TRANSPORT header in non-SETUP request");
			}
			if (requestUrlInputOrStreamSource == null) {
				throw new RtspInvalidRequestException(FNC_NAME + ": No IS/SS in SETUP request");
			}
			parseHeaderLine_setup_transport(requestUrlInputOrStreamSource, headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_SET_KEYMGMT)) {
			if (requestType != ServerMessageType.SETUP) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received KEYMGMT header in non-SETUP request");
			}
			if (requestUrlInputOrStreamSource == null) {
				throw new RtspInvalidRequestException(FNC_NAME + ": No IS/SS in SETUP request");
			}
			parseHeaderLine_setup_keymgmt(requestUrlInputOrStreamSource, headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_PLA_RANGE)) {
			if (requestType != ServerMessageType.PLAY) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received RANGE header in non-PLAY request");
			}
			parseHeaderLine_range(headerLine);
		} else {
			logWarn(FNC_NAME, "Received unknown header: '" + headerLine + "'");
		}
	}

	private void parseHeaderLine_cseq(String headerLine) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_cseq()";

		String tmpCseqStr = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_CSEQ.length()).strip();
		rtspSessionInfo.rtspSeqNrLastRcvd = Integer.parseInt(tmpCseqStr);
		if (rtspSessionInfo.rtspSeqNrLastRcvd > rtspSessionInfo.rtspSeqNrExpected) {
			rtspSessionInfo.rtspSeqNrExpected = rtspSessionInfo.rtspSeqNrLastRcvd;
		} else if (rtspSessionInfo.rtspSeqNrLastRcvd < rtspSessionInfo.rtspSeqNrExpected) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Invalid CSeq");
		}
		rtspSessionInfo.rtspSeqNrResponse = rtspSessionInfo.rtspSeqNrExpected++;
	}

	private void parseHeaderLine_useragent(String headerLine) {
		//final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_useragent()";

		@SuppressWarnings("unused")
		String tmpUa = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_USERAGENT.length()).strip();
		//logDebug(FNC_NAME, "User-Agent='" + tmpUa + "'");
	}

	private void parseHeaderLine_session(String headerLine) throws RtspInvalidSessionIdException {
		//final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_session()";

		String tmpSessId = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_SESSION.length()).strip();
		//logDebug(FNC_NAME, "Session='" + tmpSessId + "'");
		if (! rtspSessionInfo.rtspSessionId.equals(tmpSessId)) {
			throw new RtspInvalidSessionIdException();
		}
	}

	private void parseHeaderLine_describe_accept(String headerLine) throws RtspUnsupportedAcceptTypeException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_describe_accept()";

		String tmpDataType = headerLine.substring(RTSP_RR_HEADER_TOKEN_DES_ACCEPT.length()).strip();
		if (! RTSP_RR_HEADER_PARAM_VAL_DES_ACCEPT.equals(tmpDataType)) {
			logError(FNC_NAME, "Invalid Accept header value: '" + tmpDataType + "'");
			throw new RtspUnsupportedAcceptTypeException();
		}
	}

	private void parseHeaderLine_setup_transport(
				RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				String headerLine
			) throws RtspUnsupportedTransportException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_setup_transport()";

		RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.getStreamInfoOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.streamSourceId
			);

		tmpStreamInfo.tpIsUdp = false;
		tmpStreamInfo.tpIsUnicast = false;
		tmpStreamInfo.tpClientDestPortRtp = 0;
		tmpStreamInfo.tpClientDestPortRtcp = 0;
		tmpStreamInfo.tpIsEncr = false;
		//
		String tmpTransp = headerLine.substring(RTSP_RR_HEADER_TOKEN_SET_TRANSPORT.length()).strip();
		//logDebug(FNC_NAME, "Transport='" + tmpTransp + "'");
		// e.g. 'RTP/AVP;unicast;client_port=1050-1051'
		StringTokenizer tokens = new StringTokenizer(tmpTransp, ";");
		while (tokens.hasMoreTokens()) {
			String curToken = tokens.nextToken();
			if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP.equals(curToken)) {
				//logDebug(FNC_NAME, "type=" + curToken);
				tmpStreamInfo.tpIsUdp = true;
				tmpStreamInfo.tpIsEncr = false;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP.equals(curToken)) {
				//logDebug(FNC_NAME, "type=" + curToken);
				tmpStreamInfo.tpIsUdp = true;
				tmpStreamInfo.tpIsEncr = true;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP.equals(curToken)) {
				logWarn(FNC_NAME, "type=" + curToken);  // @TODO implement RTP over TCP
				tmpStreamInfo.tpIsUdp = false;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_UNICAST.equals(curToken)) {
				//logDebug(FNC_NAME, "uni/multi=" + curToken);
				tmpStreamInfo.tpIsUnicast = true;
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT)) {
				String tmpSub = curToken.substring(RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT.length());
				String[] tmpPorts = tmpSub.split("-");
				tmpStreamInfo.tpClientDestPortRtp = Integer.parseInt(tmpPorts[0]);
				tmpStreamInfo.tpClientDestPortRtcp = Integer.parseInt(tmpPorts[1]);
				tmpStreamInfo.tpIsInterleaved = false;
			} else if (curToken.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED)) {
				String tmpSub = curToken.substring(RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED.length());
				logWarn(FNC_NAME, "interleaved=" + tmpSub);  // @TODO implement RTP over TCP
				tmpStreamInfo.tpIsInterleaved = true;
			} else {
				logWarn(FNC_NAME, "Unknown Transport parameter: '" + curToken + "'");
			}
		}

		if (! tmpStreamInfo.isTransportValid(rtspSessionInfo.isRtxpEncryptionEnabled)) {
			throw new RtspUnsupportedTransportException();
		}
	}

	private void parseHeaderLine_setup_keymgmt(
				RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				String headerLine
			) throws RtspMissingEncryptionParamsException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_setup_keymgmt()";

		RtspSessionInfo.StreamInfo tmpStreamInfo = rtspSessionInfo.getStreamInfoOrThrow(
				FNC_NAME,
				requestUrlInputOrStreamSource.streamSourceId
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
					tmpStreamInfo.streamKmds.kmdInbound = kmdRcvd.clone();
					haveKeyData = true;
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

	private void parseHeaderLine_date(String headerLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_date()";

		String tmp = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_DATE.length()).strip();
		//logDebug(FNC_NAME, "Date='" + tmp + "'");
		if (tmp.isBlank()) {
			logError(FNC_NAME, "Invalid empty Date header value - ignoring");
		}
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

	// -----------------------------------------------------------------------------------------------------------------

	private void checkCallbackFncs() {
		final String FNC_NAME = getClass().getSimpleName() + ".checkCallbackFncs()";

		if (cbCanReadData == null || cbReadDataLine == null) {
			throw new IllegalStateException(FNC_NAME + ": Callback functions not set");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

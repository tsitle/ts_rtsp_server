package org.tsitle.rtsp.threads.rtsp;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.config.RtspInputSource;
import org.tsitle.rtsp.config.RtspConfig;
import org.tsitle.rtsp.config.RtspStreamSource;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.helpers.RandomHelper;
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

		// parse request lines and extract the requestType:
		String requestLine;
		do {
			try {
				requestLine = readOneLine(true);
			} catch (InputStreamEofException ex) {
				logError(FNC_NAME, "EOF reached");
				return RequestBasicInfo.createUnknown();
			}
			//
			if (requestLine.isBlank()) {
				logError(FNC_NAME, "received empty line");
			}
		} while (requestLine.isBlank());

		// read requestType from the requestLine
		final ServerMessageType requestType = parseServerMessageType(requestLine);
		if (requestType == ServerMessageType.UNKNOWN) {
			logError(FNC_NAME, "Unknown request type in requestLine '" + requestLine + "'");
			return RequestBasicInfo.createUnknown();
		}

		// read resource URL from the requestLine
		String resourceUrl = parseServerMessageResourceUrl(requestLine);
		if (resourceUrl.isEmpty()) {
			return RequestBasicInfo.createKnownWithError(requestType, ServerResponseStatusCode.BAD_REQUEST);
		}

		// handle resource URL
		RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource;
		try {
			if (resourceUrl.length() > RTSP_MAX_RESOURCE_URL_LENGTH) {
				resourceUrl = resourceUrl.substring(0, RTSP_MAX_RESOURCE_URL_LENGTH);  // just in case
				throw new RtspInvalidUriException("Resource URL too long");
			}
			requestUrlInputOrStreamSource = handleResourceUrl(requestType, resourceUrl);
		} catch (RtspInvalidUriException e) {
			logError(FNC_NAME, "Invalid Resource URL '" + resourceUrl + "' (" + e.getMessage() +
					"), rejecting request");
			return RequestBasicInfo.createKnownWithError(requestType, ServerResponseStatusCode.BAD_REQUEST);
		} catch (RtspInputSourceIdNotFoundException e) {
			logError(FNC_NAME, "Invalid Stream ID in Resource URL '" + resourceUrl + "' (" + e.getMessage() +
					"), rejecting request");
			return RequestBasicInfo.createKnownWithError(requestType, ServerResponseStatusCode.NOT_FOUND);
		}

		// parse header lines
		String headerLine;
		do {
			try {
				headerLine = readOneLine(false);
				parseHeaderLine(requestType, requestUrlInputOrStreamSource, headerLine);
			} catch (InputStreamNotReadyException | InputStreamEofException ex) {
				break;
			} catch (RtspInvalidRequestException ex) {
				logError(FNC_NAME, "InvalidRtspRequestException: " + ex.getMessage());
				return RequestBasicInfo.createKnownWithError(requestType, ServerResponseStatusCode.BAD_REQUEST);
			} catch (RtspInvalidSessionIdException ex) {
				logError(FNC_NAME, "Invalid Session ID, rejecting request");
				return RequestBasicInfo.createKnownWithError(requestType, ServerResponseStatusCode.SESSION_NOT_FOUND);
			} catch (RtspUnsupportedAcceptTypeException ex) {
				logError(FNC_NAME, "Unsupported Accept Type, rejecting request");
				return RequestBasicInfo.createKnownWithError(requestType, ServerResponseStatusCode.BAD_REQUEST);
			} catch (RtspUnsupportedTransportException ex) {
				logError(FNC_NAME, "Unsupported Transport, rejecting request");
				return RequestBasicInfo.createKnownWithError(requestType, ServerResponseStatusCode.UNSUPPORTED_TRANSPORT);
			}
		} while (! headerLine.isBlank());

		return RequestBasicInfo.createOk(requestType, requestUrlInputOrStreamSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private String readOneLine(boolean isFirst) throws InputStreamNotReadyException, InputStreamEofException {
		if (! cbCanReadData.getAsBoolean()) {
			throw new InputStreamNotReadyException();
		}
		Optional<String> optLine = cbReadDataLine.get();
		if (optLine.isEmpty()) {
			throw new InputStreamEofException();
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
		} catch (NoSuchElementException ex) {
			logError(FNC_NAME, "NoSuchElementException caught: " + ex);
			return ServerMessageType.UNKNOWN;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private String parseServerMessageResourceUrl(String requestLine) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseServerMessageResourceUrl()";

		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			tokens.nextToken();  // requestType
			String resS = tokens.nextToken();
			if (! resS.startsWith(RtspConstants.RTSP_URL_PROTOCOL + "://")) {
				resS = "";
				logError(FNC_NAME, "invalid URL '" + resS + "'");
			}
			return resS;
		} catch (NoSuchElementException ex) {
			logError(FNC_NAME, "NoSuchElementException caught: " + ex);
			return "";
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
		 * For DESCRIBE/OPTIONS/PLAY/PAUSE/TEARDOWN requests, the Resource URL needs to contain only the Input Source ID (== SDP name):
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
			} catch (NumberFormatException ex) {
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
			RtspSessionInfo.StreamInfo streamInfo = new RtspSessionInfo.StreamInfo();
			streamInfo.rtspStreamSource = rtspStreamSource;
			streamInfo.inputSourceUrlSetup = resourceUrl;
			streamInfo.rtspSsrcId = RandomHelper.getRandomUint32();
			streamInfo.rtspRtpSeqNrT0 = RandomHelper.getRandomUint16();
			streamInfo.rtspRtpTimestampT0 = RandomHelper.getRandomUint32();
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
				ServerMessageType requestType,
				RequestBasicInfo.RequestUrlInputOrStreamSource requestUrlInputOrStreamSource,
				String headerLine
			) throws RtspInvalidSessionIdException, RtspUnsupportedAcceptTypeException,
				RtspInvalidRequestException, RtspUnsupportedTransportException {
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
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_DES_ACCEPT)) {
			if (requestType != ServerMessageType.DESCRIBE) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received ACCEPT header in non-DESCRIBE request");
			}
			parseHeaderLine_describe_accept(headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_SET_TRANSPORT)) {
			if (requestType != ServerMessageType.SETUP) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received TRANSPORT header in non-SETUP request");
			}
			parseHeaderLine_setup_transport(requestUrlInputOrStreamSource, headerLine);
		} else if (headerLine.startsWith(RTSP_RR_HEADER_TOKEN_PLA_RANGE)) {
			if (requestType != ServerMessageType.PLAY) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Received RANGE header in non-PLAY request");
			}
			parseHeaderLine_range(headerLine);
		} else {
			logError(FNC_NAME, "Received unknown header: '" + headerLine + "'");
		}
	}

	private void parseHeaderLine_cseq(String headerLine) {
		//final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLine_cseq()";

		String tmpCseq = headerLine.substring(RTSP_RR_HEADER_TOKEN_XXX_CSEQ.length()).strip();
		rtspSessionInfo.rtspSeqNr = Integer.parseInt(tmpCseq);
		//logDebug(FNC_NAME, "Cseq=" + rtspVariables.rtspSeqNr);
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
		if (! RTSP_RR_HEADER_VALUE_DES_ACCEPT.equals(tmpDataType)) {
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
		//
		String tmpTransp = headerLine.substring(RTSP_RR_HEADER_TOKEN_SET_TRANSPORT.length()).strip();
		//logDebug(FNC_NAME, "Transport='" + tmpTransp + "'");
		// e.g. 'RTP/AVP;unicast;client_port=1050-1051'
		StringTokenizer tokens = new StringTokenizer(tmpTransp, ";");
		while (tokens.hasMoreTokens()) {
			String curToken = tokens.nextToken();
			if (RTSP_RR_HEADER_VALUE_SET_TP_RTPAVPUDP.equals(curToken)) {
				//logDebug(FNC_NAME, "type=" + RTSP_RR_HEADER_VALUE_TP_RTPAVPUDP);
				tmpStreamInfo.tpIsUdp = true;
			} else if (RTSP_RR_HEADER_VALUE_SET_TP_RTPAVPTCP.equals(curToken)) {
				logDebug(FNC_NAME, "type=" + RTSP_RR_HEADER_VALUE_SET_TP_RTPAVPTCP);  // @TODO implement RTP over TCP
				tmpStreamInfo.tpIsUdp = false;
			} else if (RTSP_RR_HEADER_VALUE_SET_TP_UNICAST.equals(curToken)) {
				//logDebug(FNC_NAME, "uni/multi=" + RTSP_RR_HEADER_VALUE_TP_UNICAST);
				tmpStreamInfo.tpIsUnicast = true;
			} else if (curToken.startsWith(RTSP_RR_HEADER_VALUE_SET_TP_CLIENTPORT)) {
				String tmpSub = curToken.substring(RTSP_RR_HEADER_VALUE_SET_TP_CLIENTPORT.length());
				String[] tmpPorts = tmpSub.split("-");
				tmpStreamInfo.tpClientDestPortRtp = Integer.parseInt(tmpPorts[0]);
				tmpStreamInfo.tpClientDestPortRtcp = Integer.parseInt(tmpPorts[1]);
				tmpStreamInfo.tpIsInterleaved = false;
			} else if (curToken.startsWith(RTSP_RR_HEADER_VALUE_SET_TP_INTERLEAVED)) {
				String tmpSub = curToken.substring(RTSP_RR_HEADER_VALUE_SET_TP_INTERLEAVED.length());
				logDebug(FNC_NAME, "interleaved=" + tmpSub);  // @TODO implement RTP over TCP
				tmpStreamInfo.tpIsInterleaved = true;
			}
		}

		if (! tmpStreamInfo.isTransportValid()) {
			throw new RtspUnsupportedTransportException();
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

	// -----------------------------------------------------------------------------------------------------------------

	private void checkCallbackFncs() {
		final String FNC_NAME = getClass().getSimpleName() + ".checkCallbackFncs()";

		if (cbCanReadData == null || cbReadDataLine == null) {
			throw new IllegalStateException(FNC_NAME + ": Callback functions not set");
		}
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

package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.*;
import org.tsitle.rtsp.helpers.HostnameHelper;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoStatusCode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtoLowMsgStructured;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderEntry;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderKey;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderTypeAuth;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.header.RtspProtoLowHeaderTypeKeymgmt;

import java.net.URI;
import java.util.*;

import static org.tsitle.rtsp.threads.rtsp.proto.RtspProtoConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_CT_SDP;
import static org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtoLowConstants.*;

public final class RtspProtoLowRequestParser {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowRequestParser(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoLowMsgStructured parseRequest(@NonNull RtspProtoLowMsgRaw input) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseRequest()";

		RtspProtoLowMsgStructured resObj = new RtspProtoLowMsgStructured();
		if (! input.readSuccess) {
			return resObj;
		}

		// read messageType from the requestLine
		parseMessageType(input.mainLine, resObj);
		if (resObj.messageType == RtspProtoMessageType.UNKNOWN) {
			logError(FNC_NAME, "Unknown request type in requestLine '" + input.mainLine + "'");
			resObj.statusCode = RtspProtoStatusCode.METHOD_NOT_ALLOWED;  // this will be ignored though
			return resObj;
		}

		//
		resObj.statusCode = RtspProtoStatusCode.OK;

		// read resource URL from the requestLine
		parseRequestResourceUrl(input.mainLine, resObj);
		if (resObj.statusCode != RtspProtoStatusCode.OK) {
			return resObj;
		}

		// parse header lines
		parseHeaderLines(input.headerLines, resObj);

		// check CSeq
		Optional<Integer> tmpOptCseq = resObj.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			logError(FNC_NAME, "Missing CSeq header in request");
			resObj.statusCode = RtspProtoStatusCode.BAD_REQUEST;
			return resObj;
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void parseMessageType(@NonNull String requestLine, @NonNull RtspProtoLowMsgStructured output) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMessageType()";

		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			String requestTypeStr = tokens.nextToken();
			//
			output.messageType = Arrays.stream(RtspProtoMessageType.values())
					.filter(tmpType -> tmpType != RtspProtoMessageType.UNKNOWN)
					.filter(tmpType -> tmpType.name().equalsIgnoreCase(requestTypeStr))
					.findFirst()
					.orElse(RtspProtoMessageType.UNKNOWN);
			if (output.messageType == RtspProtoMessageType.UNKNOWN) {
				return;
			}
			tokens.nextToken();  // URL
			// we shall be tolerant here and ignore a missing PROTOCOL/VERSION token
			if (! tokens.hasMoreTokens()) {
				return;
			}
			String proto = tokens.nextToken();
			if (proto.equalsIgnoreCase(RTSP_RR_CMD_PROTOCOL_VERSION_1)) {
				output.rtspProtoVersion = RtspProtocolVersion.RTSP_V1_0;
			} else if (proto.equalsIgnoreCase(RTSP_RR_CMD_PROTOCOL_VERSION_2)) {
				output.rtspProtoVersion = RtspProtocolVersion.RTSP_V2_0;
			} else {
				output.rtspProtoVersion = RtspProtocolVersion.NONE;
				output.messageType = RtspProtoMessageType.UNKNOWN;
				logError(FNC_NAME, "invalid protocol/version '" + proto + "'");
			}
		} catch (NoSuchElementException e) {
			logError(FNC_NAME, "NoSuchElementException caught: " + e);
			output.messageType = RtspProtoMessageType.UNKNOWN;
		}
	}

	private void parseRequestResourceUrl(@NonNull String requestLine, @NonNull RtspProtoLowMsgStructured output) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseRequestResourceUrl()";

		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			tokens.nextToken();  // messageType
			String currentUrl = tokens.nextToken();

			boolean isRtsps = currentUrl.startsWith(RTSPS_URL_PROTOCOL + "://");
			if (! (currentUrl.startsWith(RTSP_URL_PROTOCOL + "://") || isRtsps)) {
				logError(FNC_NAME, "invalid protocol in URL '" + currentUrl + "'");
				output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
				return;
			}
			// rewrite the URL to get rid of any query parameters or fragments or userinfo (username + password)
			URI tmpUri = URI.create(currentUrl);
			if (tmpUri.getUserInfo() != null && tmpUri.getUserInfo().split(":").length == 2) {
				output.authUser = tmpUri.getUserInfo().split(":")[0];
				output.authPlainPassword = tmpUri.getUserInfo().split(":")[1];
			}
			int tmpPort = tmpUri.getPort();
			currentUrl = (isRtsps ? RTSPS_URL_PROTOCOL : RTSP_URL_PROTOCOL) +
					"://" + tmpUri.getHost() +
					(tmpPort != -1 ? ":" + tmpUri.getPort() : "") + tmpUri.getPath();
			if (tmpUri.getQuery() != null) {
				try {
					extractResourceUrlQueryParam(currentUrl + "?" + tmpUri.getQuery(), output);
				} catch (RtspInvalidUriException e) {
					output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
					return;
				}
			}

			//
			if (currentUrl.length() > RTSP_MAX_RESOURCE_URL_LENGTH) {
				logError(FNC_NAME, String.format("Resource URL too long (is=%d, max=%d), rejecting request",
						currentUrl.length(), RTSP_MAX_RESOURCE_URL_LENGTH));
				output.statusCode = RtspProtoStatusCode.URI_TOO_LONG;
				return;
			}

			//
			output.resourceUrl = currentUrl;
		} catch (NoSuchElementException e) {
			logError(FNC_NAME, "NoSuchElementException caught: " + e);
			output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
		}
	}

	private static void extractResourceUrlQueryParam(
				@NonNull String resourceUrlStr,
				@NonNull RtspProtoLowMsgStructured output
			) throws RtspInvalidUriException {
		URI rscUriObj = HostnameHelper.convertRtspUrlIntoURI(resourceUrlStr);
		String tmpQuery = rscUriObj.getQuery();
		if (tmpQuery == null) {
			return;
		}
		for (String tmpParam : tmpQuery.split("&")) {
			String[] tmpKv = tmpParam.split("=");
			if (tmpKv.length == 1) {
				output.queryParams.put(tmpKv[0].strip(), "");
			} else if (tmpKv.length == 2) {
				output.queryParams.put(tmpKv[0].strip(), tmpKv[1].strip());
			}
		}
	}

	private void parseHeaderLines(@NonNull List<@NonNull String> headerLines, @NonNull RtspProtoLowMsgStructured output) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLines()";

		for (String tmpHeaderLine : headerLines) {
			if (tmpHeaderLine.isBlank()) {
				break;
			}
			if (tmpHeaderLine.equals(" " + RTSP_RR_CMD_PROTOCOL_VERSION_1) ||
					tmpHeaderLine.equals(" " + RTSP_RR_CMD_PROTOCOL_VERSION_2)) {
				// ignore this non-standard header line
				continue;
			}
			String[] kv = tmpHeaderLine.split(":");
			if (kv.length < 2) {
				logWarn(FNC_NAME, "Invalid header (missing value): '" + tmpHeaderLine + "'");
				continue;
			}
			String tmpHeaderKeyStr = kv[0].strip();
			String tmpHeaderVal = tmpHeaderLine.substring(kv[0].length() + 1).strip();
			if (tmpHeaderVal.isBlank()) {
				logWarn(FNC_NAME, "Invalid header (empty value): '" + tmpHeaderLine + "'");
				continue;
			}
			try {
				parseHeaderLines_oneLine(tmpHeaderKeyStr, tmpHeaderVal, output);
			} catch (RtspInvalidRequestException e) {
				logWarn(FNC_NAME, e.getMessage());
				output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
				return;
			}
		}
	}

	private void parseHeaderLines_oneLine(
				@NonNull String hdKey,
				@NonNull String hdValue,
				@NonNull RtspProtoLowMsgStructured output
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLines_oneLine()";

		RtspProtoLowHeaderEntry entry = new RtspProtoLowHeaderEntry();
		if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_DES_ACCEPT)) {
			parseHeaderValue_describe_accept(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_XXX_AUTH)) {
			parseHeaderValue_com_auth(hdValue, output, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_XXX_CSEQ)) {
			parseHeaderValue_com_cseq(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_XXX_DATE)) {
			parseHeaderValue_com_date(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_XXX_KEYMGMT)) {
			parseHeaderValue_com_keymgmt(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_OPT_PUBLIC)) {
			parseHeaderValue_options_public(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_PLA_RANGE)) {
			parseHeaderValue_play_range(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_OPT_REQUIRE)) {
			parseHeaderValue_options_require(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_XXX_SESSION)) {
			parseHeaderValue_com_session(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_SET_TRANSPORT)) {
			parseHeaderValue_setup_transport(hdValue, entry);
		} else if (hdKey.equalsIgnoreCase(RTSP_RR_HEADER_TOKEN_XXX_USERAGENT)) {
			parseHeaderValue_com_useragent(hdValue, entry);
		} else {
			logWarn(FNC_NAME, "Received unknown header: '" + hdKey + "'");
			return;
		}
		output.headers.put(entry.hdKeyEn, entry);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseHeaderValue_describe_accept(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry)
			throws RtspInvalidRequestException {
		if (! RTSP_RR_HEADER_PARAM_VAL_XXX_CT_SDP.equalsIgnoreCase(hdValue)) {
			throw new RtspInvalidRequestException("Invalid Accept header value: '" + hdValue + "'");
		}
		entry.hdValAccept.acceptStr = hdValue;
		entry.hdKeyEn = RtspProtoLowHeaderKey.ACCEPT;
	}

	private void parseHeaderValue_com_auth(
				@NonNull String hdValue,
				@NonNull RtspProtoLowMsgStructured output,
				@NonNull RtspProtoLowHeaderEntry entry
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_auth()";

		// e.g. 'Authorization: Digest username="admin", realm="Abcdef Some", nonce="xxx", uri="rtsp://xxx:88/videoMain", response="xxx"'
		if (! hdValue.toLowerCase().startsWith(RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.toLowerCase())) {
			throw new RtspInvalidRequestException("Invalid Auth header prefix");
		}
		hdValue = hdValue.substring(RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.length()).strip();
		StringTokenizer tokens = new StringTokenizer(hdValue, ",");
		boolean haveUser = false;
		boolean haveRealm = false;
		boolean haveNonce = false;
		boolean haveUri = false;
		boolean haveResp = false;
		while (tokens.hasMoreTokens()) {
			String curTokenAsIs = tokens.nextToken().strip();
			String curTokenLc = curTokenAsIs.toLowerCase();
			if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER.toLowerCase())) {
				output.authUser = extractKeyValue(curTokenAsIs, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER);
				haveUser = true;  // tolerate empty username now and reject it later
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM.toLowerCase())) {
				entry.hdValAuth.authRealmClient = extractKeyValue(curTokenAsIs, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM);
				haveRealm = (! entry.hdValAuth.authRealmClient.isBlank());
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE.toLowerCase())) {
				entry.hdValAuth.authNonceClient = extractKeyValue(curTokenAsIs, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE);
				entry.hdValAuth.authNonceClient = entry.hdValAuth.authNonceClient.toLowerCase();
				haveNonce = (! entry.hdValAuth.authNonceClient.isBlank());
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI.toLowerCase())) {
				entry.hdValAuth.authUri = extractKeyValue(curTokenAsIs, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI);
				haveUri = (! entry.hdValAuth.authUri.isBlank());
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP.toLowerCase())) {
				entry.hdValAuth.authResp = extractKeyValue(curTokenAsIs, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP);
				entry.hdValAuth.authResp = entry.hdValAuth.authResp.toLowerCase();
				haveResp = true;  // tolerate empty challenge-response now and reject it later
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO.toLowerCase())) {
				String tmpAlgo = extractKeyValue(curTokenAsIs, RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO);
				if (! tmpAlgo.equalsIgnoreCase(RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_ALGO_MD5)) {
					throw new RtspInvalidRequestException("Unsupported Auth Algorithm: '" + tmpAlgo + "'");
				}
				entry.hdValAuth.authAlgo = RtspProtoLowHeaderTypeAuth.AuthAlgo.MD5;
			} else {
				logWarn(FNC_NAME, "Unknown Auth parameter: '" + curTokenAsIs + "'");
			}
		}

		if (! (haveUser && haveRealm && haveNonce && haveUri && haveResp)) {
			throw new RtspInvalidRequestException("Missing required Auth parameters");
		}
		entry.hdKeyEn = RtspProtoLowHeaderKey.AUTH;
	}

	private void parseHeaderValue_com_cseq(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry)
			throws RtspInvalidRequestException {
		try {
			entry.hdValCseq.cseqNr = Integer.parseInt(hdValue);
		} catch (NumberFormatException e) {
			throw new RtspInvalidRequestException("Invalid CSeq format: '" + hdValue + "'");
		}
		if (entry.hdValCseq.cseqNr < 0) {
			throw new RtspInvalidRequestException("Invalid CSeq value: " + entry.hdValCseq.cseqNr);
		}
		entry.hdKeyEn = RtspProtoLowHeaderKey.CSEQ;
	}

	private void parseHeaderValue_com_date(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry) {
		entry.hdValDate.dateStr = hdValue;
		entry.hdKeyEn = RtspProtoLowHeaderKey.DATE;
	}

	private void parseHeaderValue_com_keymgmt(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry)
			throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_keymgmt()";

		// e.g. 'KeyMgmt: prot=mikey; uri="rtsp://.../streamid00"; data="[BASE64 ENCODED DATA]"'
		StringTokenizer tokens = new StringTokenizer(hdValue, ";");
		while (tokens.hasMoreTokens()) {
			String curTokenAsIs = tokens.nextToken().strip();
			String curTokenLc = curTokenAsIs.toLowerCase();
			if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_KM_PROT.toLowerCase())) {
				String tmpSub = curTokenAsIs.substring(RTSP_RR_HEADER_PARAM_KEY_XXX_KM_PROT.length());
				if (! tmpSub.equalsIgnoreCase(RTSP_RR_HEADER_PARAM_VAL_XXX_KM_MIKEY)) {
					throw new RtspInvalidRequestException("Invalid Keymgmt protocol '" + tmpSub + "'");
				}
				entry.hdValKeymgmt.proto = RtspProtoLowHeaderTypeKeymgmt.KeymgmtProto.MIKEY;
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_KM_DATA.toLowerCase())) {
				entry.hdValKeymgmt.dataStr = extractKeyValue(curTokenAsIs, RTSP_RR_HEADER_PARAM_KEY_XXX_KM_DATA);
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_XXX_KM_URI.toLowerCase())) {
				entry.hdValKeymgmt.uriStr = extractKeyValue(curTokenAsIs, RTSP_RR_HEADER_PARAM_KEY_XXX_KM_URI);
			} else {
				logWarn(FNC_NAME, "Unknown Keymgmt parameter: '" + curTokenAsIs + "'");
			}
		}

		entry.hdKeyEn = RtspProtoLowHeaderKey.KEYMGMT;
	}

	private void parseHeaderValue_options_public(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_options_public()";

		/*
		 * Example:
		 *   "Public: SETUP, PLAY, PAUSE, TEARDOWN, DESCRIBE, OPTIONS, SET_PARAMETER"
		 */
		for (String tmpOption : hdValue.split(",")) {
			tmpOption = tmpOption.strip();
			try {
				RtspProtoMessageType tmpEn = RtspProtoMessageType.valueOf(tmpOption);
				entry.hdValPublic.messageTypes.add(tmpEn);
			} catch (IllegalArgumentException e) {
				logWarn(FNC_NAME, "Unknown option: '" + tmpOption + "'");
			}
		}
		entry.hdKeyEn = RtspProtoLowHeaderKey.PUBLIC;
	}

	private void parseHeaderValue_play_range(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry) {
		entry.hdValRange.rangeStr = hdValue;
		entry.hdKeyEn = RtspProtoLowHeaderKey.RANGE;
	}

	private void parseHeaderValue_options_require(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry) {
		entry.hdValRequire.requireStr = hdValue;
		entry.hdKeyEn = RtspProtoLowHeaderKey.REQUIRE;
	}

	private void parseHeaderValue_com_session(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry) {
		entry.hdValSession.sessionIdStr = hdValue;
		entry.hdKeyEn = RtspProtoLowHeaderKey.SESSION;
	}

	private void parseHeaderValue_setup_transport(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry)
			throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_setup_transport()";

		//logDebug(FNC_NAME, "Transport='" + hdValue + "'");
		// e.g. 'RTP/AVP;unicast;client_port=1050-1051'
		StringTokenizer tokens = new StringTokenizer(hdValue, ";");
		while (tokens.hasMoreTokens()) {
			String curTokenAsIs = tokens.nextToken().strip();
			String curTokenLc = curTokenAsIs.toLowerCase();
			if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP1.equalsIgnoreCase(curTokenAsIs) ||
					RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP2.equalsIgnoreCase(curTokenAsIs)) {
				entry.hdValTransport.tpIsUdp = true;
				entry.hdValTransport.tpIsEncr = false;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP1.equalsIgnoreCase(curTokenAsIs) ||
					RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP2.equalsIgnoreCase(curTokenAsIs)) {
				entry.hdValTransport.tpIsUdp = true;
				entry.hdValTransport.tpIsEncr = true;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP.equalsIgnoreCase(curTokenAsIs)) {
				entry.hdValTransport.tpIsUdp = false;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPTCP.equalsIgnoreCase(curTokenAsIs)) {
				entry.hdValTransport.tpIsUdp = false;
				entry.hdValTransport.tpIsEncr = true;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_UNICAST.equalsIgnoreCase(curTokenAsIs)) {
				entry.hdValTransport.tpIsUnicast = true;
			} else if (RTSP_RR_HEADER_PARAM_VAL_SET_TP_MULTICAST.equalsIgnoreCase(curTokenAsIs)) {
				entry.hdValTransport.tpIsUnicast = false;
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT.toLowerCase())) {
				String tmpSub = curTokenAsIs.substring(RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT.length());
				String[] tmpPorts = tmpSub.split("-");
				if (tmpPorts.length != 2) {
					throw new RtspInvalidRequestException("Invalid Transport parameter: '" + curTokenAsIs + "' - " +
							"cannot parse ports, expected two ports");
				}
				try {
					entry.hdValTransport.tpClientDestUdpPortRtp = Integer.parseInt(tmpPorts[0]);
					entry.hdValTransport.tpClientDestUdpPortRtcp = Integer.parseInt(tmpPorts[1]);
				} catch (NumberFormatException e) {
					throw new RtspInvalidRequestException("Invalid Transport parameter: '" + curTokenAsIs + "' - " +
							"cannot parse ports, invalid format");
				}
				entry.hdValTransport.tpIsInterleaved = false;
			} else if (curTokenLc.startsWith(RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED.toLowerCase())) {
				String tmpSub = curTokenAsIs.substring(RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED.length());
				String[] tmpPorts = tmpSub.split("-");
				if (tmpPorts.length != 2) {
					throw new RtspInvalidRequestException("Invalid Transport parameter: '" + curTokenAsIs + "' - " +
							"cannot parse channels, expected two ports");
				}
				try {
					entry.hdValTransport.tpClientDestTcpChannRtp = Integer.parseInt(tmpPorts[0]);
					entry.hdValTransport.tpClientDestTcpChannRtcp = Integer.parseInt(tmpPorts[1]);
				} catch (NumberFormatException e) {
					throw new RtspInvalidRequestException("Invalid Transport parameter: '" + curTokenAsIs + "' - " +
							"cannot parse channels, invalid format");
				}
				entry.hdValTransport.tpIsInterleaved = true;
				/*logDebug(FNC_NAME, "interleaved RTP=" + entry.hdValTransport.tpClientDestTcpChannRtp +
						", RTCP=" + entry.hdValTransport.tpClientDestTcpChannRtcp);*/
			} else {
				logWarn(FNC_NAME, "Unknown Transport parameter: '" + curTokenAsIs + "'");
			}
		}

		entry.hdKeyEn = RtspProtoLowHeaderKey.TRANSPORT;
	}

	private void parseHeaderValue_com_useragent(@NonNull String hdValue, @NonNull RtspProtoLowHeaderEntry entry) {
		/*
		 * GStreamer (Rocky Linux 10): GStreamer/1.24.11
		 * GStreamer (KUbuntu 24): GStreamer/1.24.2
		 * VLC (Rocky Linux 10): LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)
		 * FFplay (Rocky Linux 10): Lavf61.7.100
		 * VLC (Windows): LibVLC/3.0.21 (LIVE555 Streaming Media v2016.11.28)
		 * VLC (macOS x86):
		 *   LibVLC/3.0.23 (LIVE555 Streaming Media v2016.11.28)
		 *   RealMedia Player Version 6.0.9.1235 (linux-2.0-libc6-i386-gcc2.95)
		 * RTSP Player (Windows): Lavf59.27.100
		 * Win RTSP Player (Windows): RTSPClient v1.0.16.0615 (LIVE555 Streaming Media v2016.05.20)
		 */
		entry.hdValUserAgent.userAgentStr = hdValue;
		entry.hdKeyEn = RtspProtoLowHeaderKey.USERAGENT;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String extractKeyValue(@NonNull String inputStr, @NonNull String key) {
		return inputStr.substring(key.length())
				.replace("\"", "").replace("'", "").strip();
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

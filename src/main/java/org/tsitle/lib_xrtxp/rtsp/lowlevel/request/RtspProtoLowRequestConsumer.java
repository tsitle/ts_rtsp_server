package org.tsitle.lib_xrtxp.rtsp.lowlevel.request;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.lib_xrtxp.common.helpers.HostnameHelper;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidUriException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidRequestException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSkippedHeaderException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.*;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.helper.RtspLowInvalidRrException;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.helper.RtspLowParserHelper;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;

import java.net.URI;
import java.util.*;

public final class RtspProtoLowRequestConsumer {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowRequestConsumer(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredRequest parseMessage(@NonNull RtspProtoLowMsgRaw input) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMessage()";

		RtspProtoHighMsgStructuredRequest resObj = new RtspProtoHighMsgStructuredRequest();
		if (! input.readSuccess) {
			return resObj;
		}

		// read messageType from the requestLine
		try {
			parseMessageTypeAndProtoVers(input.mainLine, resObj);
		} catch (RtspProtoInvalidRequestException e) {
			logWarn(FNC_NAME, e.getMessage());
			resObj.messageType = RtspProtoMessageType.UNKNOWN;
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
		if (resObj.statusCode != RtspProtoStatusCode.OK) {
			return resObj;
		}

		// check CSeq
		Optional<Long> tmpOptCseq = resObj.getHeaderCseq();
		if (tmpOptCseq.isEmpty()) {
			logWarn(FNC_NAME, "Missing CSeq header in request");
			resObj.statusCode = RtspProtoStatusCode.BAD_REQUEST;
			return resObj;
		}

		// parse body
		if (! input.body.isBlank()) {
			parseBody(input.body, resObj);
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void parseMessageTypeAndProtoVers(@NonNull String requestLine, @NonNull RtspProtoHighMsgStructuredRequest output)
			throws RtspProtoInvalidRequestException {
		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			String requestTypeStr = tokens.nextToken();
			//
			output.messageType = RtspProtoMessageType.of(requestTypeStr);
			if (output.messageType == RtspProtoMessageType.UNKNOWN) {
				output.statusCode = RtspProtoStatusCode.METHOD_NOT_ALLOWED;
				throw new RtspProtoInvalidRequestException("Unknown request type in requestLine '" + requestLine + "'");
			}
			tokens.nextToken();  // URL
			// we shall be tolerant here and ignore a missing PROTOCOL/VERSION token
			if (! tokens.hasMoreTokens()) {
				return;
			}
			String tmpProtoStr = tokens.nextToken();
			output.rtspProtoVersion = RtspProtocolVersion.of(tmpProtoStr);
			if (output.rtspProtoVersion == RtspProtocolVersion.NONE) {
				output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
				throw new RtspProtoInvalidRequestException("Invalid protocol/version '" + tmpProtoStr + "'");
			}
		} catch (NoSuchElementException e) {
			output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
			throw new RtspProtoInvalidRequestException("Missing element in request line '" + requestLine + "'");
		}
	}

	private void parseRequestResourceUrl(@NonNull String requestLine, @NonNull RtspProtoHighMsgStructuredRequest output) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseRequestResourceUrl()";

		try {
			StringTokenizer tokens = new StringTokenizer(requestLine);
			tokens.nextToken();  // messageType
			String currentUrl = tokens.nextToken();

			boolean isRtsps = currentUrl.startsWith(RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL + "://");
			if (! (currentUrl.startsWith(RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL + "://") || isRtsps)) {
				logWarn(FNC_NAME, "invalid protocol in URL '" + currentUrl + "'");
				output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
				return;
			}
			// rewrite the URL to get rid of any query parameters, fragments and userinfo (username + password)
			URI tmpUri = URI.create(currentUrl);
			if (tmpUri.getUserInfo() != null && tmpUri.getUserInfo().split(":").length == 2) {
				output.authUser = tmpUri.getUserInfo().split(":")[0];
				output.authPlainPassword = tmpUri.getUserInfo().split(":")[1];
			}
			int tmpPort = tmpUri.getPort();
			currentUrl = (isRtsps ? RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL : RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL) +
					"://" + (tmpUri.getHost() == null ? "" : tmpUri.getHost()) +
					(tmpPort > 0 ? ":" + tmpUri.getPort() : "") + (tmpUri.getPath() == null ? "" : tmpUri.getPath());
			if (tmpUri.getQuery() != null) {
				try {
					extractResourceUrlQueryParam(currentUrl + "?" + tmpUri.getQuery(), output);
				} catch (RtspProtoInvalidUriException e) {
					output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
					return;
				}
			}

			//
			if (currentUrl.length() > RtspProtoLowMsgConstants.RTSP_MAX_RESOURCE_URL_LENGTH) {
				logWarn(FNC_NAME, String.format("Resource URL too long (is=%d, max=%d), rejecting request",
						currentUrl.length(), RtspProtoLowMsgConstants.RTSP_MAX_RESOURCE_URL_LENGTH));
				output.statusCode = RtspProtoStatusCode.URI_TOO_LONG;
				return;
			}

			//
			output.resourceUrl = currentUrl;
		} catch (NoSuchElementException e) {
			logWarn(FNC_NAME, "Missing element in request line '" + requestLine + "'");
			output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
		}
	}

	private static void extractResourceUrlQueryParam(
				@NonNull String resourceUrlStr,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidUriException {
		URI rscUriObj;
		try {
			rscUriObj = HostnameHelper.convertRtspUrlIntoURI(resourceUrlStr);
		} catch (HostnameHelperInvalidUriException e) {
			throw new RtspProtoInvalidUriException(e.getMessage());
		}
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

	// -----------------------------------------------------------------------------------------------------------------

	private void parseHeaderLines(@NonNull List<@NonNull String> headerLines, @NonNull RtspProtoHighMsgStructuredRequest output) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLines()";

		for (String tmpHeaderLine : headerLines) {
			if (tmpHeaderLine.isBlank()) {
				break;
			}
			if (tmpHeaderLine.equals(" " + RtspProtocolVersion.RTSP_V1.getStrValue()) ||
					tmpHeaderLine.equals(" " + RtspProtocolVersion.RTSP_V2.getStrValue())) {
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
			} catch (RtspProtoInvalidRequestException | RtspLowInvalidRrException e) {
				logWarn(FNC_NAME, e.getMessage());
				output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
				return;
			} catch (RtspProtoSkippedHeaderException e) {
				logWarn(FNC_NAME, "Skipped header: " + e.getMessage());
			}
		}
	}

	private void parseHeaderLines_oneLine(
				@NonNull String hdKeyStr,
				@NonNull String hdValue,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspProtoInvalidRequestException, RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLines_oneLine()";

		RtspProtoHeaderEntryRequest entry = new RtspProtoHeaderEntryRequest();
		RtspHeaderKey hdKeyEn = RtspHeaderKey.of(hdKeyStr);
		switch (hdKeyEn) {
			case ACCEPT -> parseHeaderValue_describe_accept(output.messageType, hdValue, entry);
			case AUTH_CLIENT -> parseHeaderValue_com_auth_client(hdValue, entry);
			case CONNECTION -> parseHeaderValue_com_connection(hdValue, entry);
			case CONTENT_BASE -> parseHeaderValue_announce_contbase(output.messageType, hdValue, entry);
			case CONTENT_ENC -> parseHeaderValue_com_contenc(output.messageType, hdValue, entry);
			case CONTENT_LANG -> parseHeaderValue_com_contlang(output.messageType, hdValue, entry);
			case CONTENT_LEN -> parseHeaderValue_com_contlen(output.messageType, hdValue, entry);
			case CONTENT_TYPE -> parseHeaderValue_com_conttype(output.messageType, hdValue, entry);
			case CSEQ -> parseHeaderValue_com_cseq(hdValue, entry);
			case DATE -> parseHeaderValue_com_date(hdValue, entry);
			case KEYMGMT -> parseHeaderValue_com_keymgmt(output.messageType, hdValue, output.resourceUrl, entry);
			case PROXY_REQU -> parseHeaderValue_com_proxyrequ(hdValue, entry);
			case RANGE -> parseHeaderValue_play_range(output.messageType, hdValue, entry);
			case REQUIRE -> parseHeaderValue_com_require(hdValue, entry);
			case SERVER -> parseHeaderValue_com_server(hdValue, entry);
			case SESSION -> parseHeaderValue_com_session(hdValue, entry);
			case TRANSPORT -> parseHeaderValue_setup_transport(output.messageType, hdValue, entry);
			case USERAGENT -> parseHeaderValue_com_useragent(hdValue, entry);
			default -> {
				logWarn(FNC_NAME, "Received unknown/invalid header: '" + hdKeyStr + "'");
				return;
			}
		}
		entry.setHdKey(hdKeyEn);
		output.headers.put(entry.getHdKey(), entry);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseHeaderValue_describe_accept(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspProtoInvalidRequestException, RtspProtoSkippedHeaderException {
		if (messageType != RtspProtoMessageType.DESCRIBE) {
			throw new RtspProtoSkippedHeaderException("Accept header only allowed in DESCRIBE requests");
		}
		entry.hdValAccept.rtspMimeType = RtspMimeType.of(hdValue);
		if (entry.hdValAccept.rtspMimeType == RtspMimeType.NONE) {
			throw new RtspProtoInvalidRequestException("Invalid Accept value: '" + hdValue + "'");
		}
		if (entry.hdValAccept.rtspMimeType != RtspMimeType.SDP) {
			throw new RtspProtoInvalidRequestException("Unsupported Accept value: " + entry.hdValAccept.rtspMimeType);
		}
	}

	private void parseHeaderValue_com_auth_client(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_auth_client()";

		/*
		 * Example:
		 *   "Authorization: Digest username=\"admin\", realm=\"Abcdef Some\", nonce=\"xxx\", uri=\"rtsp://xxx:88/videoMain\", response=\"xxx\""
		 *   or
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"...\", response=\"...\", algorithm=\"MD5\""
		 */
		if (! hdValue.toLowerCase()
				.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.toLowerCase())) {
			throw new RtspProtoInvalidRequestException("Invalid Auth header prefix");
		}
		hdValue = hdValue.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.length()).strip();
		StringTokenizer tokens = new StringTokenizer(hdValue, ",");
		boolean haveUser = false;
		boolean haveRealm = false;
		boolean haveNonce = false;
		boolean haveUri = false;
		boolean haveResp = false;
		while (tokens.hasMoreTokens()) {
			String curTokenAsIs = tokens.nextToken().strip();
			String curTokenLc = curTokenAsIs.toLowerCase();
			if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER.toLowerCase())) {
				entry.hdValAuthClient.authUser =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER);
				haveUser = true;  // tolerate empty username now and reject it later
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM.toLowerCase())) {
				entry.hdValAuthClient.authRealm =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM);
				haveRealm = (! entry.hdValAuthClient.authRealm.isBlank());
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE.toLowerCase())) {
				entry.hdValAuthClient.authNonce =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE);
				entry.hdValAuthClient.authNonce = entry.hdValAuthClient.authNonce.toLowerCase();
				haveNonce = (! entry.hdValAuthClient.authNonce.isBlank());
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI.toLowerCase())) {
				entry.hdValAuthClient.authUri =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI);
				haveUri = (! entry.hdValAuthClient.authUri.isBlank());
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP.toLowerCase())) {
				entry.hdValAuthClient.authResp =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP);
				entry.hdValAuthClient.authResp = entry.hdValAuthClient.authResp.toLowerCase();
				haveResp = true;  // tolerate empty challenge-response now and reject it later
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO.toLowerCase())) {
				String tmpAlgoStr =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO);
				RtspAuthAlgo tmpAlgoEn = RtspAuthAlgo.of(tmpAlgoStr);
				if (tmpAlgoEn == RtspAuthAlgo.NONE) {
					throw new RtspProtoInvalidRequestException("Invalid Auth Algo '" + tmpAlgoStr + "'");
				}
			} else {
				logWarn(FNC_NAME, "Unknown Auth parameter: '" + curTokenAsIs + "'");
			}
		}

		if (! (haveUser && haveRealm && haveNonce && haveUri && haveResp)) {
			throw new RtspProtoInvalidRequestException("Missing required Auth parameters");
		}
	}

	private void parseHeaderValue_com_connection(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryRequest entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_connection(hdValue, entry.hdValConnection);
	}

	private void parseHeaderValue_announce_contbase(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspProtoSkippedHeaderException {
		if (messageType != RtspProtoMessageType.ANNOUNCE) {
			throw new RtspProtoSkippedHeaderException("Content-Base header only allowed in ANNOUNCE requests");
		}
		RtspLowParserHelper.helperParseHeaderValue_contbase(hdValue, entry.hdValContBase);
	}

	private void parseHeaderValue_com_contenc(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		allowOnlyAnnounceGetOrSetParameter("Content-Encoding", messageType);
		RtspLowParserHelper.helperParseHeaderValue_contenc(hdValue, entry.hdValContEnc);
	}

	private void parseHeaderValue_com_contlang(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspProtoSkippedHeaderException {
		allowOnlyAnnounceGetOrSetParameter("Content-Language", messageType);
		RtspLowParserHelper.helperParseHeaderValue_contlang(hdValue, entry.hdValContLang);
	}

	private void parseHeaderValue_com_contlen(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		allowOnlyAnnounceGetOrSetParameter("Content-Length", messageType);
		RtspLowParserHelper.helperParseHeaderValue_contlen(hdValue, entry.hdValContLen);
	}

	private void parseHeaderValue_com_conttype(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		allowOnlyAnnounceGetOrSetParameter("Content-Type", messageType);
		RtspLowParserHelper.helperParseHeaderValue_conttype(hdValue, entry.hdValContType);
	}

	private void parseHeaderValue_com_cseq(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryRequest entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_cseq(hdValue, entry.hdValCseq);
	}

	private void parseHeaderValue_com_date(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryRequest entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_date(hdValue, entry.hdValDate);
	}

	private void parseHeaderValue_com_keymgmt(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull String resourceUrl,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspProtoInvalidRequestException, RtspProtoSkippedHeaderException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_keymgmt()";

		if (messageType != RtspProtoMessageType.SETUP && messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspProtoSkippedHeaderException("Keymgmt header only allowed in SETUP/SET_PARAMETER requests");
		}
		/*
		 * Example:
		 *   "prot=mikey; uri=\"rtsp://.../streamid00\"; data=\"[BASE64 ENCODED DATA]\""
		 */

		String rawProt = "";
		String rawUri = "";
		String rawData = "";

		StringTokenizer tokens = new StringTokenizer(hdValue, ";");
		while (tokens.hasMoreTokens()) {
			String curTokenAsIs = tokens.nextToken().strip();
			String curTokenLc = curTokenAsIs.toLowerCase();
			if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_PROT.toLowerCase())) {
				rawProt = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_PROT.length());
				rawProt = rawProt.replace("\"", "").strip();  // just in case
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_DATA.toLowerCase())) {
				rawData = extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_DATA);
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_URI.toLowerCase())) {
				rawUri = extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_URI);
			} else {
				logWarn(FNC_NAME, "Unknown Keymgmt parameter: '" + curTokenAsIs + "'");
			}
		}

		if (rawProt.isBlank()) {
			throw new RtspProtoInvalidRequestException("Keymgmt Protocol must be set");
		}
		entry.hdValKeymgmt.proto = RtspKeymgmtProto.of(rawProt);
		if (entry.hdValKeymgmt.proto == RtspKeymgmtProto.NONE) {
			throw new RtspProtoInvalidRequestException("Invalid Keymgmt Protocol value: '" + rawProt + "'");
		}
		entry.hdValKeymgmt.uriStr = (rawUri.isBlank() ? resourceUrl : rawUri);
		if (rawData.isBlank()) {
			throw new RtspProtoInvalidRequestException("Keymgmt Data must be set");
		}
		entry.hdValKeymgmt.dataStr = rawData;
	}

	private void parseHeaderValue_com_proxyrequ(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryRequest entry) {
		parseRequiredFeatures(hdValue, entry.hdValProxyRequ.requiredFeatures);
	}

	private void parseHeaderValue_play_range(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspProtoSkippedHeaderException {
		if (messageType != RtspProtoMessageType.PLAY) {
			throw new RtspProtoSkippedHeaderException("Range header only allowed in PLAY requests");
		}
		RtspLowParserHelper.helperParseHeaderValue_range(hdValue, entry.hdValRange);
	}

	private void parseHeaderValue_com_require(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryRequest entry) {
		parseRequiredFeatures(hdValue, entry.hdValRequire.requiredFeatures);
	}

	private void parseHeaderValue_com_server(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryRequest entry) {
		entry.hdValServer.serverStr = hdValue;
	}

	private void parseHeaderValue_com_session(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryRequest entry)
			throws RtspLowInvalidRrException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_session()";

		List<@NonNull String> outputWarnings = new ArrayList<>();
		RtspLowParserHelper.helperParseHeaderValue_session(
				hdValue,
				true,
				entry.hdValSession,
				outputWarnings
			);
		for (String warning : outputWarnings) {
			logWarn(FNC_NAME, warning);
		}
	}

	private void parseHeaderValue_setup_transport(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryRequest entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_setup_transport()";

		if (messageType != RtspProtoMessageType.SETUP) {
			throw new RtspProtoSkippedHeaderException("Transport header only allowed in SETUP requests");
		}

		List<@NonNull String> outputWarnings = new ArrayList<>();
		RtspLowParserHelper.helperParseHeaderValue_transport(
				hdValue,
				true,
				entry.hdValTransport,
				outputWarnings
			);
		for (String warning : outputWarnings) {
			logWarn(FNC_NAME, warning);
		}
	}

	private void parseHeaderValue_com_useragent(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryRequest entry) {
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
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void allowOnlyAnnounceGetOrSetParameter(@NonNull String hdDesc, @NonNull RtspProtoMessageType messageType)
			throws RtspProtoSkippedHeaderException {
		if (messageType != RtspProtoMessageType.ANNOUNCE &&
				messageType != RtspProtoMessageType.GET_PARAMETER && messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspProtoSkippedHeaderException(hdDesc + " header is only valid for " +
					"ANNOUNCE/GET_PARAMETER/SET_PARAMETER requests");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseBody(@NonNull String bodyValue, @NonNull RtspProtoHighMsgStructuredRequest output) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseBody()";

		// Content-Length
		if (! output.headers.containsKey(RtspHeaderKey.CONTENT_LEN)) {
			return;
		}
		long contLenLong = output.headers.get(RtspHeaderKey.CONTENT_LEN).hdValContLen.contentLen.getLen32bit().orElseThrow();
		if (contLenLong == 0L) {
			return;
		}
		// Content-Language
		String tmpBodyContLang = "";
		if (output.headers.containsKey(RtspHeaderKey.CONTENT_LANG)) {
			tmpBodyContLang = output.headers.get(RtspHeaderKey.CONTENT_LANG).hdValContLang.contentLangStr;
		}

		if (! output.headers.containsKey(RtspHeaderKey.CONTENT_TYPE)) {
			logWarn(FNC_NAME, "Missing Content-Type header");
			output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
			return;
		}
		if (output.headers.containsKey(RtspHeaderKey.CONTENT_ENC) &&
				output.headers.get(RtspHeaderKey.CONTENT_ENC).hdValContEnc.contentEnc != RtspContentEncoding.NONE) {
			logWarn(FNC_NAME, "No other Content-Encoding than NONE is supported");
			output.statusCode = RtspProtoStatusCode.NOT_ACCEPTABLE;
			return;
		}

		switch (output.messageType) {
			case ANNOUNCE -> {
				if (output.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType != RtspMimeType.SDP) {
					logWarn(FNC_NAME, "Invalid Content-Type header value");
					output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
					return;
				}
				String[] tmpSplit = bodyValue.split(RtspProtoLowMsgConstants.CRLF);
				output.bodyAnnounceSdp.addAllSdpLinesAllRaw(Arrays.asList(tmpSplit));
				output.bodyAnnounceSdp.setContentLang(tmpBodyContLang);
			}
			case GET_PARAMETER, SET_PARAMETER -> {
				if (output.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType != RtspMimeType.PARAMETERS) {
					logWarn(FNC_NAME, "Invalid Content-Type header value");
					output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
					return;
				}
				try {
					for (String tmpBdLine : bodyValue.split(RtspProtoLowMsgConstants.CRLF)) {
						if (output.messageType == RtspProtoMessageType.GET_PARAMETER) {
							parseBody_getParamLine(tmpBdLine, output);
						} else {
							parseBody_setParamLine(tmpBdLine, output);
						}
					}
				} catch (RtspLowInvalidRrException e) {
					logWarn(FNC_NAME, "Invalid body line format for " + e.getMessage());
					output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
				}
				if (output.messageType == RtspProtoMessageType.SET_PARAMETER) {
					output.bodySetParamKv.setContentLang(tmpBodyContLang);
				}
			}
			default -> {
				logWarn(FNC_NAME, "Content-Type header only allowed in ANNOUNCE/GET_PARAMETER/SET_PARAMETER request");
				output.statusCode = RtspProtoStatusCode.BAD_REQUEST;
			}
		}
	}

	private void parseBody_getParamLine(@NonNull String bodyLine, @NonNull RtspProtoHighMsgStructuredRequest output) {
		/*
		 * Example:
		 *   "GET_PARAMETER rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "CSeq: 431"
		 *   "Content-Type: text/parameters"
		 *   "Session: 12345678"
		 *   "Content-Length: 15"
		 *   ""
		 *   "packets_received"
		 *   "jitter"
		 */
		String tmpParam = RtspLowParserHelper.helperCleanUpBodyLine(bodyLine);
		if (tmpParam.isBlank()) {
			return;
		}
		output.bodyGetParamNames.putParamName(tmpParam);
	}

	private void parseBody_setParamLine(@NonNull String bodyLine, @NonNull RtspProtoHighMsgStructuredRequest output)
			throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "SET_PARAMETER rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "CSeq: 421"
		 *   "Content-Length: 20"
		 *   "Content-Type: text/parameters"
		 *   ""
		 *   "barparam: barstuff"
		 */
		RtspLowParserHelper.helperParseBodyLine_keyValue(RtspProtoMessageType.SET_PARAMETER, bodyLine, output.bodySetParamKv);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void parseRequiredFeatures(@NonNull String hdValue, @NonNull Set<@NonNull String> requiredFeatures) {
		/*
		 * Example:
		 *   "Require: funky-feature" // "Proxy-Require: funky-feature"
		 * This header may be accompanied by a custom header like:
		 *   "Funky-Parameter: funkystuff"
		 * Unsupported features need to be handled by responding with a message like this:
		 *   "RTSP/1.0 551 Option not supported"
		 *   "CSeq: 302"
		 *   "Unsupported: funky-feature"
		 * See
		 *   https://datatracker.ietf.org/doc/html/rfc2326#section-12.32
		 */
		for (String tmpFeat : hdValue.split(",")) {
			tmpFeat = tmpFeat.strip().replace("'", "").replace("\"", "");
			requiredFeatures.add(tmpFeat.toLowerCase());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String extractKeyValue(@NonNull String inputStr, @NonNull String key) {
		return inputStr.substring(key.length())
				.replace("\"", "").replace("'", "").strip();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

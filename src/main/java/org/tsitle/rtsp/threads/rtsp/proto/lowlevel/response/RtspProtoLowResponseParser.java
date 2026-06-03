package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.response;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderTypeRtpinfo;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper.RtspLowInvalidRrException;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper.RtspLowParserHelper;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;

import java.util.*;

public final class RtspProtoLowResponseParser {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowResponseParser(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredResponse parseMessage(
				@NonNull RtspMessageType requestMessageType,
				@NonNull RtspProtoLowMsgRaw input
			) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseMessage()";

		RtspProtoHighMsgStructuredResponse resObj = new RtspProtoHighMsgStructuredResponse();
		resObj.messageType = requestMessageType;
		if (! input.readSuccess) {
			return resObj;
		}

		try {
			// read status code from the status line
			parseResponseStatusCode(input.mainLine, resObj);

			// parse header lines
			parseHeaderLines(input.headerLines, resObj);

			// parse body
			// @TODO
		} catch (RtspInvalidResponseException | RtspLowInvalidRrException e) {
			resObj.statusCode = RtspStatusCode.INTERNAL_SERVER_ERROR;
			logWarn(FNC_NAME, "Invalid response: " + e.getMessage());
		}
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void parseResponseStatusCode(@NonNull String statusLine, @NonNull RtspProtoHighMsgStructuredResponse output)
			throws RtspInvalidResponseException {
		/*
		 * Examples:
		 *   "RTSP/1.0 200 OK"
		 *  or
		 *   "RTSP/1.0 405 Method Not Allowed"
		 */
		try {
			StringTokenizer tokens = new StringTokenizer(statusLine);
			String tmpProtoStr = tokens.nextToken();  // Protocol version
			output.rtspProtoVersion = RtspProtocolVersion.of(tmpProtoStr);
			if (output.rtspProtoVersion == RtspProtocolVersion.NONE) {
				throw new RtspInvalidResponseException("Invalid protocol/version '" + tmpProtoStr + "'");
			}
			//
			String tmpStatCodeStr = tokens.nextToken();  // Status code
			int tmpStatCodeInt;
			try {
				tmpStatCodeInt = Integer.parseInt(tmpStatCodeStr);
			} catch (NumberFormatException e) {
				throw new RtspInvalidResponseException("Invalid Status Code: '" + tmpStatCodeStr + "' - " +
						"invalid format");
			}
			output.statusCode = RtspStatusCode.of(tmpStatCodeInt);
		} catch (NoSuchElementException e) {
			throw new RtspInvalidResponseException("Missing element in status line: '" + statusLine + "'");
		}
	}

	private void parseHeaderLines(@NonNull List<@NonNull String> headerLines, @NonNull RtspProtoHighMsgStructuredResponse output)
			throws RtspInvalidResponseException, RtspLowInvalidRrException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLines()";

		for (String tmpHeaderLine : headerLines) {
			if (tmpHeaderLine.isBlank()) {
				break;
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

			parseHeaderLines_oneLine(tmpHeaderKeyStr, tmpHeaderVal, output);
		}
	}

	private void parseHeaderLines_oneLine(
				@NonNull String hdKeyStr,
				@NonNull String hdValue,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspInvalidResponseException, RtspLowInvalidRrException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLines_oneLine()";

		RtspProtoHeaderEntryResponse entry = new RtspProtoHeaderEntryResponse();
		RtspHeaderKey hdKeyEn = RtspHeaderKey.of(hdKeyStr);
		switch (hdKeyEn) {
			case AUTH_SERVER -> parseHeaderValue_com_auth_server(hdValue, entry);
			case CONTENT_BASE -> parseHeaderValue_describe_contbase(hdValue, entry);
			case CONTENT_LEN -> parseHeaderValue_com_contlen(hdValue, entry);
			case CONTENT_TYPE -> parseHeaderValue_com_conttype(hdValue, entry);
			case CSEQ -> parseHeaderValue_com_cseq(hdValue, entry);
			case DATE -> parseHeaderValue_com_date(hdValue, entry);
			case PUBLIC -> parseHeaderValue_options_public(hdValue, entry);
			case RANGE -> parseHeaderValue_play_range(hdValue, entry);
			case RTPINFO -> parseHeaderValue_play_rtpinfo(hdValue, entry, output.rtspProtoVersion);
			case SERVER -> parseHeaderValue_com_server(hdValue, entry);
			case SESSION -> parseHeaderValue_com_session(hdValue, entry);
			case TRANSPORT -> parseHeaderValue_setup_transport(hdValue, entry);
			case UNSUPPORTED -> parseHeaderValue_com_unsupported(hdValue, entry);
			default -> {
				logWarn(FNC_NAME, "Received unknown/invalid header: '" + hdKeyStr + "'");
				return;
			}
		}
		output.headers.put(entry.getHdKey(), entry);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseHeaderValue_com_auth_server(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_auth_server()";

		// e.g. 'WWW-Authenticate: Digest realm="Abcdef Some", nonce="xxx", algorithm="MD5"'
		if (! hdValue.toLowerCase()
				.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.toLowerCase())) {
			throw new RtspInvalidResponseException("Invalid Auth header prefix");
		}
		hdValue = hdValue.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.length()).strip();
		StringTokenizer tokens = new StringTokenizer(hdValue, ",");
		boolean haveRealm = false;
		boolean haveNonce = false;
		boolean haveAlgo = false;
		while (tokens.hasMoreTokens()) {
			String curTokenAsIs = tokens.nextToken().strip();
			String curTokenLc = curTokenAsIs.toLowerCase();
			if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM.toLowerCase())) {
				entry.hdValAuthServer.authRealm =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM);
				haveRealm = (! entry.hdValAuthServer.authRealm.isBlank());
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE.toLowerCase())) {
				entry.hdValAuthServer.authNonce =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE);
				entry.hdValAuthServer.authNonce = entry.hdValAuthServer.authNonce.toLowerCase();
				haveNonce = (! entry.hdValAuthServer.authNonce.isBlank());
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO.toLowerCase())) {
				String tmpAlgo =
						extractKeyValue(curTokenAsIs, RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO);
				entry.hdValAuthServer.authAlgo = RtspAuthAlgo.of(tmpAlgo);
				if (entry.hdValAuthServer.authAlgo == RtspAuthAlgo.NONE) {
					throw new RtspInvalidResponseException("Unsupported Auth Algorithm: '" + tmpAlgo + "'");
				}
				haveAlgo = true;
			} else {
				logWarn(FNC_NAME, "Unknown Auth parameter: '" + curTokenAsIs + "'");
			}
		}

		if (! (haveRealm && haveNonce && haveAlgo)) {
			throw new RtspInvalidResponseException("Missing required Auth parameters");
		}
		entry.setHdKey(RtspHeaderKey.AUTH_SERVER);
	}

	private void parseHeaderValue_describe_contbase(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry) {
		RtspLowParserHelper.helperParseHeaderValue_contbase(hdValue, entry.hdValContBase);
		entry.setHdKey(RtspHeaderKey.CONTENT_BASE);
	}

	private void parseHeaderValue_com_contlen(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_contlen(hdValue, entry.hdValContLen);
		entry.setHdKey(RtspHeaderKey.CONTENT_LEN);
	}

	private void parseHeaderValue_com_conttype(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_conttype(hdValue, entry.hdValContType);
		entry.setHdKey(RtspHeaderKey.CONTENT_TYPE);
	}

	private void parseHeaderValue_com_cseq(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_cseq(hdValue, entry.hdValCseq);
		entry.setHdKey(RtspHeaderKey.CSEQ);
	}

	private void parseHeaderValue_com_date(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_date(hdValue, entry.hdValDate);
		entry.setHdKey(RtspHeaderKey.DATE);
	}

	private void parseHeaderValue_options_public(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry) {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_options_public()";

		/*
		 * Example:
		 *   "Public: SETUP, PLAY, PAUSE, TEARDOWN, DESCRIBE, OPTIONS, SET_PARAMETER"
		 */
		for (String tmpOption : hdValue.split(",")) {
			tmpOption = tmpOption.strip();
			try {
				RtspMessageType tmpEn = RtspMessageType.valueOf(tmpOption);
				entry.hdValPublic.messageTypes.add(tmpEn);
			} catch (IllegalArgumentException e) {
				logWarn(FNC_NAME, "Unknown option: '" + tmpOption + "'");
			}
		}
		entry.setHdKey(RtspHeaderKey.PUBLIC);
	}

	private void parseHeaderValue_play_range(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry) {
		RtspLowParserHelper.helperParseHeaderValue_range(hdValue, entry.hdValRange);
		entry.setHdKey(RtspHeaderKey.RANGE);
	}

	private void parseHeaderValue_play_rtpinfo(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspInvalidResponseException {
		String[] tmpSplit = hdValue.split(",");
		if (tmpSplit.length == 0) {
			throw new RtspInvalidResponseException("RTP-Info header is empty");
		}
		if (tmpSplit.length > 2) {
			throw new RtspInvalidResponseException("RTP-Info header contains too many sub-streams");
		}
		entry.hdValRtpinfo.setSubStream1(
				parseRtpInfoForOneSubStream(tmpSplit[0], rtspProtocolVersion)
			);
		if (tmpSplit.length == 2) {
			entry.hdValRtpinfo.setSubStream2(
					parseRtpInfoForOneSubStream(tmpSplit[1], rtspProtocolVersion)
				);
		}
		entry.setHdKey(RtspHeaderKey.RTPINFO);
	}

	private RtspProtoHeaderTypeRtpinfo.@NonNull SubStream parseRtpInfoForOneSubStream(
				@NonNull String hdPartValue,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseRtpInfoForOneSubStream()";

		/*
		 * Example:
		 *   RTSP v1 (see https://datatracker.ietf.org/doc/html/rfc2326#section-12.33):
		 *     url=rtsp://foo.com/bar.file;seq=232433;rtptime=972948234
		 *   RTSP v2 (see https://datatracker.ietf.org/doc/html/rfc7826#section-13.4):
		 *     url="rtsp://example.com/audio" ssrc=0D12F123:seq=14783;rtptime=2345962545
		 */
		RtspProtoHeaderTypeRtpinfo.SubStream resObj = new RtspProtoHeaderTypeRtpinfo.SubStream();

		String rawUrlStr = "";
		String rawSeqNrStr = "";
		String rawSsrcHexStr = "";
		String rawTimeStr = "";

		for (String tmpElemAsIs : hdPartValue.split(";")) {
			String tmpElemLc = tmpElemAsIs.toLowerCase().strip();
			if (tmpElemLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_URL.toLowerCase())) {
				String tmpUrlElemFull = tmpElemAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_URL.length());
				if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V1) {
					rawUrlStr = tmpUrlElemFull;
				} else {
					// "\"rtsp://example.com/audio\" ssrc=0D12F123:seq=14783"
					String[] tmpUrlElemSplit = tmpUrlElemFull.split(" ");
					if (tmpUrlElemSplit.length == 0) {
						throw new RtspInvalidResponseException("RTP-Info header is malformed - URL/SSRC/SEQ element");
					}
					for (int tmpUrlElemIx = 0; tmpUrlElemIx < tmpUrlElemSplit.length; tmpUrlElemIx++) {
						String tmpUrlElemSubAsIs = tmpUrlElemSplit[tmpUrlElemIx];
						if (tmpUrlElemIx == 0) {
							rawUrlStr = tmpUrlElemSubAsIs;
							continue;
						}
						if (tmpUrlElemSubAsIs.isBlank()) {
							continue;
						}
						// "ssrc=0D12F123:seq=14783"
						String[] tmpSsrcSeqSplit = tmpUrlElemSubAsIs.split(":");
						if (tmpSsrcSeqSplit.length != 2) {
							throw new RtspInvalidResponseException("RTP-Info header is malformed - SSRC/SEQ element");
						}
						for (String tmpSsrcSeqSubElemAsIs : tmpSsrcSeqSplit) {
							String tmpSsrcSeqSubElemLc = tmpSsrcSeqSubElemAsIs.toLowerCase();
							if (tmpSsrcSeqSubElemLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SSRC.toLowerCase())) {
								rawSsrcHexStr = tmpSsrcSeqSubElemAsIs
										.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SSRC.length());
							} else if (tmpSsrcSeqSubElemLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SEQ.toLowerCase())) {
								rawSeqNrStr = tmpSsrcSeqSubElemAsIs
										.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SEQ.length());
							} else {
								logWarn(FNC_NAME, "Unknown RTP-Info parameter: '" + tmpSsrcSeqSubElemAsIs + "' in SSRC/SEQ element");
							}
						}
						break;
					}
				}
			} else if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V1 &&
					tmpElemLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SEQ.toLowerCase())) {
				rawSeqNrStr = tmpElemAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SEQ.length());
			} else if (tmpElemLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_RTPTIME.toLowerCase())) {
				rawTimeStr = tmpElemAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_RTPTIME.length());
			} else {
				logWarn(FNC_NAME, "Unknown RTP-Info parameter: '" + tmpElemAsIs + "'");
			}
		}

		// parse URL
		if (rawUrlStr.isBlank()) {
			throw new RtspInvalidResponseException("RTP-Info header is missing URL");
		}
		resObj.urlStr = rawUrlStr.strip().replace("'", "").replace("\"", "");
		if (resObj.urlStr.isBlank()) {
			throw new RtspInvalidResponseException("RTP-Info header is malformed - URL is empty");
		}
		// parse SeqNr
		if (rawSeqNrStr.isBlank()) {
			throw new RtspInvalidResponseException("RTP-Info header is missing SeqNr");
		}
		{
			final String fieldDesc = "RTP-Info parameter value for SeqNr";
			try {
				resObj.setSeqNr16bit(Integer.parseInt(rawSeqNrStr.strip()));
			} catch (NumberFormatException e) {
				throw new RtspInvalidResponseException("Cannot parse " + fieldDesc + ": '" + rawSeqNrStr + "'");
			} catch (IllegalArgumentException e) {
				throw new RtspInvalidResponseException("Invalid " + fieldDesc + ": '" + rawSeqNrStr + "': " +
						e.getMessage());
			}
		}
		// parse SSRC ID
		if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V2) {
			if (rawSsrcHexStr.isBlank()) {
				throw new RtspInvalidResponseException("RTP-Info header is missing SSRC");
			}
			final String fieldDesc = "RTP-Info parameter value for SSRC";
			try {
				resObj.setSsrcId32bit(
						RtspLowParserHelper.parseHexStringIntoLong(fieldDesc, rawSsrcHexStr)
					);
			} catch (RtspLowInvalidRrException e) {
				throw new RtspInvalidResponseException(e.getMessage());
			} catch (IllegalArgumentException e) {
				throw new RtspInvalidResponseException("Invalid " + fieldDesc + ": '" + rawSsrcHexStr + "': " +
						e.getMessage());
			}
		}
		// parse RtpTimestamp
		if (rawTimeStr.isBlank()) {
			throw new RtspInvalidResponseException("RTP-Info header is missing RtpTimestamp");
		}
		{
			final String fieldDesc = "RTP-Info parameter value for RtpTimestamp";
			try {
				resObj.setRtpTimestamp32bit(Long.parseLong(rawTimeStr.strip()));
			} catch (NumberFormatException e) {
				throw new RtspInvalidResponseException("Cannot parse " + fieldDesc + ": '" + rawTimeStr + "'");
			} catch (IllegalArgumentException e) {
				throw new RtspInvalidResponseException("Invalid " + fieldDesc + ": '" + rawTimeStr + "': " +
						e.getMessage());
			}
		}

		return resObj;
	}

	private void parseHeaderValue_com_server(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry) {
		entry.hdValServer.serverStr = hdValue;
		entry.setHdKey(RtspHeaderKey.SERVER);
	}

	private void parseHeaderValue_com_session(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_session()";

		List<@NonNull String> outputWarnings = new ArrayList<>();
		RtspLowParserHelper.helperParseHeaderValue_session(
				hdValue,
				false,
				entry.hdValSession,
				outputWarnings
			);

		for (String warning : outputWarnings) {
			logWarn(FNC_NAME, warning);
		}

		entry.setHdKey(RtspHeaderKey.SESSION);
	}

	private void parseHeaderValue_setup_transport(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_setup_transport()";

		List<@NonNull String> outputWarnings = new ArrayList<>();
		RtspLowParserHelper.helperParseHeaderValue_transport(
				hdValue,
				false,
				entry.hdValTransport,
				outputWarnings
			);
		for (String warning : outputWarnings) {
			logWarn(FNC_NAME, warning);
		}

		entry.setHdKey(RtspHeaderKey.TRANSPORT);
	}

	private void parseHeaderValue_com_unsupported(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry) {
		entry.hdValUnsupported.unsupportedOptionStr = hdValue;
		entry.setHdKey(RtspHeaderKey.UNSUPPORTED);
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

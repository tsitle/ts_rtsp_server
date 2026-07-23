package org.tsitle.lib_xrtxp.rtsp.lowlevel.response;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoStatusCode;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidResponseException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoSkippedHeaderException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderEntryResponse;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.RtspProtoHeaderTypeRtpinfo;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.*;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.helper.RtspLowInvalidRrException;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.helper.RtspLowParserHelper;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;

import java.util.*;

public final class RtspProtoLowResponseConsumer {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowResponseConsumer(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredResponse parseMessage(
				@NonNull RtspProtoMessageType requestMessageType,
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
			parseBody(input.body, resObj);

			// check headers and body
			postflightChecks(resObj);
		} catch (RtspProtoInvalidResponseException | RtspLowInvalidRrException e) {
			resObj.statusCode = RtspProtoStatusCode.INTERNAL_SERVER_ERROR;
			logWarn(FNC_NAME, "Invalid response: " + e.getMessage());
		}
		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void parseResponseStatusCode(@NonNull String statusLine, @NonNull RtspProtoHighMsgStructuredResponse output)
			throws RtspProtoInvalidResponseException {
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
				throw new RtspProtoInvalidResponseException("Invalid protocol/version '" + tmpProtoStr + "'");
			}
			//
			String tmpStatCodeStr = tokens.nextToken();  // Status code
			int tmpStatCodeInt;
			try {
				tmpStatCodeInt = Integer.parseInt(tmpStatCodeStr);
			} catch (NumberFormatException e) {
				throw new RtspProtoInvalidResponseException("Invalid Status Code: '" + tmpStatCodeStr + "' - " +
						"invalid format");
			}
			output.statusCode = RtspProtoStatusCode.of(tmpStatCodeInt);
		} catch (NoSuchElementException e) {
			throw new RtspProtoInvalidResponseException("Missing element in status line: '" + statusLine + "'");
		}
	}

	private void parseHeaderLines(@NonNull List<@NonNull String> headerLines, @NonNull RtspProtoHighMsgStructuredResponse output)
			throws RtspProtoInvalidResponseException, RtspLowInvalidRrException {
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

			try {
				parseHeaderLines_oneLine(tmpHeaderKeyStr, tmpHeaderVal, output);
			} catch (RtspProtoSkippedHeaderException e) {
				logWarn(FNC_NAME, "Skipped header: " + e.getMessage());
			}
		}
	}

	private void parseHeaderLines_oneLine(
				@NonNull String hdKeyStr,
				@NonNull String hdValue,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspProtoInvalidResponseException, RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderLines_oneLine()";

		RtspProtoHeaderEntryResponse entry = new RtspProtoHeaderEntryResponse();
		RtspHeaderKey hdKeyEn = RtspHeaderKey.of(hdKeyStr);
		switch (hdKeyEn) {
			case AUTH_SERVER -> parseHeaderValue_com_auth_server(hdValue, entry);
			case CONNECTION -> parseHeaderValue_com_connection(hdValue, entry);
			case CONTENT_BASE -> parseHeaderValue_describe_contbase(output.messageType, hdValue, entry);
			case CONTENT_ENC -> parseHeaderValue_com_contenc(output.messageType, hdValue, entry);
			case CONTENT_LANG -> parseHeaderValue_com_contlang(output.messageType, hdValue, entry);
			case CONTENT_LEN -> parseHeaderValue_com_contlen(output.messageType, hdValue, entry);
			case CONTENT_TYPE -> parseHeaderValue_com_conttype(output.messageType, hdValue, entry);
			case CSEQ -> parseHeaderValue_com_cseq(hdValue, entry);
			case DATE -> parseHeaderValue_com_date(hdValue, entry);
			case PUBLIC -> parseHeaderValue_options_public(output.messageType, hdValue, entry);
			case RANGE -> parseHeaderValue_play_range(output.messageType, hdValue, entry);
			case RTPINFO -> parseHeaderValue_play_rtpinfo(output.messageType, hdValue, entry, output.rtspProtoVersion);
			case SERVER -> parseHeaderValue_com_server(hdValue, entry);
			case SESSION -> parseHeaderValue_com_session(hdValue, entry);
			case TRANSPORT -> parseHeaderValue_setup_transport(output.messageType, hdValue, entry);
			case UNSUPPORTED -> parseHeaderValue_com_unsupported(hdValue, entry);
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

	private void parseHeaderValue_com_auth_server(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_com_auth_server()";

		/*
		 * Example:
		 *   "WWW-Authenticate: Digest realm=\"Abcdef Some\", nonce=\"xxx\", algorithm=\"MD5\""
		 */
		if (! hdValue.toLowerCase()
				.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX.toLowerCase())) {
			throw new RtspProtoInvalidResponseException("Invalid Auth header prefix");
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
					throw new RtspProtoInvalidResponseException("Unsupported Auth Algorithm: '" + tmpAlgo + "'");
				}
				haveAlgo = true;
			} else {
				logWarn(FNC_NAME, "Unknown Auth parameter: '" + curTokenAsIs + "'");
			}
		}

		if (! (haveRealm && haveNonce && haveAlgo)) {
			throw new RtspProtoInvalidResponseException("Missing required Auth parameters");
		}
	}

	private void parseHeaderValue_com_connection(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_connection(hdValue, entry.hdValConnection);
	}

	private void parseHeaderValue_describe_contbase(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspProtoSkippedHeaderException {
		if (messageType != RtspProtoMessageType.DESCRIBE) {
			throw new RtspProtoSkippedHeaderException("Content-Base header only allowed in DESCRIBE responses");
		}
		RtspLowParserHelper.helperParseHeaderValue_contbase(hdValue, entry.hdValContBase);
	}

	private void parseHeaderValue_com_contenc(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		allowOnlyDescribeGetSetParameter("Content-Encoding", messageType);
		RtspLowParserHelper.helperParseHeaderValue_contenc(hdValue, entry.hdValContEnc);
	}

	private void parseHeaderValue_com_contlang(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspProtoSkippedHeaderException {
		allowOnlyDescribeGetSetParameter("Content-Language", messageType);
		RtspLowParserHelper.helperParseHeaderValue_contlang(hdValue, entry.hdValContLang);
	}

	private void parseHeaderValue_com_contlen(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		allowOnlyDescribeGetSetParameter("Content-Length", messageType);
		RtspLowParserHelper.helperParseHeaderValue_contlen(hdValue, entry.hdValContLen);
	}

	private void parseHeaderValue_com_conttype(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		allowOnlyDescribeGetSetParameter("Content-Type", messageType);
		RtspLowParserHelper.helperParseHeaderValue_conttype(hdValue, entry.hdValContType);
	}

	private void parseHeaderValue_com_cseq(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_cseq(hdValue, entry.hdValCseq);
	}

	private void parseHeaderValue_com_date(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry)
			throws RtspLowInvalidRrException {
		RtspLowParserHelper.helperParseHeaderValue_date(hdValue, entry.hdValDate);
	}

	private void parseHeaderValue_options_public(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspProtoSkippedHeaderException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_options_public()";

		if (messageType != RtspProtoMessageType.OPTIONS) {
			throw new RtspProtoSkippedHeaderException("Public header only allowed in OPTIONS responses");
		}
		/*
		 * Example:
		 *   "Public: SETUP, PLAY, PAUSE, TEARDOWN, DESCRIBE, OPTIONS, SET_PARAMETER"
		 */
		for (String tmpOption : hdValue.split(",")) {
			tmpOption = tmpOption.strip();
			try {
				RtspProtoMessageType tmpEn = RtspProtoMessageType.valueOf(tmpOption);
				entry.hdValPublic.messageTypes.putMt(tmpEn);
			} catch (IllegalArgumentException e) {
				logWarn(FNC_NAME, "Unknown option: '" + tmpOption + "'");
			}
		}
	}

	private void parseHeaderValue_play_range(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		if (messageType != RtspProtoMessageType.PLAY) {
			throw new RtspProtoSkippedHeaderException("Range header only allowed in PLAY responses");
		}
		RtspLowParserHelper.helperParseHeaderValue_range(hdValue, entry.hdValRange);
	}

	private void parseHeaderValue_play_rtpinfo(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspProtoInvalidResponseException, RtspProtoSkippedHeaderException {
		if (messageType != RtspProtoMessageType.PLAY) {
			throw new RtspProtoSkippedHeaderException("RTP-Info header only allowed in PLAY responses");
		}
		String[] tmpSplit = hdValue.split(",");
		if (tmpSplit.length == 0) {
			throw new RtspProtoInvalidResponseException("RTP-Info header is empty");
		}
		if (tmpSplit.length > 2) {
			throw new RtspProtoInvalidResponseException("RTP-Info header contains too many sub-streams");
		}
		entry.hdValRtpinfo.setSubStream1(
				parseRtpInfoForOneSubStream(tmpSplit[0], rtspProtocolVersion)
			);
		if (tmpSplit.length == 2) {
			entry.hdValRtpinfo.setSubStream2(
					parseRtpInfoForOneSubStream(tmpSplit[1], rtspProtocolVersion)
				);
		}
	}

	private RtspProtoHeaderTypeRtpinfo.@NonNull SubStream parseRtpInfoForOneSubStream(
				@NonNull String hdPartValue,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspProtoInvalidResponseException {
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
						throw new RtspProtoInvalidResponseException("RTP-Info header is malformed - URL/SSRC/SEQ element");
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
							throw new RtspProtoInvalidResponseException("RTP-Info header is malformed - SSRC/SEQ element");
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
			throw new RtspProtoInvalidResponseException("RTP-Info header is missing URL");
		}
		resObj.urlStr = rawUrlStr.strip().replace("'", "").replace("\"", "");
		if (resObj.urlStr.isBlank()) {
			throw new RtspProtoInvalidResponseException("RTP-Info header is malformed - URL is empty");
		}
		// parse SeqNr
		if (rawSeqNrStr.isBlank()) {
			throw new RtspProtoInvalidResponseException("RTP-Info header is missing SeqNr");
		}
		{
			final String fieldDesc = "RTP-Info parameter value for SeqNr";
			try {
				resObj.seqNr.setSeqNr16bit(Integer.parseInt(rawSeqNrStr.strip()));
			} catch (NumberFormatException e) {
				throw new RtspProtoInvalidResponseException("Cannot parse " + fieldDesc + ": '" + rawSeqNrStr + "'");
			} catch (RtspProtoNumberRangeException e) {
				throw new RtspProtoInvalidResponseException("Invalid " + fieldDesc + ": '" + rawSeqNrStr + "': " +
						e.getMessage());
			}
		}
		// parse SSRC ID
		if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V2) {
			if (rawSsrcHexStr.isBlank()) {
				throw new RtspProtoInvalidResponseException("RTP-Info header is missing SSRC");
			}
			final String fieldDesc = "RTP-Info parameter value for SSRC";
			try {
				resObj.ssrcId.setId32bit(
						RtspLowParserHelper.helperParseHexStringIntoLong(fieldDesc, rawSsrcHexStr)
					);
			} catch (RtspLowInvalidRrException e) {
				throw new RtspProtoInvalidResponseException(e.getMessage());
			} catch (RtspProtoNumberRangeException e) {
				throw new RtspProtoInvalidResponseException("Invalid " + fieldDesc + ": '" + rawSsrcHexStr + "': " +
						e.getMessage());
			}
		}
		// parse RtpTimestamp
		if (rawTimeStr.isBlank()) {
			throw new RtspProtoInvalidResponseException("RTP-Info header is missing RtpTimestamp");
		}
		{
			final String fieldDesc = "RTP-Info parameter value for RtpTimestamp";
			try {
				resObj.rtpTimestamp.setTs32bit(Long.parseLong(rawTimeStr.strip()));
			} catch (NumberFormatException e) {
				throw new RtspProtoInvalidResponseException("Cannot parse " + fieldDesc + ": '" + rawTimeStr + "'");
			} catch (RtspProtoNumberRangeException e) {
				throw new RtspProtoInvalidResponseException("Invalid " + fieldDesc + ": '" + rawTimeStr + "': " +
						e.getMessage());
			}
		}

		return resObj;
	}

	private void parseHeaderValue_com_server(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry) {
		entry.hdValServer.serverStr = hdValue;
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
	}

	private void parseHeaderValue_setup_transport(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderEntryResponse entry
			) throws RtspLowInvalidRrException, RtspProtoSkippedHeaderException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseHeaderValue_setup_transport()";

		if (messageType != RtspProtoMessageType.SETUP) {
			throw new RtspProtoSkippedHeaderException("Transport header only allowed in SETUP responses");
		}

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
	}

	private void parseHeaderValue_com_useragent(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry) {
		entry.hdValUserAgent.userAgentStr = hdValue;
	}

	private void parseHeaderValue_com_unsupported(@NonNull String hdValue, @NonNull RtspProtoHeaderEntryResponse entry) {
		entry.hdValUnsupported.unsupportedFeatureStr = hdValue;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void allowOnlyDescribeGetSetParameter(@NonNull String hdDesc, @NonNull RtspProtoMessageType messageType)
			throws RtspProtoSkippedHeaderException {
		if (messageType != RtspProtoMessageType.DESCRIBE &&
				messageType != RtspProtoMessageType.GET_PARAMETER && messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspProtoSkippedHeaderException(hdDesc + " header is only valid for " +
					"DESCRIBE/GET_PARAMETER/SET_PARAMETER responses");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String extractKeyValue(@NonNull String inputStr, @NonNull String key) {
		return inputStr.substring(key.length())
				.replace("\"", "").replace("'", "").strip();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void parseBody(
				@NonNull String bodyValue,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspProtoInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".parseBody()";

		/*
		 * Example:
		 *   DESCRIBE:
		 *     "RTSP/1.0 200 OK"
		 *     "Content-Base: rtsp://example.com/fizzle/foo/"
		 *     "Content-Type: application/sdp"
		 *     "Content-Length: 1234"
		 *     ...
		 *     ""
		 *     "v=0"
		 *     "a=tool:TS RTSP Server/1.0"
		 *     ...
		 *   GET_PARAMETER:
		 *     "RTSP/1.0 200 OK"
		 *     "Content-Length: 1234"
		 *     "Content-Type: text/parameters"
		 *     ...
		 *     ""
		 *     "packets_received: 10"
		 *     "jitter: 0.3838"
		 *   GET_PARAMETER/SET_PARAMETER:
		 *     "RTSP/1.0 451 Invalid Parameter"
		 *     "Content-Length: 1234"
		 *     "Content-Type: text/parameters"
		 *     ...
		 *     ""
		 *     "packets_received"
		 *     "jitter"
		 */

		// Content-Length
		if (! output.headers.containsKey(RtspHeaderKey.CONTENT_LEN)) {
			return;
		}
		long contLenLong = output.headers.get(RtspHeaderKey.CONTENT_LEN).hdValContLen.contentLen.getLen32bit().orElseThrow();
		if (contLenLong == 0L) {
			return;
		}
		// Content-Type
		if (! output.headers.containsKey(RtspHeaderKey.CONTENT_TYPE)) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": Missing Content-Type header");
		}
		// Content-Encoding
		if (output.headers.containsKey(RtspHeaderKey.CONTENT_ENC) &&
				output.headers.get(RtspHeaderKey.CONTENT_ENC).hdValContEnc.contentEnc != RtspContentEncoding.NONE) {
			throw new RtspProtoInvalidResponseException(FNC_NAME + ": No other Content-Encoding than NONE is supported");
		}
		// Content-Language
		String tmpBodyContLang = "";
		if (output.headers.containsKey(RtspHeaderKey.CONTENT_LANG)) {
			tmpBodyContLang = output.headers.get(RtspHeaderKey.CONTENT_LANG).hdValContLang.contentLangStr;
		}

		switch (output.messageType) {
			case DESCRIBE -> {
				if (output.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType != RtspMimeType.SDP) {
					throw new RtspProtoInvalidResponseException(FNC_NAME + ": Invalid Content-Type header value");
				}
				String[] tmpSplit = bodyValue.split(RtspProtoLowMsgConstants.CRLF);
				output.bodyDescribeSdp.addAllSdpLinesAllRaw(Arrays.asList(tmpSplit));
				output.bodyDescribeSdp.setContentLang(tmpBodyContLang);
			}
			case GET_PARAMETER, SET_PARAMETER -> {
				if (output.headers.get(RtspHeaderKey.CONTENT_TYPE).hdValContType.contentType != RtspMimeType.PARAMETERS) {
					throw new RtspProtoInvalidResponseException(FNC_NAME + ": Invalid Content-Type header value");
				}
				try {
					for (String tmpBdLine : bodyValue.split(RtspProtoLowMsgConstants.CRLF)) {
						parseBody_getParamLine(tmpBdLine, output);
					}
				} catch (RtspLowInvalidRrException e) {
					throw new RtspProtoInvalidResponseException(FNC_NAME + ": Invalid body line format for " + e.getMessage());
				}
				if (output.messageType == RtspProtoMessageType.GET_PARAMETER) {
					output.bodyGetParamKv.setContentLang(tmpBodyContLang);
				}
			}
			default -> throw new RtspProtoInvalidResponseException(FNC_NAME + ": Content-Type header only allowed in " +
					"DESCRIBE/GET_PARAMETER response");
		}
	}

	private void parseBody_getParamLine(
				@NonNull String bodyLine,
				@NonNull RtspProtoHighMsgStructuredResponse output
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "barparam: barstuff"
		 *   or
		 *   "barparam"
		 */
		if (! bodyLine.contains(":")) {
			bodyLine = RtspLowParserHelper.helperCleanUpBodyLine(bodyLine);
			output.bodyGetSetInvalidParams.putParamName(bodyLine);
			return;
		}
		if (output.messageType == RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspLowInvalidRrException(output.messageType + ": '" + bodyLine + "'");
		}
		RtspLowParserHelper.helperParseBodyLine_keyValue(RtspProtoMessageType.GET_PARAMETER, bodyLine, output.bodyGetParamKv);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void postflightChecks(@NonNull RtspProtoHighMsgStructuredResponse output) throws RtspProtoInvalidResponseException {
		if (! output.headers.containsKey(RtspHeaderKey.CSEQ)) {
			throw new RtspProtoInvalidResponseException("Missing CSeq header");
		}
		//
		if (output.headers.containsKey(RtspHeaderKey.CONTENT_ENC) &&
				output.headers.get(RtspHeaderKey.CONTENT_ENC).hdValContEnc.contentEnc != RtspContentEncoding.NONE) {
			throw new RtspProtoInvalidResponseException("No other Content-Encoding than NONE is supported");
		}
		//
		if (output.messageType == RtspProtoMessageType.DESCRIBE) {
			final String errMsgSuffix = " for " + output.messageType + " message";
			if (output.statusCode != RtspProtoStatusCode.OK) {
				return;
			}
			if (! output.headers.containsKey(RtspHeaderKey.CONTENT_BASE)) {
				throw new RtspProtoInvalidResponseException("Missing Content-Base header" + errMsgSuffix);
			}
			if (! output.headers.containsKey(RtspHeaderKey.CONTENT_TYPE)) {
				throw new RtspProtoInvalidResponseException("Missing Content-Type header" + errMsgSuffix);
			}
			if (! output.headers.containsKey(RtspHeaderKey.CONTENT_LEN)) {
				throw new RtspProtoInvalidResponseException("Missing Content-Length header" + errMsgSuffix);
			}
			long contentLengthLong = output.headers.get(RtspHeaderKey.CONTENT_LEN).hdValContLen.contentLen.getLen32bit().orElseThrow();
			if (contentLengthLong == 0L) {
				throw new RtspProtoInvalidResponseException("Content-Length header value is zero" + errMsgSuffix);
			}
		} else if (output.messageType == RtspProtoMessageType.GET_PARAMETER || output.messageType == RtspProtoMessageType.SET_PARAMETER) {
			if (output.statusCode != RtspProtoStatusCode.OK && output.bodyGetSetInvalidParams.isParamNamesEmpty()) {
				return;
			}
			if (output.messageType != RtspProtoMessageType.GET_PARAMETER || output.bodyGetParamKv.isParamKvsEmpty()) {
				return;
			}
			final String errMsgSuffix = " for " + output.messageType + " message";
			if (output.headers.containsKey(RtspHeaderKey.CONTENT_TYPE) &&
					! output.headers.containsKey(RtspHeaderKey.CONTENT_LEN)) {
				throw new RtspProtoInvalidResponseException("Missing Content-Length header when Content-Type is present" + errMsgSuffix);
			}
			if (! output.headers.containsKey(RtspHeaderKey.CONTENT_TYPE) &&
					output.headers.containsKey(RtspHeaderKey.CONTENT_LEN)) {
				throw new RtspProtoInvalidResponseException("Missing Content-Type header when Content-Length is present" + errMsgSuffix);
			}
		}
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

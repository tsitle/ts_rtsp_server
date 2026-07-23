package org.tsitle.lib_xrtxp.rtsp.lowlevel.helper;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.HostnameHelperInvalidUriException;
import org.tsitle.lib_xrtxp.common.helpers.HostnameHelper;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.data_rr.RtspProtoDataCntGetSetParamKvs;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidPbRangeException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.*;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspConnectionPolicy;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspContentEncoding;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspMimeType;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.RtspTransportMode;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoPlaybackRange;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public final class RtspLowParserHelper {

	private RtspLowParserHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static void helperParseHeaderValue_connection(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeConnection outputHd
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Connection: close" (close | keep-alive)
		 * Controls whether the client and server should close the connection after the current request/response
		 */
		outputHd.connectionPol = RtspConnectionPolicy.of(hdValue);
		if (outputHd.connectionPol == RtspConnectionPolicy.NONE) {
			throw new RtspLowInvalidRrException("Invalid Connection value: '" + hdValue + "'");
		}
	}

	public static void helperParseHeaderValue_contbase(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeContBase outputHd
			) {
		/*
		 * Example:
		 *   "Content-Base: rtsp://example.com/path/to/resource/"
		 * Contains an absolute URI as base for resolving relative URLs within the entity
		 */
		try {
			URI tmpUri = HostnameHelper.convertRtspUrlIntoURI(hdValue);

			// we strip away any user credentials and fragment
			outputHd.contentBaseStr = (tmpUri.getScheme().equals("https") ?
					RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL : RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL);
			outputHd.contentBaseStr += "://" + tmpUri.getHost();
			if (tmpUri.getPort() > 0) {
				outputHd.contentBaseStr += ":" + tmpUri.getPort();
			}
			outputHd.contentBaseStr += (tmpUri.getPath() == null ? "" : tmpUri.getPath());
			// add a slash to the end
			if (! outputHd.contentBaseStr.endsWith("/")) {
				outputHd.contentBaseStr += "/";
			}
			// add query parameters
			if (tmpUri.getQuery() != null) {
				outputHd.contentBaseStr += "?" + tmpUri.getQuery();
			}
		} catch (HostnameHelperInvalidUriException e) {
			// we ignore this error and use the value as-is
			outputHd.contentBaseStr = hdValue;
		}
	}

	public static void helperParseHeaderValue_contenc(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeContEnc outputHd
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Content-Encoding: gzip" (gzip | compress | deflate)
		 * Specifies the encoding method used for the entity body
		 */
		outputHd.contentEnc = RtspContentEncoding.of(hdValue);
		if (outputHd.contentEnc == RtspContentEncoding.NONE) {
			throw new RtspLowInvalidRrException("Invalid Content-Encoding value: '" + hdValue + "'");
		}
	}

	public static void helperParseHeaderValue_contlang(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeContLang outputHd
			) {
		/*
		 * Example:
		 *   "Content-Language: en" (en | fr | de | ...)
		 *  or
		 *   "Content-Language: en, de"
		 * Specifies the language(s) of the entity body
		 */
		outputHd.contentLangStr = hdValue;
	}

	public static void helperParseHeaderValue_contlen(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeContLen outputHd
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Content-Length: 1234"
		 * Specifies the length of the entity body
		 */
		try {
			outputHd.contentLen.setLen32bit(Long.parseLong(hdValue));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid Content-Length format: '" + hdValue + "'");
		} catch (RtspProtoNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid Content-Length value: " + e.getMessage());
		}
	}

	public static void helperParseHeaderValue_conttype(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeContType outputHd
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Content-Type: text/parameters"
		 */
		outputHd.contentType = RtspMimeType.of(hdValue);
		if (outputHd.contentType == RtspMimeType.NONE) {
			throw new RtspLowInvalidRrException("Invalid Content-Type value: '" + hdValue + "'");
		}
		if (outputHd.contentType != RtspMimeType.PARAMETERS && outputHd.contentType != RtspMimeType.SDP) {
			throw new RtspLowInvalidRrException("Unsupported Content-Type value: " + outputHd.contentType);
		}
	}

	public static void helperParseHeaderValue_cseq(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeCseq outputHd
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "CSeq: 1234"
		 * Specifies the sequence number of the request or response
		 */
		try {
			outputHd.cseqNr.setCseq32bit(Long.parseLong(hdValue));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid CSeq format: '" + hdValue + "'");
		} catch (RtspProtoNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid CSeq value: " + e.getMessage());
		}
	}

	public static void helperParseHeaderValue_date(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeDate outputHd
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Date: Fri, 03 Apr 2026 10:54:06 GMT"
		 */
		try {
			outputHd.dateObj = ZonedDateTime.parse(
					hdValue,
					DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)
				).toInstant();
		} catch (DateTimeParseException e) {
			throw new RtspLowInvalidRrException("Invalid Date format: '" + hdValue + "'");
		}
	}

	public static void helperParseHeaderValue_range(
				@NonNull String hdValue,
				@NonNull RtspProtoHeaderTypeRange outputHd
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Range: clock=19960213T143205Z-;time=19970123T143720Z"
		 * SMPTE Relative Timestamps:
		 *    "Range: smpte=10:12:33:20-"
		 *    "Range: smpte=10:07:33-"
		 *    "Range: smpte=10:07:00-10:07:33:05.01"
		 *    "Range: smpte-25=10:07:00-10:07:33:05.01"
		 * Normal Play Time:
		 *    "Range: npt=123.45-125"
		 *    "Range: npt=12:05:35.3-"
		 *    "Range: npt=now-"
		 * Absolute Time:
		 *    "Range: clock=19961108T143720.25Z-"  (November 8, 1996 at 14h37 and 20 and a quarter seconds UTC)
		 */
		try {
			outputHd.range.copyFrom(RtspProtoPlaybackRange.parseString(hdValue));
		} catch (RtspProtoInvalidPbRangeException e) {
			throw new RtspLowInvalidRrException("Invalid Range format: '" + hdValue + "'");
		}
	}

	public static void helperParseHeaderValue_session(
				@NonNull String hdValue,
				boolean isForRequest,
				@NonNull RtspProtoHeaderTypeSession outputHd,
				@NonNull List<@NonNull String> outputWarnings
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Session: 1234567890"
		 *   "Session: 1234567890;timeout=60"
		 */
		String rawSid;
		String rawTimeout = "";
		if (hdValue.contains(";")) {
			String[] parts = hdValue.split(";");
			if ((isForRequest && parts.length > 1) || (! isForRequest && parts.length > 2)) {
				outputWarnings.add("Ignoring additional Session parameters in '" + hdValue + "'");
			} else if (! isForRequest && parts.length == 2) {
				String partLc = parts[1].toLowerCase();
				if (partLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TIMEOUT.toLowerCase())) {
					rawTimeout = parts[1].substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TIMEOUT.length());
					rawTimeout = rawTimeout.strip();
				} else {
					outputWarnings.add("Ignoring unknown Session parameter '" + parts[1] + "' in '" + hdValue + "'");
				}
			}
			rawSid = parts[0];
		} else {
			rawSid = hdValue;
		}

		// parse timeout
		if (! rawTimeout.isBlank() && ! rawTimeout.equals("-1")) {
			try {
				outputHd.setTimeout32bit(Long.parseLong(rawTimeout));
			} catch (NumberFormatException e) {
				throw new RtspLowInvalidRrException("Invalid Session timeout parameter: '" + rawTimeout + "' - " +
						"invalid format");
			} catch (RtspProtoNumberRangeException e) {
				throw new RtspLowInvalidRrException("Invalid Session parameter: '" + rawTimeout + "' - " +
						e.getMessage());
			}
		}
		//
		outputHd.idSession.setIdStr(rawSid.strip());
	}

	public static void helperParseHeaderValue_transport(
				@NonNull String hdValue,
				boolean isForRequest,
				@NonNull RtspProtoHeaderTypeTransport outputHd,
				@NonNull List<@NonNull String> outputWarnings
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   Request:
		 *     single transport option acceptable to the client:
		 *       "RTP/AVP;unicast;client_port=1050-1051"
		 *     multiple transport options that are acceptable to the client:
		 *       "RTP/AVP;multicast;ttl=127;mode=\"PLAY\",RTP/AVP;unicast;client_port=3456-3457;mode=\"PLAY\""
		 *   Response:
		 *       "RTP/AVP;unicast;destination=10.55.0.5;source=192.168.5.20;client_port=1050-1051;server_port=6970-6971;ssrc=DEADBEEF"
		 *     or
		 *       "RTP/AVP/TCP;interleaved=0-1"
		 * See https://datatracker.ietf.org/doc/html/rfc2326#section-12.39
		 *
		 * Currently, there is no support for RTSP v2.0 style Transport parameters
		 *   See https://datatracker.ietf.org/doc/html/rfc7826#section-13.3
		 */

		for (String tmpPart : hdValue.split(",")) {
			RtspProtoHeaderTypeTransport.@NonNull TpOption tmpOutputTpOpt = new RtspProtoHeaderTypeTransport.TpOption();
			parseOneTransportVariant(tmpPart.strip(), isForRequest, tmpOutputTpOpt, outputWarnings);
			outputHd.tpOptions.add(tmpOutputTpOpt);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public static long helperParseHexStringIntoLong(@NonNull String fieldDesc, @NonNull String hexStr)
			throws RtspLowInvalidRrException {
		try {
			return Long.parseLong(hexStr.strip(), 16);
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Cannot parse " + fieldDesc + ": '" + hexStr + "'");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull String helperCleanUpBodyLine(@NonNull String bodyLine) {
		return bodyLine
				.replace("'", "").replace("\"", "")
				.replace("\t", "")
				.replace("\r", "").replace("\n", "")
				.strip();
	}

	public static void helperParseBodyLine_keyValue(
				@NonNull RtspProtoMessageType messageType,
				@NonNull String bodyLine,
				@NonNull RtspProtoDataCntGetSetParamKvs outputMap
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "barparam: barstuff"
		 */
		if (helperCleanUpBodyLine(bodyLine).isBlank()) {
			return;
		}
		String[] tmpSplit = bodyLine.split(":");
		if (tmpSplit.length < 2) {
			throw new RtspLowInvalidRrException(messageType + ": '" + bodyLine + "'");
		}
		String tmpParKey = helperCleanUpBodyLine(tmpSplit[0]);
		if (tmpParKey.isBlank()) {
			throw new RtspLowInvalidRrException(messageType + ": '" + bodyLine + "'");
		}
		String tmpParVal = helperCleanUpBodyLine(bodyLine.substring(tmpSplit[0].length() + 1));
		if (tmpParVal.isBlank()) {
			throw new RtspLowInvalidRrException(messageType + ": '" + bodyLine + "'");
		}
		outputMap.putParamKvsEntry(tmpParKey, tmpParVal);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private enum InternalTpProfLtp {
		NONE,
		RTP_AVP_UDP,
		RTP_SAVP_UDP,
		RTP_AVP_TCP,
		RTP_SAVP_TCP
	}

	private static void parseOneTransportVariant(
				@NonNull String hdPartValue,
				boolean isForRequest,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt,
				@NonNull List<@NonNull String> outputWarnings
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   Request:
		 *     "RTP/AVP;unicast;client_port=1050-1051"
		 *     or
		 *     "RTP/AVP/TCP;interleaved=0-1"
		 *     or
		 *     "RTP/AVP;multicast;ttl=127;mode=\"PLAY\""
		 *     or
		 *     "RTP/AVP;unicast;client_port=3456-3457;mode=\"PLAY\""
		 *   Response:
		 *     "RTP/AVP;unicast;destination=10.55.0.5;source=192.168.5.20;client_port=1050-1051;server_port=6970-6971;ssrc=DEADBEEF"
		 *     or
		 *     "RTP/AVP/TCP;interleaved=0-1"
		 * See https://datatracker.ietf.org/doc/html/rfc2326#section-12.39
		 *
		 * Currently, there is no support for RTSP v2.0 style Transport parameters
		 *   See https://datatracker.ietf.org/doc/html/rfc7826#section-13.3
		 */

		InternalTpProfLtp rawTransportProfileLowerTp = InternalTpProfLtp.NONE;
		String rawClientPorts = "";
		String rawClientChanns = "";
		String rawServerPorts = "";
		String rawSourceIp = "";
		String rawDestIp = "";
		String rawSsrc = "";
		String rawMode = "";

		StringTokenizer tokens = new StringTokenizer(hdPartValue, ";");
		while (tokens.hasMoreTokens()) {
			String curTokenAsIs = tokens.nextToken().strip();
			String curTokenLc = curTokenAsIs.toLowerCase();
			if (RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP1.equalsIgnoreCase(curTokenAsIs) ||
					RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP2.equalsIgnoreCase(curTokenAsIs)) {
				rawTransportProfileLowerTp = InternalTpProfLtp.RTP_AVP_UDP;
			} else if (RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP1.equalsIgnoreCase(curTokenAsIs) ||
					RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP2.equalsIgnoreCase(curTokenAsIs)) {
				rawTransportProfileLowerTp = InternalTpProfLtp.RTP_SAVP_UDP;
			} else if (RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP.equalsIgnoreCase(curTokenAsIs)) {
				rawTransportProfileLowerTp = InternalTpProfLtp.RTP_AVP_TCP;
			} else if (RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPTCP.equalsIgnoreCase(curTokenAsIs)) {
				rawTransportProfileLowerTp = InternalTpProfLtp.RTP_SAVP_TCP;
			} else if (RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_UNICAST.equalsIgnoreCase(curTokenAsIs)) {
				outputTpOpt.tpSubStream.setIsUnicast(true);
			} else if (RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_MULTICAST.equalsIgnoreCase(curTokenAsIs)) {
				outputTpOpt.tpSubStream.setIsUnicast(false);
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT.toLowerCase())) {
				rawClientPorts = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT.length());
				rawClientPorts = rawClientPorts.strip();
				outputTpOpt.tpSubStream.setIsInterleaved(false);
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED.toLowerCase())) {
				rawClientChanns = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED.length());
				rawClientChanns = rawClientChanns.strip();
				outputTpOpt.tpSubStream.setIsInterleaved(true);
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SERVERPORT.toLowerCase())) {
				rawServerPorts = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SERVERPORT.length());
				rawServerPorts = rawServerPorts.strip();
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SOURCEIP.toLowerCase())) {
				rawSourceIp = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SOURCEIP.length());
				rawSourceIp = rawSourceIp.strip();
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_DESTIP.toLowerCase())) {
				rawDestIp = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_DESTIP.length());
				rawDestIp = rawDestIp.strip();
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SSRC.toLowerCase())) {
				rawSsrc = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SSRC.length());
				rawSsrc = rawSsrc.strip();
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_MODE.toLowerCase())) {
				rawMode = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_MODE.length());
				rawMode = rawMode.strip();
			} else {
				outputWarnings.add("Unknown Transport parameter: '" + curTokenAsIs + "'");
			}
		}

		// parse Transport/Profile/Lower-Transport
		parseTransportParam_tpProfLtp(rawTransportProfileLowerTp, outputTpOpt);
		// check Unicast/Multicast
		checkTransportParam_delivery(rawClientPorts, rawClientChanns, rawServerPorts, outputTpOpt);
		// check Interleaved
		checkTransportParam_interleaved(outputTpOpt);
		// parse client UDP ports
		if (outputTpOpt.tpSubStream.getIsUdp() && outputTpOpt.tpSubStream.getIsUnicast()) {
			parseTransportParam_clientUdpPorts(rawClientPorts, outputTpOpt);
		}
		// parse client TCP channels
		if (! outputTpOpt.tpSubStream.getIsUdp() && outputTpOpt.tpSubStream.getIsUnicast()) {
			parseTransportParam_clientTcpChanns(rawClientChanns, outputTpOpt);
		}
		// parse server UDP ports
		if (outputTpOpt.tpSubStream.getIsUdp() && outputTpOpt.tpSubStream.getIsUnicast()) {
			parseTransportParam_serverUdpPorts(rawServerPorts, isForRequest, outputTpOpt);
		}
		// parse Source and Destination IPs
		parseTransportParam_sourceDestIps(rawSourceIp, rawDestIp, isForRequest, outputTpOpt);
		// parse SSRC ID
		parseTransportParam_ssrc(rawSsrc, isForRequest, outputTpOpt);
		// parse Mode
		parseTransportParam_mode(rawMode, outputTpOpt);
	}

	private static void parseTransportParam_tpProfLtp(
				@NonNull InternalTpProfLtp rawTransportProfileLowerTp,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) throws RtspLowInvalidRrException {
		switch (rawTransportProfileLowerTp) {
			case InternalTpProfLtp.RTP_AVP_UDP -> {
				outputTpOpt.tpSubStream.setIsUdp(true);
				outputTpOpt.tpSubStream.setIsEncr(false);
			}
			case InternalTpProfLtp.RTP_SAVP_UDP -> {
				outputTpOpt.tpSubStream.setIsUdp(true);
				outputTpOpt.tpSubStream.setIsEncr(true);
			}
			case InternalTpProfLtp.RTP_AVP_TCP -> {
				outputTpOpt.tpSubStream.setIsUdp(false);
				outputTpOpt.tpSubStream.setIsEncr(false);
			}
			case InternalTpProfLtp.RTP_SAVP_TCP -> {
				outputTpOpt.tpSubStream.setIsUdp(false);
				outputTpOpt.tpSubStream.setIsEncr(true);
			}
			default -> throw new RtspLowInvalidRrException("Missing Transport parameter Tp/Prof/LowerTp");
		}
	}

	private static void checkTransportParam_delivery(
				@NonNull String rawClientPorts,
				@NonNull String rawClientChanns,
				@NonNull String rawServerPorts,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) throws RtspLowInvalidRrException {
		if (! (outputTpOpt.tpSubStream.getIsUdp() || outputTpOpt.tpSubStream.getIsUnicast())) {
			throw new RtspLowInvalidRrException("Cannot combine TCP with Multicast");
		}
		if (! outputTpOpt.tpSubStream.getIsUnicast() &&
				(! (rawClientPorts.isBlank() && rawClientChanns.isBlank() && rawServerPorts.isBlank()))) {
			throw new RtspLowInvalidRrException("Cannot combine Multicast with individual ports/channels");
		}
	}

	private static void checkTransportParam_interleaved(
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) throws RtspLowInvalidRrException {
		if (outputTpOpt.tpSubStream.getIsUdp() && outputTpOpt.tpSubStream.getIsInterleaved()) {
			throw new RtspLowInvalidRrException("Cannot combine UDP with Interleaved");
		}
		if (! (outputTpOpt.tpSubStream.getIsUdp() || outputTpOpt.tpSubStream.getIsInterleaved())) {
			throw new RtspLowInvalidRrException("TCP must use Interleaved");
		}
	}

	private static void parseTransportParam_clientUdpPorts(
				@NonNull String rawClientPorts,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) throws RtspLowInvalidRrException {
		final String fieldDesc = "Transport parameter Client UDP Ports";
		if (rawClientPorts.isBlank()) {
			throw new RtspLowInvalidRrException("Missing " + fieldDesc);
		}
		String[] tmpPorts = rawClientPorts.split("-");
		if (tmpPorts.length != 2) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientPorts + "' - " +
					"expected two ports");
		}
		try {
			outputTpOpt.tpSubStream.getClientUdpPortRtpPtr().setPort16bit(Integer.parseInt(tmpPorts[0]));
			outputTpOpt.tpSubStream.getClientUdpPortRtcpPtr().setPort16bit(Integer.parseInt(tmpPorts[1]));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientPorts + "' - " +
					"cannot parse ports, invalid format");
		} catch (RtspProtoNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientPorts + "' - " +
					e.getMessage());
		}
	}

	private static void parseTransportParam_clientTcpChanns(
				@NonNull String rawClientChanns,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) throws RtspLowInvalidRrException {
		final String fieldDesc = "Transport parameter Client TCP Channels";
		if (rawClientChanns.isBlank()) {
			throw new RtspLowInvalidRrException("Missing " + fieldDesc);
		}
		String[] tmpPorts = rawClientChanns.split("-");
		if (tmpPorts.length != 2) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientChanns + "' - " +
					"expected two channels");
		}
		try {
			outputTpOpt.tpSubStream.getClientTcpChannRtpPtr().setChannel8bit(Integer.parseInt(tmpPorts[0]));
			outputTpOpt.tpSubStream.getClientTcpChannRtcpPtr().setChannel8bit(Integer.parseInt(tmpPorts[1]));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientChanns + "' - " +
					"cannot parse channels, invalid format");
		} catch (RtspProtoNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientChanns + "' - " +
					e.getMessage());
		}
	}

	private static void parseTransportParam_serverUdpPorts(
				@NonNull String rawServerPorts,
				boolean isForRequest,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) throws RtspLowInvalidRrException {
		final String fieldDesc = "Transport parameter Server UDP Ports";
		if (isForRequest) {
			if (! rawServerPorts.isBlank()) {
				throw new RtspLowInvalidRrException(fieldDesc + " in request");
			}
			return;
		}
		if (rawServerPorts.isBlank()) {
			throw new RtspLowInvalidRrException("Missing " + fieldDesc);
		}
		String[] tmpPorts = rawServerPorts.split("-");
		if (tmpPorts.length != 2) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawServerPorts + "' - " +
					"expected two ports");
		}
		try {
			outputTpOpt.tpSubStream.getServerUdpPortRtpPtr().setPort16bit(Integer.parseInt(tmpPorts[0]));
			outputTpOpt.tpSubStream.getServerUdpPortRtcpPtr().setPort16bit(Integer.parseInt(tmpPorts[1]));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawServerPorts + "' - " +
					"cannot parse ports, invalid format");
		} catch (RtspProtoNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawServerPorts + "' - " +
					e.getMessage());
		}
	}

	private static void parseTransportParam_sourceDestIps(
				@NonNull String rawSourceIp,
				@NonNull String rawDestIp,
				boolean isForRequest,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) throws RtspLowInvalidRrException {
		if (isForRequest) {
			if (! (rawSourceIp.isBlank() && rawDestIp.isBlank())) {
				final String fieldDesc = "Transport parameter Source-/Dest-IP";
				throw new RtspLowInvalidRrException(fieldDesc + " in request");
			}
			return;
		}
		outputTpOpt.tpSourceIpOrHost = rawSourceIp;  // can be empty
		outputTpOpt.tpDestIpOrHost = rawDestIp;  // can be empty
	}

	private static void parseTransportParam_ssrc(
				@NonNull String rawSsrc,
				boolean isForRequest,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) throws RtspLowInvalidRrException {
		final String fieldDesc = "Transport parameter SSRC";
		if (isForRequest) {
			if (! rawSsrc.isBlank()) {
				throw new RtspLowInvalidRrException(fieldDesc + " in request");
			}
			return;
		}
		if (rawSsrc.isBlank()) {
			outputTpOpt.tpSsrcId.clear();  // can be empty
			return;
		}
		if (! outputTpOpt.tpSubStream.getIsUnicast()) {
			throw new RtspLowInvalidRrException(fieldDesc + " is only valid for Unicast");
		}
		try {
			outputTpOpt.tpSsrcId.setId32bit(
					helperParseHexStringIntoLong(fieldDesc, rawSsrc)
				);
		} catch (RtspProtoNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawSsrc + "' - " +
					e.getMessage());
		}
	}

	private static void parseTransportParam_mode(
				@NonNull String rawMode,
				RtspProtoHeaderTypeTransport.@NonNull TpOption outputTpOpt
			) {
		if (rawMode.isBlank()) {
			outputTpOpt.tpMode = RtspTransportMode.NONE;
			return;
		}
		outputTpOpt.tpMode = RtspTransportMode.of(rawMode);
	}

}

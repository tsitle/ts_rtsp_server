package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
		 *   "Content-Base: rtsp://example.com/path/to/resource"
		 * Contains an absolute URI as base for resolving relative URLs within the entity
		 */
		outputHd.contentBaseStr = hdValue;
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
			outputHd.setContentLen32bit((int)Long.parseLong(hdValue));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid Content-Length format: '" + hdValue + "'");
		} catch (RtspNumberRangeException e) {
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
			outputHd.setCseqNr32bit(Long.parseLong(hdValue));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid CSeq format: '" + hdValue + "'");
		} catch (RtspNumberRangeException e) {
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
			) {
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
		outputHd.rangeStr = hdValue;
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
			} catch (RtspNumberRangeException e) {
				throw new RtspLowInvalidRrException("Invalid Session parameter: '" + rawTimeout + "' - " +
						e.getMessage());
			}
		}
		//
		outputHd.sessionIdStr = rawSid.strip();
	}

	public static void helperParseHeaderValue_transport(
				@NonNull String hdValue,
				boolean isForRequest,
				@NonNull RtspProtoHeaderTypeTransport outputHd,
				@NonNull List<@NonNull String> outputWarnings
			) throws RtspLowInvalidRrException {
		/*
		 * @TODO remove example once support for multiple transports has been implemented
		 * Example:
		 *   Request:
		 *     "RTP/AVP;unicast;client_port=1050-1051"
		 *     or
		 *     "RTP/AVP/TCP;interleaved=0-1"
		 *     or
		 *     "RTP/AVP;multicast;ttl=127;mode=\"PLAY\",RTP/AVP;unicast;client_port=3456-3457;mode=\"PLAY\""
		 *   Response:
		 *     "RTP/AVP;unicast;destination=10.55.0.5;source=192.168.5.20;client_port=1050-1051;server_port=6970-6971;ssrc=DEADBEEF"
		 *     or
		 *     "RTP/AVP/TCP;interleaved=0-1"
		 * See https://datatracker.ietf.org/doc/html/rfc2326#section-12.39
		 *
		 * Currently, there is no support for RTSP v2.0 style Transport parameters
		 *   See https://datatracker.ietf.org/doc/html/rfc7826#section-13.3
		 */

		for (String tmpPart : hdValue.split(",")) {
			parseOneTransportVariant(tmpPart.strip(), isForRequest, outputHd, outputWarnings);
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
				@NonNull RtspMessageType messageType,
				@NonNull String bodyLine,
				@NonNull Map<@NonNull String, @NonNull String> outputMap
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
		outputMap.put(tmpParKey, tmpParVal);
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
				@NonNull RtspProtoHeaderTypeTransport outputHd,
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
				outputHd.tpIsUnicast = true;
			} else if (RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_MULTICAST.equalsIgnoreCase(curTokenAsIs)) {
				outputHd.tpIsUnicast = false;
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT.toLowerCase())) {
				rawClientPorts = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT.length());
				rawClientPorts = rawClientPorts.strip();
				outputHd.tpIsInterleaved = false;
			} else if (curTokenLc.startsWith(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED.toLowerCase())) {
				rawClientChanns = curTokenAsIs.substring(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED.length());
				rawClientChanns = rawClientChanns.strip();
				outputHd.tpIsInterleaved = true;
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
		parseTransportParam_tpProfLtp(rawTransportProfileLowerTp, outputHd);
		// check Unicast/Multicast
		checkTransportParam_delivery(rawClientPorts, rawClientChanns, rawServerPorts, outputHd);
		// check Interleaved
		checkTransportParam_interleaved(outputHd);
		// parse client UDP ports
		if (outputHd.tpIsUdp && outputHd.tpIsUnicast) {
			parseTransportParam_clientUdpPorts(rawClientPorts, outputHd);
		}
		// parse client TCP channels
		if (! outputHd.tpIsUdp && outputHd.tpIsUnicast) {
			parseTransportParam_clientTcpChanns(rawClientChanns, outputHd);
		}
		// parse server UDP ports
		if (outputHd.tpIsUdp && outputHd.tpIsUnicast) {
			parseTransportParam_serverUdpPorts(rawServerPorts, isForRequest, outputHd);
		}
		// parse Source and Destination IPs
		parseTransportParam_sourceDestIps(rawSourceIp, rawDestIp, isForRequest, outputHd);
		// parse SSRC ID
		parseTransportParam_ssrc(rawSsrc, isForRequest, outputHd);
		// parse Mode
		parseTransportParam_mode(rawMode, outputHd);
	}

	private static void parseTransportParam_tpProfLtp(
				@NonNull InternalTpProfLtp rawTransportProfileLowerTp,
				@NonNull RtspProtoHeaderTypeTransport outputHd
			) throws RtspLowInvalidRrException {
		switch (rawTransportProfileLowerTp) {
			case InternalTpProfLtp.RTP_AVP_UDP -> {
				outputHd.tpIsUdp = true;
				outputHd.tpIsEncr = false;
			}
			case InternalTpProfLtp.RTP_SAVP_UDP -> {
				outputHd.tpIsUdp = true;
				outputHd.tpIsEncr = true;
			}
			case InternalTpProfLtp.RTP_AVP_TCP -> {
				outputHd.tpIsUdp = false;
				outputHd.tpIsEncr = false;
			}
			case InternalTpProfLtp.RTP_SAVP_TCP -> {
				outputHd.tpIsUdp = false;
				outputHd.tpIsEncr = true;
			}
			default -> throw new RtspLowInvalidRrException("Missing Transport parameter Tp/Prof/LowerTp");
		}
	}

	private static void checkTransportParam_delivery(
				@NonNull String rawClientPorts,
				@NonNull String rawClientChanns,
				@NonNull String rawServerPorts,
				@NonNull RtspProtoHeaderTypeTransport outputHd
			) throws RtspLowInvalidRrException {
		if (! (outputHd.tpIsUdp || outputHd.tpIsUnicast)) {
			throw new RtspLowInvalidRrException("Cannot combine TCP with Multicast");
		}
		if (! outputHd.tpIsUnicast &&
				(! (rawClientPorts.isBlank() && rawClientChanns.isBlank() && rawServerPorts.isBlank()))) {
			throw new RtspLowInvalidRrException("Cannot combine Multicast with individual ports/channels");
		}
	}

	private static void checkTransportParam_interleaved(
				@NonNull RtspProtoHeaderTypeTransport outputHd
			) throws RtspLowInvalidRrException {
		if (outputHd.tpIsUdp && outputHd.tpIsInterleaved) {
			throw new RtspLowInvalidRrException("Cannot combine UDP with Interleaved");
		}
		if (! (outputHd.tpIsUdp || outputHd.tpIsInterleaved)) {
			throw new RtspLowInvalidRrException("TCP must use Interleaved");
		}
	}

	private static void parseTransportParam_clientUdpPorts(
				@NonNull String rawClientPorts,
				@NonNull RtspProtoHeaderTypeTransport outputHd
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
			outputHd.setClientUdpPortRtp16bit(Integer.parseInt(tmpPorts[0]));
			outputHd.setClientUdpPortRtcp16bit(Integer.parseInt(tmpPorts[1]));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientPorts + "' - " +
					"cannot parse ports, invalid format");
		} catch (RtspNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientPorts + "' - " +
					e.getMessage());
		}
	}

	private static void parseTransportParam_clientTcpChanns(
				@NonNull String rawClientChanns,
				@NonNull RtspProtoHeaderTypeTransport outputHd
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
			outputHd.setClientTcpChannRtp16bit(Integer.parseInt(tmpPorts[0]));
			outputHd.setClientTcpChannRtcp16bit(Integer.parseInt(tmpPorts[1]));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientChanns + "' - " +
					"cannot parse channels, invalid format");
		} catch (RtspNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawClientChanns + "' - " +
					e.getMessage());
		}
	}

	private static void parseTransportParam_serverUdpPorts(
				@NonNull String rawServerPorts,
				boolean isForRequest,
				@NonNull RtspProtoHeaderTypeTransport outputHd
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
			outputHd.setServerUdpPortRtp16bit(Integer.parseInt(tmpPorts[0]));
			outputHd.setServerUdpPortRtcp16bit(Integer.parseInt(tmpPorts[1]));
		} catch (NumberFormatException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawServerPorts + "' - " +
					"cannot parse ports, invalid format");
		} catch (RtspNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawServerPorts + "' - " +
					e.getMessage());
		}
	}

	private static void parseTransportParam_sourceDestIps(
				@NonNull String rawSourceIp,
				@NonNull String rawDestIp,
				boolean isForRequest,
				@NonNull RtspProtoHeaderTypeTransport outputHd
			) throws RtspLowInvalidRrException {
		if (isForRequest) {
			if (! (rawSourceIp.isBlank() && rawDestIp.isBlank())) {
				final String fieldDesc = "Transport parameter Source-/Dest-IP";
				throw new RtspLowInvalidRrException(fieldDesc + " in request");
			}
			return;
		}
		outputHd.tpSourceIpOrHost = rawSourceIp;  // can be empty
		outputHd.tpDestIpOrHost = rawDestIp;  // can be empty
	}

	private static void parseTransportParam_ssrc(
				@NonNull String rawSsrc,
				boolean isForRequest,
				@NonNull RtspProtoHeaderTypeTransport outputHd
			) throws RtspLowInvalidRrException {
		final String fieldDesc = "Transport parameter SSRC";
		if (isForRequest) {
			if (! rawSsrc.isBlank()) {
				throw new RtspLowInvalidRrException(fieldDesc + " in request");
			}
			return;
		}
		if (rawSsrc.isBlank()) {
			outputHd.clearSsrcId();  // can be empty
			return;
		}
		if (! outputHd.tpIsUnicast) {
			throw new RtspLowInvalidRrException(fieldDesc + " is only valid for Unicast");
		}
		try {
			outputHd.setSsrcId32bit(
					helperParseHexStringIntoLong(fieldDesc, rawSsrc)
				);
		} catch (RtspNumberRangeException e) {
			throw new RtspLowInvalidRrException("Invalid " + fieldDesc + ": '" + rawSsrc + "' - " +
					e.getMessage());
		}
	}

	private static void parseTransportParam_mode(
				@NonNull String rawMode,
				@NonNull RtspProtoHeaderTypeTransport outputHd
			) {
		if (rawMode.isBlank()) {
			outputHd.tpMode = RtspTransportMode.NONE;
			return;
		}
		outputHd.tpMode = RtspTransportMode.of(rawMode);
	}

}

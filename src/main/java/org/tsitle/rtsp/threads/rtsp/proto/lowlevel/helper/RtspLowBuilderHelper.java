package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspConnectionPolicy;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspContentEncoding;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspMimeType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspTransportMode;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class RtspLowBuilderHelper {

	private RtspLowBuilderHelper() { }

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull String helperBuildHeaderValue_connection(@NonNull RtspProtoHeaderTypeConnection hdValue)
			throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Connection: close" (close | keep-alive)
		 * Controls whether the client and server should close the connection after the current request/response
		 */
		if (hdValue.connectionPol == RtspConnectionPolicy.NONE) {
			throw new RtspLowInvalidRrException("connectionPol must be set");
		}
		return hdValue.connectionPol.getStrValue();
	}

	public static @NonNull String helperBuildHeaderValue_contbase(@NonNull RtspProtoHeaderTypeContBase hdValue)
			throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Content-Base: rtsp://example.com/path/to/resource"
		 * Contains an absolute URI as base for resolving relative URLs within the entity
		 */
		if (hdValue.contentBaseStr.isBlank()) {
			throw new RtspLowInvalidRrException("contentBaseStr cannot be blank");
		}
		return hdValue.contentBaseStr;
	}

	public static @NonNull String helperBuildHeaderValue_contenc(@NonNull RtspProtoHeaderTypeContEnc hdValue)
			throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Content-Encoding: gzip" (gzip | compress | deflate)
		 * Specifies the encoding method used for the entity body
		 */
		if (hdValue.contentEnc == RtspContentEncoding.NONE) {
			throw new RtspLowInvalidRrException("contentEnc must be set");
		}
		return hdValue.contentEnc.getStrValue();
	}

	public static @NonNull String helperBuildHeaderValue_contlang(@NonNull RtspProtoHeaderTypeContLang hdValue)
			throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Content-Language: en" (en | fr | de | ...)
		 *  or
		 *   "Content-Language: en, de"
		 * Specifies the language(s) of the entity body
		 */
		if (hdValue.contentLangStr.isBlank()) {
			throw new RtspLowInvalidRrException("contentLangStr cannot be blank");
		}
		return hdValue.contentLangStr;
	}

	public static @NonNull String helperBuildHeaderValue_contlen(@NonNull RtspProtoHeaderTypeContLen hdValue)
			throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Content-Length: 1234"
		 * Specifies the length of the entity body
		 */
		if (hdValue.getContentLen32bit().isEmpty()) {
			throw new RtspLowInvalidRrException("Content-Length must be set");
		}
		return Integer.toUnsignedString(hdValue.getContentLen32bit().get());
	}

	public static @NonNull String helperBuildHeaderValue_conttype(@NonNull RtspProtoHeaderTypeContType hdValue)
			throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Content-Type: text/parameters"
		 */
		if (hdValue.contentType == RtspMimeType.NONE) {
			throw new RtspLowInvalidRrException("Content-Type must be set");
		}
		return hdValue.contentType.getStrValue();
	}

	public static @NonNull String helperBuildHeaderValue_cseq(@NonNull RtspProtoHeaderTypeCseq hdValue)
			throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "CSeq: 1234"
		 * Specifies the sequence number of the request or response
		 */
		if (hdValue.cseqNr.isEmpty()) {
			throw new RtspLowInvalidRrException("CSeq must be set");
		}
		return Long.toUnsignedString(hdValue.cseqNr.getCseq32bit().orElseThrow());
	}

	public static @NonNull String helperBuildHeaderValue_date(@NonNull RtspProtoHeaderTypeDate hdValue) {
		/*
		 * Example:
		 *   "Date: Fri, 03 Apr 2026 10:54:06 GMT"
		 */
		return DateTimeFormatter.RFC_1123_DATE_TIME
				.withLocale(Locale.ENGLISH)
				.format(hdValue.dateObj.atZone(ZoneOffset.UTC));
	}

	public static @NonNull String helperBuildHeaderValue_range(@NonNull RtspProtoHeaderTypeRange hdValue)
			throws RtspLowInvalidRrException {
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
		if (hdValue.rangeStr.isBlank()) {
			throw new RtspLowInvalidRrException("rangeStr cannot be blank");
		}
		return hdValue.rangeStr;
	}

	public static @NonNull String helperBuildHeaderValue_session(
				@NonNull RtspProtoHeaderTypeSession hdValue,
				boolean isForRequest
			) throws RtspLowInvalidRrException {
		/*
		 * Example:
		 *   "Session: 1234567890"
		 *   "Session: 1234567890;timeout=60"
		 */
		if (hdValue.idSession.isEmpty()) {
			throw new RtspLowInvalidRrException("sessionIdStr cannot be blank");
		}
		return hdValue.idSession.getIdStr() +
				(! isForRequest && hdValue.getTimeout32bit().isPresent() ?
						";" + RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TIMEOUT +
								Integer.toUnsignedString(hdValue.getTimeout32bit().get())
						: "");
	}

	public static @NonNull String helperBuildHeaderValue_transport(
				@NonNull RtspProtoHeaderTypeTransport hdValue,
				boolean isForRequest
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

		StringBuilder sb = new StringBuilder();

		if (hdValue.tpSubStream.getIsUdp()) {
			sb.append(hdValue.tpSubStream.getIsEncr() ?
					RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP2
					: RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP2);
		} else {
			sb.append(hdValue.tpSubStream.getIsEncr() ?
					RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPTCP
					: RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP);
		}
		sb.append(";");

		if (! hdValue.tpSubStream.getIsUnicast()) {
			throw new RtspLowInvalidRrException("No other Transport Delivery than Unicast is supported");
		}
		sb.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_UNICAST).append(";");

		if (! isForRequest) {
			if (! hdValue.tpSourceIpOrHost.isBlank()) {
				sb
						.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SOURCEIP)
						.append(hdValue.tpSourceIpOrHost).append(";");
			}
			if (! hdValue.tpDestIpOrHost.isBlank()) {
				sb
						.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_DESTIP)
						.append(hdValue.tpDestIpOrHost).append(";");
			}
		}

		if (hdValue.tpSubStream.getIsUdp()) {
			if (hdValue.tpSubStream.getClientUdpPortRtpPtr().isEmpty()) {
				throw new RtspLowInvalidRrException("Transport Client RTP UDP port must be set");
			}
			if (hdValue.tpSubStream.getClientUdpPortRtcpPtr().isEmpty()) {
				throw new RtspLowInvalidRrException("Transport Client RTCP UDP port must be set");
			}
			if (hdValue.tpSubStream.getClientUdpPortRtpPtr().equals(hdValue.tpSubStream.getClientUdpPortRtcpPtr())) {
				throw new RtspLowInvalidRrException("Transport Client RTP and RTCP UDP ports must be different");
			}
			sb
					.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT)
					.append(Integer.toUnsignedString(hdValue.tpSubStream.getClientUdpPortRtpPtr().getPort16bit().orElseThrow()))
					.append("-")
					.append(Integer.toUnsignedString(hdValue.tpSubStream.getClientUdpPortRtcpPtr().getPort16bit().orElseThrow()));
			if (! isForRequest) {
				sb.append(";");
				if (hdValue.tpSubStream.getServerUdpPortRtpPtr().isEmpty()) {
					throw new RtspLowInvalidRrException("Transport Server RTP UDP port must be set");
				}
				if (hdValue.tpSubStream.getServerUdpPortRtcpPtr().isEmpty()) {
					throw new RtspLowInvalidRrException("Transport Server RTCP UDP port must be set");
				}
				if (hdValue.tpSubStream.getServerUdpPortRtpPtr().equals(hdValue.tpSubStream.getServerUdpPortRtcpPtr())) {
					throw new RtspLowInvalidRrException("Transport Server RTP and RTCP UDP ports must be different");
				}
				sb
						.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SERVERPORT)
						.append(Integer.toUnsignedString(hdValue.tpSubStream.getServerUdpPortRtpPtr().getPort16bit().orElseThrow()))
						.append("-")
						.append(Integer.toUnsignedString(hdValue.tpSubStream.getServerUdpPortRtcpPtr().getPort16bit().orElseThrow()));
			}
		} else {
			if (hdValue.tpSubStream.getClientTcpChannRtpPtr().isEmpty()) {
				throw new RtspLowInvalidRrException("Transport Client RTP TCP channel must be set");
			}
			if (hdValue.tpSubStream.getClientTcpChannRtcpPtr().isEmpty()) {
				throw new RtspLowInvalidRrException("Transport Client RTCP TCP channel must be set");
			}
			if (hdValue.tpSubStream.getClientTcpChannRtpPtr().equals(hdValue.tpSubStream.getClientTcpChannRtcpPtr())) {
				throw new RtspLowInvalidRrException("Transport Client RTP and RTCP TCP channels must be different");
			}
			sb
					.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED)
					.append(Integer.toUnsignedString(hdValue.tpSubStream.getClientTcpChannRtpPtr().getChannel8bit().orElseThrow()))
					.append("-")
					.append(Integer.toUnsignedString(hdValue.tpSubStream.getClientTcpChannRtcpPtr().getChannel8bit().orElseThrow()));
		}

		if (! isForRequest && hdValue.tpSubStream.getIsUnicast() && ! hdValue.tpSsrcId.isEmpty()) {
			sb
					.append(";")
					.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SSRC)  // only valid for unicast transmission
					.append(hdValue.tpSsrcId.toHexString(false));
		}

		if (isForRequest && hdValue.tpMode != RtspTransportMode.NONE) {
			sb
					.append(";")
					.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_MODE)
					.append(hdValue.tpMode.getStrValue());
		}

		return sb.toString();
	}

}

package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.*;
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

	public static @NonNull String helperBuildHeaderValue_contbase(@NonNull RtspProtoHeaderTypeContBase hdValue)
			throws RtspLowInvalidRrException {
		// @TODO add example
		if (hdValue.contentBaseStr.isBlank()) {
			throw new RtspLowInvalidRrException("contentBaseStr cannot be blank");
		}
		return hdValue.contentBaseStr;
	}

	public static @NonNull String helperBuildHeaderValue_contlen(@NonNull RtspProtoHeaderTypeContLen hdValue)
			throws RtspLowInvalidRrException {
		if (hdValue.getContentLen32bit().isEmpty()) {
			throw new RtspLowInvalidRrException("Content-Length must be set");
		}
		return Integer.toUnsignedString(hdValue.getContentLen32bit().get());
	}

	public static @NonNull String helperBuildHeaderValue_conttype(@NonNull RtspProtoHeaderTypeContType hdValue)
			throws RtspLowInvalidRrException {
		if (hdValue.contentType == RtspMimeType.NONE) {
			throw new RtspLowInvalidRrException("Content-Type must be set");
		}
		return hdValue.contentType.getStrValue();
	}

	public static @NonNull String helperBuildHeaderValue_cseq(@NonNull RtspProtoHeaderTypeCseq hdValue)
			throws RtspLowInvalidRrException {
		if (hdValue.getCseqNr32bit().isEmpty()) {
			throw new RtspLowInvalidRrException("CSeq must be set");
		}
		return Integer.toUnsignedString(hdValue.getCseqNr32bit().get());
	}

	public static @NonNull String helperBuildHeaderValue_date(@NonNull RtspProtoHeaderTypeDate hdValue) {
		/*
		 * Example:
		 *   "Fri, 03 Apr 2026 10:54:06 GMT"
		 */
		return DateTimeFormatter.RFC_1123_DATE_TIME
				.withLocale(Locale.ENGLISH)
				.format(hdValue.dateObj.atZone(ZoneOffset.UTC));
	}

	public static @NonNull String helperBuildHeaderValue_range(@NonNull RtspProtoHeaderTypeRange hdValue)
			throws RtspLowInvalidRrException {
		// @TODO add example
		if (hdValue.rangeStr.isBlank()) {
			throw new RtspLowInvalidRrException("rangeStr cannot be blank");
		}
		return hdValue.rangeStr;
	}

	public static @NonNull String helperBuildHeaderValue_session(
				@NonNull RtspProtoHeaderTypeSession hdValue,
				boolean isForRequest
			) throws RtspLowInvalidRrException {
		// @TODO add example
		if (hdValue.sessionIdStr.isBlank()) {
			throw new RtspLowInvalidRrException("sessionIdStr cannot be blank");
		}
		return hdValue.sessionIdStr +
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

		if (hdValue.tpIsUdp) {
			sb.append(hdValue.tpIsEncr ?
					RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP2
					: RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP2);
		} else {
			sb.append(hdValue.tpIsEncr ?
					RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPTCP
					: RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP);
		}
		sb.append(";");

		if (! hdValue.tpIsUnicast) {
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

		if (hdValue.tpIsUdp) {
			if (hdValue.getClientUdpPortRtp16bit().isEmpty()) {
				throw new RtspLowInvalidRrException("Transport Client RTP UDP port must be set");
			}
			if (hdValue.getClientUdpPortRtcp16bit().isEmpty()) {
				throw new RtspLowInvalidRrException("Transport Client RTCP UDP port must be set");
			}
			if (hdValue.getClientUdpPortRtp16bit().get().equals(hdValue.getClientUdpPortRtcp16bit().get())) {
				throw new RtspLowInvalidRrException("Transport Client RTP and RTCP UDP ports must be different");
			}
			sb
					.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT)
					.append(Short.toUnsignedInt(hdValue.getClientUdpPortRtp16bit().get()))
					.append("-")
					.append(Short.toUnsignedInt(hdValue.getClientUdpPortRtcp16bit().get()));
			if (! isForRequest) {
				sb.append(";");
				if (hdValue.getServerUdpPortRtp16bit().isEmpty()) {
					throw new RtspLowInvalidRrException("Transport Server RTP UDP port must be set");
				}
				if (hdValue.getServerUdpPortRtcp16bit().isEmpty()) {
					throw new RtspLowInvalidRrException("Transport Server RTCP UDP port must be set");
				}
				if (hdValue.getServerUdpPortRtp16bit().get().equals(hdValue.getServerUdpPortRtcp16bit().get())) {
					throw new RtspLowInvalidRrException("Transport Server RTP and RTCP UDP ports must be different");
				}
				sb
						.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SERVERPORT)
						.append(Short.toUnsignedInt(hdValue.getServerUdpPortRtp16bit().get()))
						.append("-")
						.append(Short.toUnsignedInt(hdValue.getServerUdpPortRtcp16bit().get()));
			}
		} else {
			if (hdValue.getClientTcpChannRtp16bit().isEmpty()) {
				throw new RtspLowInvalidRrException("Transport Client RTP TCP channel must be set");
			}
			if (hdValue.getClientTcpChannRtcp16bit().isEmpty()) {
				throw new RtspLowInvalidRrException("Transport Client RTCP TCP channel must be set");
			}
			if (hdValue.getClientTcpChannRtp16bit().get().equals(hdValue.getClientTcpChannRtcp16bit().get())) {
				throw new RtspLowInvalidRrException("Transport Client RTP and RTCP TCP channels must be different");
			}
			sb
					.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED)
					.append(Short.toUnsignedInt(hdValue.getClientTcpChannRtp16bit().get()))
					.append("-")
					.append(Short.toUnsignedInt(hdValue.getClientTcpChannRtcp16bit().get()));
		}

		if (! isForRequest && hdValue.tpIsUnicast && hdValue.getSsrcId32bit().isPresent()) {
			sb
					.append(";")
					.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_SSRC)  // only valid for unicast transmission
					.append(RtspLowBuilderHelper.helperBuildHexString(hdValue.getSsrcId32bit().get()));
		}

		if (isForRequest && hdValue.tpMode != RtspTransportMode.NONE) {
			sb
					.append(";")
					.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_SET_TP_MODE)
					.append(hdValue.tpMode.getStrValue());
		}

		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	public static @NonNull String helperBuildHexString(int value) {
		return String.format("%08X", value);
	}

}

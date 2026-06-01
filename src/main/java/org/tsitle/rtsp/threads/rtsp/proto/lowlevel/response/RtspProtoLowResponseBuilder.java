package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.response;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoMessageType;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgStructuredResponse;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.RtspProtocolVersion;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.header.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants.*;

public final class RtspProtoLowResponseBuilder {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowResponseBuilder(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoLowMsgRaw buildResponse(@NonNull RtspProtoLowMsgStructuredResponse input) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildResponse()";

		if (input.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspInvalidResponseException(FNC_NAME + ": rtspProtoVersion cannot be NONE");
		}

		RtspProtoLowMsgRaw resObj = new RtspProtoLowMsgRaw();

		// set response line
		String tmpRtspProtoVersStr = (input.rtspProtoVersion == RtspProtocolVersion.RTSP_V1_0 ?
				RTSP_RR_CMD_PROTOCOL_VERSION_1 : RTSP_RR_CMD_PROTOCOL_VERSION_2);
		resObj.mainLine = String.format("%s %d %s", tmpRtspProtoVersStr, input.statusCode.getValue(), input.statusCode.getReasonPhrase());

		// set headers
		for (Map.Entry<@NonNull RtspProtoLowHeaderKey, @NonNull RtspProtoLowHeaderEntryResponse> entry : input.headers.entrySet()) {
			String tmpHdKey;
			String tmpHdVal = switch (entry.getKey()) {
					case AUTH -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_XXX_AUTH_SERVER;
						yield buildHeaderValue_com_auth(entry.getValue().hdValAuth);
					}
					case CONTENT_BASE -> {  // @TODO add to request parser
						tmpHdKey = RTSP_RR_HEADER_TOKEN_DES_CONTBASE;
						yield buildHeaderValue_describe_contbase(entry.getValue().hdValContBase);
					}
					case CONTENT_TYPE -> {  // @TODO add to request parser
						tmpHdKey = RTSP_RR_HEADER_TOKEN_XXX_CONTTYPE;
						yield buildHeaderValue_com_conttype(entry.getValue().hdValContType);
					}
					case CSEQ -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_XXX_CSEQ;
						yield buildHeaderValue_com_cseq(entry.getValue().hdValCseq);
					}
					case DATE -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_XXX_DATE;
						yield buildHeaderValue_com_date(entry.getValue().hdValDate);
					}
					case PUBLIC -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_OPT_PUBLIC;
						yield buildHeaderValue_options_public(entry.getValue().hdValPublic);
					}
					case RANGE -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_PLA_RANGE;
						yield buildHeaderValue_play_range(entry.getValue().hdValRange);
					}
					case RTPINFO -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_PLA_RTPINFO;
						yield buildHeaderValue_play_rtpinfo(entry.getValue().hdValRtpinfo, input.rtspProtoVersion);
					}
					case SERVER -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_XXX_SERVER;
						yield buildHeaderValue_com_server(entry.getValue().hdValServer);
					}
					case SESSION -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_XXX_SESSION;
						yield buildHeaderValue_com_session(entry.getValue().hdValSession);
					}
					case TRANSPORT -> {
						tmpHdKey = RTSP_RR_HEADER_TOKEN_SET_TRANSPORT;
						yield buildHeaderValue_setup_transport(entry.getValue().hdValTransport, input.rtspProtoVersion);
					}
					default -> throw new RtspInvalidResponseException(FNC_NAME + ": Unknown header key: " + entry.getKey());
				};
			resObj.headerLines.add(tmpHdKey + " " + tmpHdVal);
		}

		// set body
		if (! input.body.isBlank()) {
			if (! input.headers.containsKey(RtspProtoLowHeaderKey.CONTENT_TYPE)) {
				throw new RtspInvalidResponseException(FNC_NAME + ": Have a msg body but no ContentType header");
			}
			resObj.headerLines.add(RTSP_RR_HEADER_TOKEN_XXX_CONTLEN + " " + Integer.toUnsignedString(input.body.length()));
			resObj.body = input.body;  // @TODO add to request parser
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHeaderValue_com_auth(@NonNull RtspProtoLowHeaderTypeAuth hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.authRealm.isBlank()) {
			throw new RtspInvalidResponseException("Auth Realm cannot be blank");
		}
		if (hdValue.authNonce.isBlank()) {
			throw new RtspInvalidResponseException("Auth Nonce cannot be blank");
		}
		//noinspection SwitchStatementWithTooFewBranches
		String tmpAlgoStr = switch (hdValue.authAlgo) {
				case MD5 -> RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_ALGO_MD5;
				default -> throw new RtspInvalidResponseException("Unsupported Auth Algorithm: " + hdValue.authAlgo);
			};
		return RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX +
				RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM + "\"" + hdValue.authRealm + "\", " +
				RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE + "\"" + hdValue.authNonce + "\", " +
				RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO + "\"" + tmpAlgoStr + "\"";
	}

	private static @NonNull String buildHeaderValue_describe_contbase(@NonNull RtspProtoLowHeaderTypeContBase hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.contentBaseStr.isBlank()) {
			throw new RtspInvalidResponseException("contentBaseStr cannot be blank");
		}
		return hdValue.contentBaseStr;
	}

	private static @NonNull String buildHeaderValue_com_conttype(@NonNull RtspProtoLowHeaderTypeContType hdValue)
			throws RtspInvalidResponseException {
		//noinspection SwitchStatementWithTooFewBranches
		return switch (hdValue.contentType) {
				case SDP -> RTSP_RR_HEADER_PARAM_VAL_XXX_CT_SDP;
				default -> throw new RtspInvalidResponseException("Unsupported content type: " + hdValue.contentType);
			};
	}

	private static @NonNull String buildHeaderValue_com_cseq(@NonNull RtspProtoLowHeaderTypeCseq hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.getCseqNr32bit().isEmpty()) {
			throw new RtspInvalidResponseException("CSeq must be set");
		}
		return Integer.toUnsignedString(hdValue.getCseqNr32bit().get());
	}

	private static @NonNull String buildHeaderValue_com_date(@NonNull RtspProtoLowHeaderTypeDate hdValue) {
		// Date: Fri, 03 Apr 2026 10:54:06 GMT
		return DateTimeFormatter.RFC_1123_DATE_TIME
				.withLocale(Locale.ENGLISH)
				.format(hdValue.dateObj.atZone(ZoneOffset.UTC));
	}

	private static @NonNull String buildHeaderValue_options_public(@NonNull RtspProtoLowHeaderTypePublic hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.messageTypes.contains(RtspProtoMessageType.UNKNOWN)) {
			throw new RtspInvalidResponseException("UNKNOWN message type in PUBLIC header");
		}
		List<String> tmpList = hdValue.messageTypes.stream()
				.map(Enum::name)
				.toList();
		return String.join(", ", tmpList);
	}

	private static @NonNull String buildHeaderValue_play_range(@NonNull RtspProtoLowHeaderTypeRange hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.rangeStr.isBlank()) {
			throw new RtspInvalidResponseException("rangeStr cannot be blank");
		}
		return hdValue.rangeStr;
	}

	private static @NonNull String buildHeaderValue_play_rtpinfo(
				@NonNull RtspProtoLowHeaderTypeRtpinfo hdValue,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspInvalidResponseException {
		Optional<RtspProtoLowHeaderTypeRtpinfo.SubStream> tmpSs1 = hdValue.getSubStream1();
		Optional<RtspProtoLowHeaderTypeRtpinfo.SubStream> tmpSs2 = hdValue.getSubStream2();
		if (tmpSs1.isEmpty()) {
			throw new RtspInvalidResponseException("substream 1 is missing");
		}

		String resS = addRtpInfoForSubStream(tmpSs1.get(), rtspProtocolVersion);
		if (tmpSs2.isPresent()) {
			resS += "," + addRtpInfoForSubStream(tmpSs2.get(), rtspProtocolVersion);
		}

		return resS;
	}

	private static @NonNull String addRtpInfoForSubStream(
				RtspProtoLowHeaderTypeRtpinfo.@NonNull SubStream subStreamInfo,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspInvalidResponseException {
		/*
		 * RTSP v1:
		 *   Rtp-Info:
		 *     url=rtsp://foo.com/bar.file;seq=232433;rtptime=972948234
		 *   See https://datatracker.ietf.org/doc/html/rfc2326#section-12.33
		 * RTSP v2:
		 *   Rtp-Info:
		 *     url="rtsp://example.com/audio" ssrc=0D12F123:seq=14783;rtptime=2345962545
		 *   See https://datatracker.ietf.org/doc/html/rfc7826#section-13.4
		 */

		StringBuilder tmpSb = new StringBuilder();

		if (subStreamInfo.urlStr.isBlank()) {
			throw new RtspInvalidResponseException("urlStr cannot be blank");
		}
		tmpSb.append(RTSP_RR_HEADER_PARAM_KEY_PLA_RI_URL);
		if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V2_0) {
			tmpSb.append("\"");
		}
		tmpSb.append(subStreamInfo.urlStr);
		if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V2_0) {
			tmpSb.append("\" ");
			if (subStreamInfo.getSsrcId32bit().isEmpty()) {
				throw new RtspInvalidResponseException("ssrcId must be set for RTSP v2.0");
			}
			tmpSb.append(RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SSRC).append(buildHexString(subStreamInfo.getSsrcId32bit().get()));
			tmpSb.append(":");
		} else {
			tmpSb.append(";");
		}
		if (subStreamInfo.getSeqNr16bit().isEmpty()) {
			throw new RtspInvalidResponseException("seqNr must be set");
		}
		if (subStreamInfo.getRtpTimestamp32bit().isEmpty()) {
			throw new RtspInvalidResponseException("rtpTimestamp must be set");
		}
		tmpSb
				.append(RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SEQ).append(Short.toUnsignedInt(subStreamInfo.getSeqNr16bit().get()))
				.append(";")
				.append(RTSP_RR_HEADER_PARAM_KEY_PLA_RI_RTPTIME).append(Integer.toUnsignedString(subStreamInfo.getRtpTimestamp32bit().get()));
		return tmpSb.toString();
	}

	private static @NonNull String buildHeaderValue_com_server(@NonNull RtspProtoLowHeaderTypeServer hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.serverStr.isBlank()) {
			throw new RtspInvalidResponseException("serverStr cannot be blank");
		}
		return hdValue.serverStr;
	}

	private static @NonNull String buildHeaderValue_com_session(@NonNull RtspProtoLowHeaderTypeSession hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.sessionIdStr.isBlank()) {
			throw new RtspInvalidResponseException("sessionIdStr cannot be blank");
		}
		return hdValue.sessionIdStr +
				(hdValue.timeout >= 0 ? ";" + RTSP_RR_HEADER_PARAM_KEY_SET_TIMEOUT + Integer.toUnsignedString(hdValue.timeout) : "");
	}

	private static @NonNull String buildHeaderValue_setup_transport(
				@NonNull RtspProtoLowHeaderTypeTransport hdValue,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspInvalidResponseException {
		/*
		 * Transport:
		 *   RTP/AVP;unicast;destination=10.55.0.5;source=192.168.5.20;client_port=38852-38853;server_port=6970-6971;ssrc=DEADBEEF
		 * See https://datatracker.ietf.org/doc/html/rfc7826#section-13.3
		 */

		if (hdValue.getSsrcId32bit().isEmpty()) {
			throw new RtspInvalidResponseException("SSRC identifier must be set");
		}

		StringBuilder sb = new StringBuilder();

		if (hdValue.tpIsUdp) {
			sb.append(hdValue.tpIsEncr ? RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPUDP2 : RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPUDP2);
		} else {
			sb.append(hdValue.tpIsEncr ? RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPSAVPTCP : RTSP_RR_HEADER_PARAM_VAL_SET_TP_RTPAVPTCP);
		}
		sb.append(";");

		if (! hdValue.tpIsUnicast) {
			throw new RtspInvalidResponseException("No other Transport Casting Mode than UNICAST is supported");
		}
		if (hdValue.tpSourceIpOrHost.isBlank()) {
			throw new RtspInvalidResponseException("Transport source IP/host cannot be blank");
		}
		if (hdValue.tpDestIpOrHost.isBlank()) {
			throw new RtspInvalidResponseException("Transport destination IP/host cannot be blank");
		}
		sb
				.append(RTSP_RR_HEADER_PARAM_VAL_SET_TP_UNICAST).append(";")
				.append(RTSP_RR_HEADER_PARAM_KEY_SET_TP_DESTIP).append(hdValue.tpDestIpOrHost).append(";")
				.append(RTSP_RR_HEADER_PARAM_KEY_SET_TP_SOURCEIP).append(hdValue.tpSourceIpOrHost).append(";");

		if (hdValue.tpIsUdp) {
			if (hdValue.getClientUdpPortRtp16bit().isEmpty()) {
				throw new RtspInvalidResponseException("Transport Client RTP UDP port must be set");
			}
			if (hdValue.getClientUdpPortRtcp16bit().isEmpty()) {
				throw new RtspInvalidResponseException("Transport Client RTCP UDP port must be set");
			}
			if (hdValue.getClientUdpPortRtp16bit().get().equals(hdValue.getClientUdpPortRtcp16bit().get())) {
				throw new RtspInvalidResponseException("Transport Client RTP and RTCP UDP ports must be different");
			}
			sb
					.append(RTSP_RR_HEADER_PARAM_KEY_SET_TP_CLIENTPORT)
					.append(Short.toUnsignedInt(hdValue.getClientUdpPortRtp16bit().get()))
					.append("-")
					.append(Short.toUnsignedInt(hdValue.getClientUdpPortRtcp16bit().get()))
					.append(";");
			if (hdValue.getServerUdpPortRtp16bit().isEmpty()) {
				throw new RtspInvalidResponseException("Transport Server RTP UDP port must be set");
			}
			if (hdValue.getServerUdpPortRtcp16bit().isEmpty()) {
				throw new RtspInvalidResponseException("Transport Server RTCP UDP port must be set");
			}
			if (hdValue.getServerUdpPortRtp16bit().get().equals(hdValue.getServerUdpPortRtcp16bit().get())) {
				throw new RtspInvalidResponseException("Transport Server RTP and RTCP UDP ports must be different");
			}
			sb
					.append(RTSP_RR_HEADER_PARAM_KEY_SET_TP_SERVERPORT)
					.append(Short.toUnsignedInt(hdValue.getServerUdpPortRtp16bit().get()))
					.append("-")
					.append(Short.toUnsignedInt(hdValue.getServerUdpPortRtcp16bit().get()));
		} else {
			if (hdValue.getClientTcpChannRtp16bit().isEmpty()) {
				throw new RtspInvalidResponseException("Transport Client RTP TCP channel must be set");
			}
			if (hdValue.getClientTcpChannRtcp16bit().isEmpty()) {
				throw new RtspInvalidResponseException("Transport Client RTCP TCP channel must be set");
			}
			if (hdValue.getClientTcpChannRtp16bit().get().equals(hdValue.getClientTcpChannRtcp16bit().get())) {
				throw new RtspInvalidResponseException("Transport Client RTP and RTCP TCP channels must be different");
			}
			sb
					.append(RTSP_RR_HEADER_PARAM_KEY_SET_TP_INTERLEAVED)
					.append(Short.toUnsignedInt(hdValue.getClientTcpChannRtp16bit().get()))
					.append("-")
					.append(Short.toUnsignedInt(hdValue.getClientTcpChannRtcp16bit().get()));
		}
		if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V2_0 && hdValue.tpIsUnicast) {
			sb
					.append(";")
					.append(RTSP_RR_HEADER_PARAM_KEY_SET_TP_SSRC)  // only valid for unicast transmission
					.append(buildHexString(hdValue.getSsrcId32bit().get()));
		}

		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHexString(int value) {
		return String.format("%08X", value);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

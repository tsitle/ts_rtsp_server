package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.response;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidResponseException;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper.RtspLowBuilderHelper;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper.RtspLowInvalidRrException;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RtspProtoLowResponseBuilder {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowResponseBuilder(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoLowMsgRaw buildMessage(@NonNull RtspProtoHighMsgStructuredResponse input)
			throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildMessage()";

		if (input.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspInvalidResponseException(FNC_NAME + ": rtspProtoVersion cannot be NONE");
		}

		RtspProtoLowMsgRaw resObj = new RtspProtoLowMsgRaw();

		// set status line
		resObj.mainLine = String.format("%s %d %s",
				input.rtspProtoVersion.getStrValue(), input.statusCode.getIntValue(), input.statusCode.getReasonPhrase());

		// set headers
		try {
			buildAllHeaders(input, resObj);
		} catch (RtspLowInvalidRrException e) {
			throw new RtspInvalidResponseException(FNC_NAME + ": " + e.getMessage());
		}

		// set body
		if (input.messageType == RtspMessageType.DESCRIBE ||
				input.messageType == RtspMessageType.GET_PARAMETER || input.messageType == RtspMessageType.SET_PARAMETER) {
			buildBody(input, resObj);
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildAllHeaders(
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoLowMsgRaw output
			) throws RtspInvalidResponseException, RtspLowInvalidRrException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildAllHeaders()";

		for (Map.Entry<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryResponse> entry : input.headers.entrySet()) {
			String tmpHdVal = switch (entry.getKey()) {
					case AUTH_SERVER -> buildHeaderValue_com_auth_server(entry.getValue().hdValAuthServer);
					case CONNECTION -> buildHeaderValue_com_connection(entry.getValue().hdValConnection);
					case CONTENT_BASE -> buildHeaderValue_describe_contbase(input.messageType, entry.getValue().hdValContBase);
					case CONTENT_ENC -> buildHeaderValue_com_contenc(input.messageType, entry.getValue().hdValContEnc);
					case CONTENT_LANG -> buildHeaderValue_com_contlang(input.messageType, entry.getValue().hdValContLang);
					case CONTENT_LEN -> buildHeaderValue_com_contlen(input.messageType);
					case CONTENT_TYPE -> buildHeaderValue_com_conttype(input.messageType);
					case CSEQ -> buildHeaderValue_com_cseq(entry.getValue().hdValCseq);
					case DATE -> buildHeaderValue_com_date(entry.getValue().hdValDate);
					case PUBLIC -> buildHeaderValue_options_public(input.messageType, entry.getValue().hdValPublic);
					case RANGE -> buildHeaderValue_play_range(input.messageType, entry.getValue().hdValRange);
					case RTPINFO -> buildHeaderValue_play_rtpinfo(input.messageType, entry.getValue().hdValRtpinfo, input.rtspProtoVersion);
					case SERVER -> buildHeaderValue_com_server(entry.getValue().hdValServer);
					case SESSION -> buildHeaderValue_com_session(entry.getValue().hdValSession);
					case TRANSPORT -> buildHeaderValue_setup_transport(input.messageType, entry.getValue().hdValTransport);
					case UNSUPPORTED -> buildHeaderValue_com_unsupported(entry.getValue().hdValUnsupported);
					default -> throw new RtspInvalidResponseException(FNC_NAME + ": Unknown header key: " + entry.getKey());
				};
			if (tmpHdVal.isBlank()) {
				continue;
			}
			output.headerLines.add(entry.getKey().getStrValue() + ": " + tmpHdVal);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHeaderValue_com_auth_server(@NonNull RtspProtoHeaderTypeAuthServer hdValue)
			throws RtspInvalidResponseException {
		/*
		 * Example:
		 *   "WWW-Authenticate: Digest realm=\"Abcdef Some\", nonce=\"xxx\", algorithm=\"MD5\""
		 */
		if (hdValue.authRealm.isBlank()) {
			throw new RtspInvalidResponseException("Auth Realm cannot be blank");
		}
		if (hdValue.authNonce.isBlank()) {
			throw new RtspInvalidResponseException("Auth Nonce cannot be blank");
		}
		if (hdValue.authAlgo == RtspAuthAlgo.NONE) {
			throw new RtspInvalidResponseException("Auth Algo must be set");
		}
		return RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM + "\"" + hdValue.authRealm + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE + "\"" + hdValue.authNonce + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO + "\"" + hdValue.authAlgo.getStrValue() + "\"";
	}

	private static @NonNull String buildHeaderValue_com_connection(@NonNull RtspProtoHeaderTypeConnection hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_connection(hdValue);
	}

	private static @NonNull String buildHeaderValue_describe_contbase(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeContBase hdValue
			) throws RtspLowInvalidRrException, RtspInvalidResponseException {
		if (messageType != RtspMessageType.DESCRIBE) {
			throw new RtspInvalidResponseException("Content-Base header is only valid for DESCRIBE responses");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_contbase(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contenc(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeContEnc hdValue
			) throws RtspLowInvalidRrException, RtspInvalidResponseException {
		allowOnlyDescribeGetParameter("Content-Encoding", messageType);
		return RtspLowBuilderHelper.helperBuildHeaderValue_contenc(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contlang(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeContLang hdValue
			) throws RtspLowInvalidRrException, RtspInvalidResponseException {
		allowOnlyDescribeGetParameter("Content-Language", messageType);
		return RtspLowBuilderHelper.helperBuildHeaderValue_contlang(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contlen(@NonNull RtspMessageType messageType)
			throws RtspInvalidResponseException {
		allowOnlyDescribeGetParameter("Content-Length", messageType);
		return "";  // add this header when adding the body
	}

	private static @NonNull String buildHeaderValue_com_conttype(@NonNull RtspMessageType messageType)
			throws RtspInvalidResponseException {
		allowOnlyDescribeGetParameter("Content-Type", messageType);
		return "";  // add this header when adding the body
	}

	private static @NonNull String buildHeaderValue_com_cseq(@NonNull RtspProtoHeaderTypeCseq hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_cseq(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_date(@NonNull RtspProtoHeaderTypeDate hdValue) {
		return RtspLowBuilderHelper.helperBuildHeaderValue_date(hdValue);
	}

	private static @NonNull String buildHeaderValue_options_public(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypePublic hdValue
			) throws RtspInvalidResponseException {
		if (messageType != RtspMessageType.OPTIONS) {
			throw new RtspInvalidResponseException("Public header is only valid for OPTIONS responses");
		}
		if (hdValue.messageTypes.contains(RtspMessageType.UNKNOWN)) {
			throw new RtspInvalidResponseException("UNKNOWN message type in Public header");
		}
		List<String> tmpList = hdValue.messageTypes.stream()
				.map(Enum::name)
				.toList();
		return String.join(", ", tmpList);
	}

	private static @NonNull String buildHeaderValue_play_range(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeRange hdValue
			) throws RtspLowInvalidRrException, RtspInvalidResponseException {
		if (messageType != RtspMessageType.PLAY) {
			throw new RtspInvalidResponseException("Range header is only valid for PLAY responses");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_range(hdValue);
	}

	private static @NonNull String buildHeaderValue_play_rtpinfo(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeRtpinfo hdValue,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspInvalidResponseException {
		if (messageType != RtspMessageType.PLAY) {
			throw new RtspInvalidResponseException("RTP-Info header is only valid for PLAY responses");
		}
		Optional<RtspProtoHeaderTypeRtpinfo.SubStream> tmpSs1 = hdValue.getSubStream1();
		Optional<RtspProtoHeaderTypeRtpinfo.SubStream> tmpSs2 = hdValue.getSubStream2();
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
				RtspProtoHeaderTypeRtpinfo.@NonNull SubStream subStreamInfo,
				@NonNull RtspProtocolVersion rtspProtocolVersion
			) throws RtspInvalidResponseException {
		/*
		 * Example:
		 *   RTSP v1 (see https://datatracker.ietf.org/doc/html/rfc2326#section-12.33):
		 *     url=rtsp://foo.com/bar.file;seq=232433;rtptime=972948234
		 *   RTSP v2 (see https://datatracker.ietf.org/doc/html/rfc7826#section-13.4):
		 *     url="rtsp://example.com/audio" ssrc=0D12F123:seq=14783;rtptime=2345962545
		 */

		StringBuilder tmpSb = new StringBuilder();

		if (subStreamInfo.urlStr.isBlank()) {
			throw new RtspInvalidResponseException("urlStr cannot be blank");
		}
		tmpSb.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_URL);
		if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V2) {
			tmpSb.append("\"");
		}
		tmpSb.append(subStreamInfo.urlStr);
		if (rtspProtocolVersion == RtspProtocolVersion.RTSP_V2) {
			tmpSb.append("\" ");
			if (subStreamInfo.getSsrcId32bit().isEmpty()) {
				throw new RtspInvalidResponseException("ssrcId must be set for RTSP v2.0");
			}
			tmpSb.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SSRC)
					.append(RtspLowBuilderHelper.helperBuildHexString(subStreamInfo.getSsrcId32bit().get()));
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
				.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_SEQ)
						.append(Short.toUnsignedInt(subStreamInfo.getSeqNr16bit().get()))
				.append(";")
				.append(RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_PLA_RI_RTPTIME)
						.append(Integer.toUnsignedString(subStreamInfo.getRtpTimestamp32bit().get()));
		return tmpSb.toString();
	}

	private static @NonNull String buildHeaderValue_com_server(@NonNull RtspProtoHeaderTypeServer hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.serverStr.isBlank()) {
			throw new RtspInvalidResponseException("serverStr cannot be blank");
		}
		return hdValue.serverStr;
	}

	private static @NonNull String buildHeaderValue_com_session(@NonNull RtspProtoHeaderTypeSession hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_session(hdValue, false);
	}

	private static @NonNull String buildHeaderValue_setup_transport(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeTransport hdValue
			) throws RtspLowInvalidRrException, RtspInvalidResponseException {
		if (messageType != RtspMessageType.SETUP) {
			throw new RtspInvalidResponseException("Transport header is only valid for SETUP responses");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_transport(hdValue, false);
	}

	private static @NonNull String buildHeaderValue_com_unsupported(@NonNull RtspProtoHeaderTypeUnsupported hdValue)
			throws RtspInvalidResponseException {
		if (hdValue.unsupportedFeatureStr.isBlank()) {
			throw new RtspInvalidResponseException("unsupportedFeatureStr cannot be blank");
		}
		return hdValue.unsupportedFeatureStr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void allowOnlyDescribeGetParameter(@NonNull String hdDesc, @NonNull RtspMessageType messageType)
			throws RtspInvalidResponseException {
		if (messageType != RtspMessageType.DESCRIBE && messageType != RtspMessageType.GET_PARAMETER) {
			throw new RtspInvalidResponseException(hdDesc + " header is only valid for " +
					"DESCRIBE/GET_PARAMETER responses");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildBody(
				@NonNull RtspProtoHighMsgStructuredResponse input,
				@NonNull RtspProtoLowMsgRaw output
			) throws RtspInvalidResponseException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildBody()";

		RtspProtoHeaderTypeContType outpHdValueContTp = new RtspProtoHeaderTypeContType();

		switch (input.messageType) {
			case DESCRIBE:
				if (input.statusCode != RtspStatusCode.OK) {
					return;
				}
				if (input.bodyDescribeSdp.isEmpty()) {
					throw new RtspInvalidResponseException(FNC_NAME + ": bodyDescribeSdp cannot be empty");
				}
				output.body = String.join(RtspProtoLowMsgConstants.CRLF, input.bodyDescribeSdp);
				if (! output.body.endsWith(RtspProtoLowMsgConstants.CRLF)) {
					output.body += RtspProtoLowMsgConstants.CRLF;
				}
				outpHdValueContTp.contentType = RtspMimeType.SDP;
				break;
			case GET_PARAMETER:
			case SET_PARAMETER:
				StringBuilder sb = new StringBuilder();
				if (! input.bodyGetSetInvalidParams.isEmpty()) {
					for (String entryKey : input.bodyGetSetInvalidParams) {
						sb.append(entryKey).append(RtspProtoLowMsgConstants.CRLF);
					}
				} else if (input.messageType == RtspMessageType.GET_PARAMETER && ! input.bodyGetParamKv.isEmpty()) {
					for (Map.Entry<@NonNull String, @NonNull String> entry : input.bodyGetParamKv.entrySet()) {
						sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(RtspProtoLowMsgConstants.CRLF);
					}
				}
				output.body = sb.toString();
				outpHdValueContTp.contentType = RtspMimeType.PARAMETERS;
				break;
			default:
				break;
		}

		if (output.body.isBlank()) {
			return;
		}

		// Content-Type
		{
			String tmpHdVal;
			try {
				tmpHdVal = RtspLowBuilderHelper.helperBuildHeaderValue_conttype(outpHdValueContTp);
			} catch (RtspLowInvalidRrException e) {
				throw new RtspInvalidResponseException(FNC_NAME + ": body Content-Type: " + e.getMessage());
			}
			output.headerLines.add(RtspHeaderKey.CONTENT_TYPE.getStrValue() + ": " + tmpHdVal);
		}
		// Content-Length
		{
			RtspProtoHeaderTypeContLen hdValue = new RtspProtoHeaderTypeContLen();
			try {
				hdValue.setContentLen32bit(output.body.length());
			} catch (RtspNumberRangeException e) {
				throw new RtspInvalidResponseException(FNC_NAME + ": body length out of range: " + e.getMessage());
			}
			String tmpHdVal;
			try {
				tmpHdVal = RtspLowBuilderHelper.helperBuildHeaderValue_contlen(hdValue);
			} catch (RtspLowInvalidRrException e) {
				throw new RtspInvalidResponseException(e.getMessage());
			}
			output.headerLines.add(RtspHeaderKey.CONTENT_LEN.getStrValue() + ": " + tmpHdVal);
		}
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

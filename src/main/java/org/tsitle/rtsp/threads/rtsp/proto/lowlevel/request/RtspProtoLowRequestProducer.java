package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper.RtspLowBuilderHelper;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper.RtspLowInvalidRrException;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public final class RtspProtoLowRequestProducer {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowRequestProducer(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoLowMsgRaw buildMessage(@NonNull RtspProtoHighMsgStructuredRequest input) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildMessage()";

		if (input.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspInvalidRequestException(FNC_NAME + ": rtspProtoVersion cannot be NONE");
		}

		RtspProtoLowMsgRaw resObj = new RtspProtoLowMsgRaw();

		// set request line
		resObj.mainLine = String.format("%s %s %s",
				input.messageType.name(), buildResourceUrlForRequestLine(input.resourceUrl), input.rtspProtoVersion.getStrValue());

		// set headers
		try {
			buildAllHeaders(input, resObj);
		} catch (RtspLowInvalidRrException e) {
			throw new RtspInvalidRequestException(FNC_NAME + ": " + e.getMessage());
		}

		// set body
		if (input.headers.containsKey(RtspHeaderKey.CONTENT_TYPE)) {
			buildBody(input, resObj);
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildResourceUrlForRequestLine(@NonNull String inputUrl) throws RtspInvalidRequestException {
		boolean isRtsps = inputUrl.startsWith(RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL + "://");
		if (! (inputUrl.startsWith(RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL + "://") || isRtsps)) {
			throw new RtspInvalidRequestException("Invalid protocol in URL '" + inputUrl + "'");
		}
		// rewrite the URL to get rid of any fragments and userinfo (username + password)
		URI tmpUri = URI.create(inputUrl);
		int tmpPort = tmpUri.getPort();
		String resS = (isRtsps ? RtspProtoLowMsgConstants.RTSPS_URL_PROTOCOL : RtspProtoLowMsgConstants.RTSP_URL_PROTOCOL) +
				"://" + tmpUri.getHost() +
				(tmpPort != -1 ? ":" + tmpUri.getPort() : "") + tmpUri.getPath();
		if (tmpUri.getQuery() != null) {
			resS += "?" + tmpUri.getQuery();
		}
		return resS;
	}

	private void buildAllHeaders(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoLowMsgRaw output
			) throws RtspInvalidRequestException, RtspLowInvalidRrException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildAllHeaders()";

		for (Map.Entry<@NonNull RtspHeaderKey, @NonNull RtspProtoHeaderEntryRequest> entry : input.headers.entrySet()) {
			String tmpHdVal = switch (entry.getKey()) {
					case ACCEPT -> buildHeaderValue_describe_accept(input.messageType, entry.getValue().hdValAccept);
					case AUTH_CLIENT -> buildHeaderValue_com_auth_client(entry.getValue().hdValAuthClient);
					case CONNECTION -> buildHeaderValue_com_connection(entry.getValue().hdValConnection);
					case CONTENT_BASE -> buildHeaderValue_announce_contbase(input.messageType, entry.getValue().hdValContBase);
					case CONTENT_ENC -> buildHeaderValue_com_contenc(input.messageType, entry.getValue().hdValContEnc);
					case CONTENT_LEN -> buildHeaderValue_com_contlen(input.messageType);
					case CONTENT_LANG -> buildHeaderValue_com_contlang(input.messageType, entry.getValue().hdValContLang);
					case CONTENT_TYPE -> buildHeaderValue_com_conttype(input.messageType);
					case CSEQ -> buildHeaderValue_com_cseq(entry.getValue().hdValCseq);
					case DATE -> buildHeaderValue_com_date(entry.getValue().hdValDate);
					case KEYMGMT -> buildHeaderValue_com_keymgmt(input.messageType, entry.getValue().hdValKeymgmt);
					case PROXY_REQU -> buildHeaderValue_com_proxyrequ(entry.getValue().hdValProxyRequ);
					case RANGE -> buildHeaderValue_play_range(input.messageType, entry.getValue().hdValRange);
					case REQUIRE -> buildHeaderValue_com_require(entry.getValue().hdValRequire);
					case SESSION -> buildHeaderValue_com_session(entry.getValue().hdValSession);
					case TRANSPORT -> buildHeaderValue_setup_transport(input.messageType, entry.getValue().hdValTransport);
					case USERAGENT -> buildHeaderValue_com_useragent(entry.getValue().hdValUserAgent);
					default -> throw new RtspInvalidRequestException(FNC_NAME + ": Unknown header key: " + entry.getKey());
				};
			if (tmpHdVal.isBlank()) {
				continue;
			}
			output.headerLines.add(entry.getKey().getStrValue() + ": " + tmpHdVal);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHeaderValue_describe_accept(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeAccept hdValue
			) throws RtspInvalidRequestException {
		if (messageType != RtspMessageType.DESCRIBE) {
			throw new RtspInvalidRequestException("Accept header is only valid for DESCRIBE requests");
		}
		if (hdValue.rtspMimeType == RtspMimeType.NONE) {
			throw new RtspInvalidRequestException("MIME-Type must be set");
		}
		return hdValue.rtspMimeType.getStrValue();
	}

	private static @NonNull String buildHeaderValue_com_auth_client(@NonNull RtspProtoHeaderTypeAuthClient hdValue)
			throws RtspInvalidRequestException {
		/*
		 * Example:
		 *   "Authorization: Digest username=\"admin\", realm=\"Abcdef Some\", nonce=\"xxx\", uri=\"rtsp://xxx:88/videoMain\", response=\"xxx\""
		 */

		if (hdValue.authUser.isBlank()) {
			throw new RtspInvalidRequestException("Auth Username cannot be blank");
		}
		if (hdValue.authRealm.isBlank()) {
			throw new RtspInvalidRequestException("Auth Realm cannot be blank");
		}
		if (hdValue.authNonce.isBlank()) {
			throw new RtspInvalidRequestException("Auth Nonce cannot be blank");
		}
		if (hdValue.authResp.isBlank()) {
			throw new RtspInvalidRequestException("Auth Response cannot be blank");
		}
		if (hdValue.authUri.isBlank()) {
			throw new RtspInvalidRequestException("Auth URI cannot be blank");
		}
		return RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER + "\"" + hdValue.authUser + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM + "\"" + hdValue.authRealm + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE + "\"" + hdValue.authNonce + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP + "\"" + hdValue.authResp + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI + "\"" + hdValue.authUri + "\"";
	}

	private static @NonNull String buildHeaderValue_com_connection(@NonNull RtspProtoHeaderTypeConnection hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_connection(hdValue);
	}

	private static @NonNull String buildHeaderValue_announce_contbase(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeContBase hdValue
			) throws RtspLowInvalidRrException, RtspInvalidRequestException {
		if (messageType != RtspMessageType.ANNOUNCE) {
			throw new RtspInvalidRequestException("Content-Base header is only valid for ANNOUNCE requests");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_contbase(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contenc(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeContEnc hdValue
			) throws RtspLowInvalidRrException, RtspInvalidRequestException {
		allowOnlyAnnounceGetOrSetParameter("Content-Encoding", messageType);
		return RtspLowBuilderHelper.helperBuildHeaderValue_contenc(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contlang(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeContLang hdValue
			) throws RtspLowInvalidRrException, RtspInvalidRequestException {
		allowOnlyAnnounceGetOrSetParameter("Content-Language", messageType);
		return RtspLowBuilderHelper.helperBuildHeaderValue_contlang(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contlen(@NonNull RtspMessageType messageType)
			throws RtspInvalidRequestException {
		allowOnlyAnnounceGetOrSetParameter("Content-Length", messageType);
		return "";  // add this header when adding the body
	}

	private static @NonNull String buildHeaderValue_com_conttype(@NonNull RtspMessageType messageType)
			throws RtspInvalidRequestException {
		allowOnlyAnnounceGetOrSetParameter("Content-Type", messageType);
		return "";  // add this header when adding the body
	}

	private static @NonNull String buildHeaderValue_com_cseq(@NonNull RtspProtoHeaderTypeCseq hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_cseq(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_date(@NonNull RtspProtoHeaderTypeDate hdValue) {
		return RtspLowBuilderHelper.helperBuildHeaderValue_date(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_keymgmt(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeKeymgmt hdValue
			) throws RtspInvalidRequestException {
		/*
		 * Example:
		 *   "prot=mikey; uri=\"rtsp://.../streamid00\"; data=\"[BASE64 ENCODED DATA]\""
		 */

		if (messageType != RtspMessageType.SET_PARAMETER && messageType != RtspMessageType.SETUP) {
			throw new RtspInvalidRequestException("Keymgmt header is only valid for SET_PARAMETER/SETUP requests");
		}
		if (hdValue.proto == RtspKeymgmtProto.NONE) {
			throw new RtspInvalidRequestException("Proto must be set");
		}
		if (hdValue.dataStr.isBlank()) {
			throw new RtspInvalidRequestException("dataStr cannot be blank");
		}
		if (hdValue.uriStr.isBlank()) {
			throw new RtspInvalidRequestException("uriStr cannot be blank");
		}
		return RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_PROT + hdValue.proto.name() + "; " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_URI + "\"" + hdValue.uriStr + "\"; " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_DATA + "\"" + hdValue.dataStr + "\"";
	}

	private static @NonNull String buildHeaderValue_com_proxyrequ(@NonNull RtspProtoHeaderTypeProxyRequ hdValue)
			throws RtspInvalidRequestException {
		return buildRequiredFeatures(hdValue.requiredFeatures);
	}

	private static @NonNull String buildHeaderValue_play_range(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeRange hdValue
			) throws RtspLowInvalidRrException, RtspInvalidRequestException {
		if (messageType != RtspMessageType.PLAY) {
			throw new RtspInvalidRequestException("Range header is only valid for PLAY requests");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_range(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_require(@NonNull RtspProtoHeaderTypeRequire hdValue)
			throws RtspInvalidRequestException {
		return buildRequiredFeatures(hdValue.requiredFeatures);
	}

	private static @NonNull String buildHeaderValue_com_session(@NonNull RtspProtoHeaderTypeSession hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_session(hdValue, true);
	}

	private static @NonNull String buildHeaderValue_setup_transport(
				@NonNull RtspMessageType messageType,
				@NonNull RtspProtoHeaderTypeTransport hdValue
			) throws RtspLowInvalidRrException, RtspInvalidRequestException {
		if (messageType != RtspMessageType.SETUP) {
			throw new RtspInvalidRequestException("Transport header is only valid for SETUP requests");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_transport(hdValue, true);
	}

	private static @NonNull String buildHeaderValue_com_useragent(@NonNull RtspProtoHeaderTypeUa hdValue)
			throws RtspInvalidRequestException {
		if (hdValue.userAgentStr.isBlank()) {
			throw new RtspInvalidRequestException("userAgentStr cannot be blank");
		}
		return hdValue.userAgentStr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void allowOnlyAnnounceGetOrSetParameter(@NonNull String hdDesc, @NonNull RtspMessageType messageType)
			throws RtspInvalidRequestException {
		if (messageType != RtspMessageType.ANNOUNCE &&
				messageType != RtspMessageType.GET_PARAMETER && messageType != RtspMessageType.SET_PARAMETER) {
			throw new RtspInvalidRequestException(hdDesc + " header is only valid for " +
					"ANNOUNCE/GET_PARAMETER/SET_PARAMETER requests");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildRequiredFeatures(@NonNull Set<@NonNull String> requiredFeatures)
			throws RtspInvalidRequestException {
		/*
		 * Example:
		 *   "Require: funky-feature" // "Proxy-Require: funky-feature"
		 * This header may be accompanied by a custom header like:
		 *   "Funky-Parameter: funkystuff"
		 * See
		 *   https://datatracker.ietf.org/doc/html/rfc2326#section-12.32
		 */

		if (requiredFeatures.isEmpty()) {
			throw new RtspInvalidRequestException("requiredFeatures cannot be empty");
		}
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String tmpEntry : requiredFeatures) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append(tmpEntry);
			isFirst = false;
		}
		return sb.toString();
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void buildBody(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoLowMsgRaw output
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildBody()";

		RtspProtoHeaderTypeContType outpHdValueContTp = new RtspProtoHeaderTypeContType();

		switch (input.messageType) {
			case RtspMessageType.ANNOUNCE:
				if (input.bodyAnnounceSdp.isEmpty()) {
					throw new RtspInvalidRequestException(FNC_NAME + ": bodyAnnounceSdp cannot be empty");
				}
				output.body = String.join(RtspProtoLowMsgConstants.CRLF, input.bodyAnnounceSdp);
				if (! output.body.endsWith(RtspProtoLowMsgConstants.CRLF)) {
					output.body += RtspProtoLowMsgConstants.CRLF;
				}
				outpHdValueContTp.contentType = RtspMimeType.SDP;
				break;
			case RtspMessageType.GET_PARAMETER:
				if (! input.bodyGetParamKeys.isEmpty()) {
					StringBuilder sb = new StringBuilder();
					for (String entry : input.bodyGetParamKeys) {
						sb.append(entry).append(RtspProtoLowMsgConstants.CRLF);
					}
					output.body = sb.toString();
					outpHdValueContTp.contentType = RtspMimeType.PARAMETERS;
				}
				break;
			case RtspMessageType.SET_PARAMETER:
				if (! input.bodySetParamKv.isEmpty()) {
					StringBuilder sb = new StringBuilder();
					for (Map.Entry<@NonNull String, @NonNull String> entry : input.bodySetParamKv.entrySet()) {
						sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(RtspProtoLowMsgConstants.CRLF);
					}
					output.body = sb.toString();
					outpHdValueContTp.contentType = RtspMimeType.PARAMETERS;
				}
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
				throw new RtspInvalidRequestException(FNC_NAME + ": body Content-Type: " + e.getMessage());
			}
			output.headerLines.add(RtspHeaderKey.CONTENT_TYPE.getStrValue() + ": " + tmpHdVal);
		}
		// Content-Length
		{
			RtspProtoHeaderTypeContLen hdValue = new RtspProtoHeaderTypeContLen();
			try {
				hdValue.setContentLen32bit(output.body.length());
			} catch (RtspNumberRangeException e) {
				throw new RtspInvalidRequestException(FNC_NAME + ": body length out of range: " + e.getMessage());
			}
			String tmpHdVal;
			try {
				tmpHdVal = RtspLowBuilderHelper.helperBuildHeaderValue_contlen(hdValue);
			} catch (RtspLowInvalidRrException e) {
				throw new RtspInvalidRequestException(e.getMessage());
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

package org.tsitle.lib_xrtxp.rtsp.lowlevel.request;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.exceptions.ProUriInvalidUriException;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.common.types.ProUri;
import org.tsitle.lib_xrtxp.rtsp.enums.RtspProtoMessageType;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoInvalidRequestException;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.lib_xrtxp.rtsp.highlevel.msg.header.*;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.*;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.helper.RtspLowBuilderHelper;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.helper.RtspLowInvalidRrException;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.lib_xrtxp.rtsp.lowlevel.msg.RtspProtoLowMsgRaw;

import java.util.Map;
import java.util.Set;

public final class RtspProtoLowRequestProducer {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowRequestProducer(@NonNull LogMsgInterface logMsgInterface) {
		this.logMsgInterface = logMsgInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoLowMsgRaw buildMessage(@NonNull RtspProtoHighMsgStructuredRequest input) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildMessage()";

		if (input.rtspProtoVersion == RtspProtocolVersion.NONE) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": rtspProtoVersion cannot be NONE");
		}

		RtspProtoLowMsgRaw resObj = new RtspProtoLowMsgRaw();

		// set request line
		resObj.mainLine = String.format("%s %s %s",
				input.messageType.name(), buildResourceUrlForRequestLine(input.resourceUrl), input.rtspProtoVersion.getStrValue());

		// set headers
		try {
			buildAllHeaders(input, resObj);
		} catch (RtspLowInvalidRrException e) {
			throw new RtspProtoInvalidRequestException(FNC_NAME + ": " + e.getMessage());
		}

		// set body
		if (input.headers.containsKey(RtspHeaderKey.CONTENT_TYPE)) {
			buildBody(input, resObj);
		}

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildResourceUrlForRequestLine(@NonNull String inputUrl) throws RtspProtoInvalidRequestException {
		ProUri tmpInpProUri;
		try {
			tmpInpProUri = ProUri.of(inputUrl);
		} catch (ProUriInvalidUriException e) {
			throw new RtspProtoInvalidRequestException("Invalid URL '" + inputUrl + "': " + e.getMessage());
		}
		boolean isRtsps = (tmpInpProUri.getScheme().orElse(ProUri.Scheme.NONE) == ProUri.Scheme.RTSPS);
		if (tmpInpProUri.getScheme().orElse(ProUri.Scheme.NONE) != ProUri.Scheme.RTSP && ! isRtsps) {
			throw new RtspProtoInvalidRequestException("Invalid protocol in URL '" + inputUrl + "'");
		}
		int tmpPort = tmpInpProUri.getPortIfPresent().orElse(-1);

		// rewrite the URL to get rid of any fragments and userinfo (username + password)
		ProUri tmpOutpProUri;
		try {
			tmpOutpProUri = ProUri.of(
					tmpInpProUri.getScheme().orElseThrow(),
					"",
					"",
					tmpInpProUri.getHost().orElseThrow(),
					tmpPort,
					tmpInpProUri.getPath().orElse(""),
					tmpInpProUri.getQuery().orElse("")
				);
		} catch (ProUriInvalidUriException e) {
			throw new RtspProtoInvalidRequestException("Invalid URL '" + inputUrl + "': " + e.getMessage());
		}
		return tmpOutpProUri.getUriString().orElseThrow();
	}

	private void buildAllHeaders(
				@NonNull RtspProtoHighMsgStructuredRequest input,
				@NonNull RtspProtoLowMsgRaw output
			) throws RtspProtoInvalidRequestException, RtspLowInvalidRrException {
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
					default -> throw new RtspProtoInvalidRequestException(FNC_NAME + ": Unknown header key: " + entry.getKey());
				};
			if (tmpHdVal.isBlank()) {
				continue;
			}
			output.headerLines.add(entry.getKey().getStrValue() + ": " + tmpHdVal);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHeaderValue_describe_accept(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderTypeAccept hdValue
			) throws RtspProtoInvalidRequestException {
		if (messageType != RtspProtoMessageType.DESCRIBE) {
			throw new RtspProtoInvalidRequestException("Accept header is only valid for DESCRIBE requests");
		}
		if (hdValue.rtspMimeType == RtspMimeType.NONE) {
			throw new RtspProtoInvalidRequestException("MIME-Type must be set");
		}
		return hdValue.rtspMimeType.getStrValue();
	}

	private static @NonNull String buildHeaderValue_com_auth_client(@NonNull RtspProtoHeaderTypeAuthClient hdValue)
			throws RtspProtoInvalidRequestException {
		/*
		 * Example:
		 *   "Authorization: Digest username=\"admin\", realm=\"Abcdef Some\", nonce=\"xxx\", uri=\"rtsp://xxx:88/videoMain\", response=\"xxx\""
		 *   or
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"...\", response=\"...\", algorithm=\"MD5\""
		 */

		if (hdValue.authUser.isBlank()) {
			throw new RtspProtoInvalidRequestException("Auth Username cannot be blank");
		}
		if (hdValue.authRealm.isBlank()) {
			throw new RtspProtoInvalidRequestException("Auth Realm cannot be blank");
		}
		if (hdValue.authNonce.isBlank()) {
			throw new RtspProtoInvalidRequestException("Auth Nonce cannot be blank");
		}
		if (hdValue.authResp.isBlank()) {
			throw new RtspProtoInvalidRequestException("Auth Response cannot be blank");
		}
		if (hdValue.authUri.isBlank()) {
			throw new RtspProtoInvalidRequestException("Auth URI cannot be blank");
		}
		String tmpAlgoStr = RtspAuthAlgo.MD5.getStrValue();
		return RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_VAL_XXX_AUTH_DIGEST_PREFIX +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_USER + "\"" + hdValue.authUser + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_REALM + "\"" + hdValue.authRealm + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_NONCE + "\"" + hdValue.authNonce + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_RESP + "\"" + hdValue.authResp + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_URI + "\"" + hdValue.authUri + "\", " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_AUTH_ALGO + "\"" + tmpAlgoStr + "\"";
	}

	private static @NonNull String buildHeaderValue_com_connection(@NonNull RtspProtoHeaderTypeConnection hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_connection(hdValue);
	}

	private static @NonNull String buildHeaderValue_announce_contbase(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderTypeContBase hdValue
			) throws RtspLowInvalidRrException, RtspProtoInvalidRequestException {
		if (messageType != RtspProtoMessageType.ANNOUNCE) {
			throw new RtspProtoInvalidRequestException("Content-Base header is only valid for ANNOUNCE requests");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_contbase(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contenc(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderTypeContEnc hdValue
			) throws RtspLowInvalidRrException, RtspProtoInvalidRequestException {
		allowOnlyAnnounceGetOrSetParameter("Content-Encoding", messageType);
		return RtspLowBuilderHelper.helperBuildHeaderValue_contenc(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contlang(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderTypeContLang hdValue
			) throws RtspLowInvalidRrException, RtspProtoInvalidRequestException {
		allowOnlyAnnounceGetOrSetParameter("Content-Language", messageType);
		return RtspLowBuilderHelper.helperBuildHeaderValue_contlang(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contlen(@NonNull RtspProtoMessageType messageType)
			throws RtspProtoInvalidRequestException {
		allowOnlyAnnounceGetOrSetParameter("Content-Length", messageType);
		return "";  // add this header when adding the body
	}

	private static @NonNull String buildHeaderValue_com_conttype(@NonNull RtspProtoMessageType messageType)
			throws RtspProtoInvalidRequestException {
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
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderTypeKeymgmt hdValue
			) throws RtspProtoInvalidRequestException {
		/*
		 * Example:
		 *   "prot=mikey; uri=\"rtsp://.../streamid00\"; data=\"[BASE64 ENCODED DATA]\""
		 */

		if (messageType != RtspProtoMessageType.SET_PARAMETER && messageType != RtspProtoMessageType.SETUP) {
			throw new RtspProtoInvalidRequestException("Keymgmt header is only valid for SET_PARAMETER/SETUP requests");
		}
		if (hdValue.proto == RtspKeymgmtProto.NONE) {
			throw new RtspProtoInvalidRequestException("Proto must be set");
		}
		if (hdValue.dataStr.isBlank()) {
			throw new RtspProtoInvalidRequestException("dataStr cannot be blank");
		}
		if (hdValue.uriStr.isBlank()) {
			throw new RtspProtoInvalidRequestException("uriStr cannot be blank");
		}
		return RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_PROT + hdValue.proto.name() + "; " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_URI + "\"" + hdValue.uriStr + "\"; " +
				RtspProtoLowMsgConstants.RTSP_RR_HEADER_PARAM_KEY_XXX_KM_DATA + "\"" + hdValue.dataStr + "\"";
	}

	private static @NonNull String buildHeaderValue_com_proxyrequ(@NonNull RtspProtoHeaderTypeProxyRequ hdValue)
			throws RtspProtoInvalidRequestException {
		return buildRequiredFeatures(hdValue.requiredFeatures);
	}

	private static @NonNull String buildHeaderValue_play_range(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderTypeRange hdValue
			) throws RtspLowInvalidRrException, RtspProtoInvalidRequestException {
		if (messageType != RtspProtoMessageType.PLAY) {
			throw new RtspProtoInvalidRequestException("Range header is only valid for PLAY requests");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_range(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_require(@NonNull RtspProtoHeaderTypeRequire hdValue)
			throws RtspProtoInvalidRequestException {
		return buildRequiredFeatures(hdValue.requiredFeatures);
	}

	private static @NonNull String buildHeaderValue_com_session(@NonNull RtspProtoHeaderTypeSession hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_session(hdValue, true);
	}

	private static @NonNull String buildHeaderValue_setup_transport(
				@NonNull RtspProtoMessageType messageType,
				@NonNull RtspProtoHeaderTypeTransport hdValue
			) throws RtspLowInvalidRrException, RtspProtoInvalidRequestException {
		if (messageType != RtspProtoMessageType.SETUP) {
			throw new RtspProtoInvalidRequestException("Transport header is only valid for SETUP requests");
		}
		return RtspLowBuilderHelper.helperBuildHeaderValue_transport(hdValue, true);
	}

	private static @NonNull String buildHeaderValue_com_useragent(@NonNull RtspProtoHeaderTypeUa hdValue)
			throws RtspProtoInvalidRequestException {
		if (hdValue.userAgentStr.isBlank()) {
			throw new RtspProtoInvalidRequestException("userAgentStr cannot be blank");
		}
		return hdValue.userAgentStr;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static void allowOnlyAnnounceGetOrSetParameter(@NonNull String hdDesc, @NonNull RtspProtoMessageType messageType)
			throws RtspProtoInvalidRequestException {
		if (messageType != RtspProtoMessageType.ANNOUNCE &&
				messageType != RtspProtoMessageType.GET_PARAMETER && messageType != RtspProtoMessageType.SET_PARAMETER) {
			throw new RtspProtoInvalidRequestException(hdDesc + " header is only valid for " +
					"ANNOUNCE/GET_PARAMETER/SET_PARAMETER requests");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildRequiredFeatures(@NonNull Set<@NonNull String> requiredFeatures)
			throws RtspProtoInvalidRequestException {
		/*
		 * Example:
		 *   "Require: funky-feature" // "Proxy-Require: funky-feature"
		 * This header may be accompanied by a custom header like:
		 *   "Funky-Parameter: funkystuff"
		 * See
		 *   https://datatracker.ietf.org/doc/html/rfc2326#section-12.32
		 */

		if (requiredFeatures.isEmpty()) {
			throw new RtspProtoInvalidRequestException("requiredFeatures cannot be empty");
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
			) throws RtspProtoInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildBody()";

		RtspProtoHeaderTypeContType outpHdValueContTp = new RtspProtoHeaderTypeContType();

		switch (input.messageType) {
			case RtspProtoMessageType.ANNOUNCE:
				if (input.bodyAnnounceSdp.isSdpLinesAllRawEmpty()) {
					throw new RtspProtoInvalidRequestException(FNC_NAME + ": bodyAnnounceSdp cannot be empty");
				}
				output.body = String.join(RtspProtoLowMsgConstants.CRLF, input.bodyAnnounceSdp.getSdpLinesAllRaw());
				if (! output.body.endsWith(RtspProtoLowMsgConstants.CRLF)) {
					output.body += RtspProtoLowMsgConstants.CRLF;
				}
				outpHdValueContTp.contentType = RtspMimeType.SDP;
				break;
			case RtspProtoMessageType.GET_PARAMETER:
				if (! input.bodyGetParamNames.isParamNamesEmpty()) {
					StringBuilder sb = new StringBuilder();
					for (String entry : input.bodyGetParamNames.getParamNames()) {
						sb.append(entry).append(RtspProtoLowMsgConstants.CRLF);
					}
					output.body = sb.toString();
					outpHdValueContTp.contentType = RtspMimeType.PARAMETERS;
				}
				break;
			case RtspProtoMessageType.SET_PARAMETER:
				if (! input.bodySetParamKv.isParamKvsEmpty()) {
					StringBuilder sb = new StringBuilder();
					for (Map.Entry<@NonNull String, @NonNull String> entry : input.bodySetParamKv.getParamKvsEntrySet()) {
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
				throw new RtspProtoInvalidRequestException(FNC_NAME + ": body Content-Type: " + e.getMessage());
			}
			output.headerLines.add(RtspHeaderKey.CONTENT_TYPE.getStrValue() + ": " + tmpHdVal);
		}
		// Content-Length
		{
			RtspProtoHeaderTypeContLen hdValue = new RtspProtoHeaderTypeContLen();
			try {
				hdValue.contentLen.setLen32bit(Integer.toUnsignedLong(output.body.length()));
			} catch (RtspProtoNumberRangeException e) {
				throw new RtspProtoInvalidRequestException(FNC_NAME + ": body length out of range: " + e.getMessage());
			}
			String tmpHdVal;
			try {
				tmpHdVal = RtspLowBuilderHelper.helperBuildHeaderValue_contlen(hdValue);
			} catch (RtspLowInvalidRrException e) {
				throw new RtspProtoInvalidRequestException(e.getMessage());
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

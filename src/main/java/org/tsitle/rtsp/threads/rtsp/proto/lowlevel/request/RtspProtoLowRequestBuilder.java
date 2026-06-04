package org.tsitle.rtsp.threads.rtsp.proto.lowlevel.request;

import org.jspecify.annotations.NonNull;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper.RtspLowBuilderHelper;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.helper.RtspLowInvalidRrException;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgConstants;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.msg.RtspProtoLowMsgRaw;

import java.net.URI;
import java.util.Map;

public final class RtspProtoLowRequestBuilder {

	private final @NonNull LogMsgInterface logMsgInterface;

	public RtspProtoLowRequestBuilder(@NonNull LogMsgInterface logMsgInterface) {
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
		if (! input.body.isBlank()) {
			if (! input.headers.containsKey(RtspHeaderKey.CONTENT_TYPE)) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Have a msg body but no Content-Type header");
			}
			if (! input.headers.containsKey(RtspHeaderKey.CONTENT_LEN)) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Have a msg body but no Content-Length header");
			}
			resObj.body = input.body;
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
					case ACCEPT -> buildHeaderValue_describe_accept(entry.getValue().hdValAccept);
					case AUTH_CLIENT -> buildHeaderValue_com_auth_client(entry.getValue().hdValAuthClient);
					case CONTENT_BASE -> buildHeaderValue_describe_contbase(entry.getValue().hdValContBase);
					case CONTENT_LEN -> buildHeaderValue_com_contlen(entry.getValue().hdValContLen);
					case CONTENT_TYPE -> buildHeaderValue_com_conttype(entry.getValue().hdValContType);
					case CSEQ -> buildHeaderValue_com_cseq(entry.getValue().hdValCseq);
					case DATE -> buildHeaderValue_com_date(entry.getValue().hdValDate);
					case KEYMGMT -> buildHeaderValue_com_keymgmt(entry.getValue().hdValKeymgmt);
					case RANGE -> buildHeaderValue_play_range(entry.getValue().hdValRange);
					case REQUIRE -> buildHeaderValue_options_require(entry.getValue().hdValRequire);
					case SESSION -> buildHeaderValue_com_session(entry.getValue().hdValSession);
					case TRANSPORT -> buildHeaderValue_setup_transport(entry.getValue().hdValTransport);
					case USERAGENT -> buildHeaderValue_com_useragent(entry.getValue().hdValUserAgent);
					default -> throw new RtspInvalidRequestException(FNC_NAME + ": Unknown header key: " + entry.getKey());
				};
			output.headerLines.add(entry.getKey().getStrValue() + ": " + tmpHdVal);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------

	private static @NonNull String buildHeaderValue_describe_accept(@NonNull RtspProtoHeaderTypeAccept hdValue)
			throws RtspInvalidRequestException {
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

	private static @NonNull String buildHeaderValue_describe_contbase(@NonNull RtspProtoHeaderTypeContBase hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_contbase(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_contlen(@NonNull RtspProtoHeaderTypeContLen hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_contlen(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_conttype(@NonNull RtspProtoHeaderTypeContType hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_conttype(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_cseq(@NonNull RtspProtoHeaderTypeCseq hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_cseq(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_date(@NonNull RtspProtoHeaderTypeDate hdValue) {
		return RtspLowBuilderHelper.helperBuildHeaderValue_date(hdValue);
	}

	private static @NonNull String buildHeaderValue_com_keymgmt(@NonNull RtspProtoHeaderTypeKeymgmt hdValue)
			throws RtspInvalidRequestException {
		/*
		 * Example:
		 *   "prot=mikey; uri=\"rtsp://.../streamid00\"; data=\"[BASE64 ENCODED DATA]\""
		 */

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

	private static @NonNull String buildHeaderValue_play_range(@NonNull RtspProtoHeaderTypeRange hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_range(hdValue);
	}

	private static @NonNull String buildHeaderValue_options_require(
				@NonNull RtspProtoHeaderTypeRequire hdValue
			) throws RtspInvalidRequestException {
		/*
		 * Example:
		 *   "Require: funky-feature"
		 * This header may be accompanied by a custom header like:
		 *   "Funky-Parameter: funkystuff"
		 * See
		 *   https://datatracker.ietf.org/doc/html/rfc2326#section-12.32
		 */

		if (hdValue.requiredFeatures.isEmpty()) {
			throw new RtspInvalidRequestException("requiredFeatures cannot be empty");
		}
		StringBuilder sb = new StringBuilder();
		boolean isFirst = true;
		for (String tmpEntry : hdValue.requiredFeatures) {
			if (! isFirst) {
				sb.append(", ");
			}
			sb.append(tmpEntry);
			isFirst = false;
		}
		return sb.toString();
	}

	private static @NonNull String buildHeaderValue_com_session(@NonNull RtspProtoHeaderTypeSession hdValue)
			throws RtspLowInvalidRrException {
		return RtspLowBuilderHelper.helperBuildHeaderValue_session(hdValue, true);
	}

	private static @NonNull String buildHeaderValue_setup_transport(@NonNull RtspProtoHeaderTypeTransport hdValue)
			throws RtspLowInvalidRrException {
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

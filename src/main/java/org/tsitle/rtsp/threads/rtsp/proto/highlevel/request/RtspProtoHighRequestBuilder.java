package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.security.MikeyGenerator;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.RtspSessionInfo;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoAuthDigest;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RtspProtoHighRequestBuilder {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;

	public RtspProtoHighRequestBuilder(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull RtspSessionInfo rtspSessionInfo
			) {
		this.logMsgInterface = logMsgInterface;
		this.rtspSessionInfo = rtspSessionInfo;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredRequest buildRequest(
				@NonNull RtspMessageType requestMessageType,
				@NonNull String resourceUrl,
				@Nullable String subStreamId,
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@Nullable Set<String> getParameterNames,
				@Nullable Map<@NonNull String, @NonNull String> setParameterKvs
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest()";

		RtspProtoHighMsgStructuredRequest resObj = new RtspProtoHighMsgStructuredRequest();

		resObj.rtspProtoVersion = rtspSessionInfo.lastRequestRtspProtoVersion;
		resObj.statusCode = RtspStatusCode.OK;
		resObj.messageType = requestMessageType;

		//
		resObj.resourceUrl = resourceUrl;

		//
		addCommonHeaders(resObj);

		//
		switch (requestMessageType) {
			case ANNOUNCE -> buildRequest_announce(kmdsOutbound, resObj);
			case DESCRIBE -> buildRequest_describe(resObj);
			case GET_PARAMETER -> buildRequest_getParameter(getParameterNames, resObj);
			case OPTIONS -> buildRequest_options(resObj);
			case PAUSE -> buildRequest_pause(resObj);
			case PLAY -> buildRequest_play(resObj);
			case RECORD -> buildRequest_record(resObj);
			case REDIRECT -> buildRequest_redirect(resObj);
			case SET_PARAMETER -> buildRequest_setParameter(kmdsOutbound, subStreamId, setParameterKvs, resObj);
			case SETUP -> buildRequest_setup(resObj);
			case TEARDOWN -> buildRequest_teardown(resObj);
			default -> throw new RtspInvalidRequestException(FNC_NAME + ": Unsupported message type: " +
					requestMessageType);
		}

		System.out.println(">>>>>>>>> >>>>>>>>> " + resObj);  // @TODO

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildRequest_announce(
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_announce()";

		/*
		 * Example:
		 *   "ANNOUNCE rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "Content-Type: application/sdp"
		 *   "Content-Length: 1234"
		 *   ...
		 *   ""
		 *   "v=0"
		 *   "a=tool:TS RTSP Server/1.0"
		 *   ...
		 */

		// @TODO build complete SDP with optional KMDs if SRTxP encryption is enabled

		// Content-Type (we don't add the Content-Length - this will be done by the low-level request builder)
		addContentTypeHeader(output);

		logError(FNC_NAME, "ANNOUNCE is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": ANNOUNCE is not supported yet");

		/*
		 * Re-keying legacy SDES key:
		 * we need to send an ANNOUNCE request that contains the entire SDP.
		 * Only the 'a=crypto' line must change and use a different tag.
		 * The initial SDP would contain something like 'a=crypto:1 ...' and the new SDP
		 * would contain something like 'a=crypto:2 ...'.
		 */
		/*
		if (! kmd.isForLegacySdes()) {
			throw new IllegalArgumentException(FNC_NAME + ": SrtxpKmd must be for legacy SDES key management");
		}
		// legacy SDES key management (SDP Security Descriptions RFC-4568)
		String tmpCryptoStrB64 = kmd.getMasterKeyAndSaltAsBase64();
		*/
	}

	private void buildRequest_describe(@NonNull RtspProtoHighMsgStructuredRequest output) {
		// @TODO add example
		// nothing to do
	}

	private void buildRequest_getParameter(
				@Nullable Set<String> parameterNames,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspInvalidRequestException {
		/*
		 * Example:
		 *   "GET_PARAMETER rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "Content-Type: text/parameters"
		 *   "Content-Length: 1234"
		 *   ...
		 *   ""
		 *   "packets_received"
		 *   "jitter"
		 */

		if (parameterNames == null || parameterNames.isEmpty()) {
			return;
		}
		output.bodyGetParamKeys.addAll(parameterNames);
		// Content-Type (we don't add the Content-Length - this will be done by the low-level request builder)
		addContentTypeHeader(output);
	}

	private void buildRequest_options(@NonNull RtspProtoHighMsgStructuredRequest output) {
		// @TODO add example
		// nothing to do
	}

	private void buildRequest_pause(@NonNull RtspProtoHighMsgStructuredRequest output) {
		// @TODO add example
		// nothing to do
	}

	/**
	 * The client makes one PLAY request per Input Source
	 */
	private void buildRequest_play(@NonNull RtspProtoHighMsgStructuredRequest output) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_play()";

		// @TODO add example
		// @TODO set some headers

		logError(FNC_NAME, "PLAY is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": PLAY is not supported yet");
	}

	private void buildRequest_record(@NonNull RtspProtoHighMsgStructuredRequest output) {
		// @TODO add example
		// nothing to do
	}

	private void buildRequest_redirect(@NonNull RtspProtoHighMsgStructuredRequest output) {
		// @TODO add example
		// nothing to do
	}

	private void buildRequest_setParameter(
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@Nullable String subStreamId,
				@Nullable Map<@NonNull String, @NonNull String> parameterKvs,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_setParameter()";

		/*
		 * Example:
		 *   "SET_PARAMETER rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "Content-Type: text/parameters"
		 *   "Content-Length: 1234"
		 *   ...
		 *   ""
		 *   "packets_received: 100.0"
		 *   "jitter: 13.8"
		 */

		//
		if (parameterKvs != null && ! parameterKvs.isEmpty()) {
			output.bodySetParamKv.putAll(parameterKvs);
			// Content-Type (we don't add the Content-Length - this will be done by the low-level request builder)
			addContentTypeHeader(output);
		}

		// set the SRTxP Key Management Data for a SET_PARAMETER request used for re-keying
		if (kmdsOutbound == null) {
			return;
		}
		if (subStreamId == null) {
			throw new IllegalArgumentException(FNC_NAME + ": subStreamId must not be null when " +
					"setting SRTxP key management data for SET_PARAMETER request");
		}
		Optional<SrtxpKmd> tmpOptKmd = kmdsOutbound.getKmdForSubStream(subStreamId);
		if (tmpOptKmd.isEmpty()) {
			throw new IllegalArgumentException(FNC_NAME + ": KMD for Sub-Stream not found");
		}
		SrtxpKmd tmpKmd = tmpOptKmd.get();
		if (tmpKmd.isForLegacySdes()) {
			throw new IllegalArgumentException(FNC_NAME + ": KMD must not be for legacy SDES key management");
		}
		String tmpCryptoStrB64;
		try {
			// modern MIKEY key management
			tmpCryptoStrB64 = MikeyGenerator.generate(tmpKmd);
		} catch (SrtxpSecurityException e) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Could not generate MIKEY message: " + e.getMessage());
		}
		RtspProtoHeaderEntryRequest entry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.KEYMGMT);
		entry.hdValKeymgmt.proto = RtspKeymgmtProto.MIKEY;
		entry.hdValKeymgmt.uriStr = output.resourceUrl;
		entry.hdValKeymgmt.dataStr = tmpCryptoStrB64;
		output.headers.put(RtspHeaderKey.KEYMGMT, entry);
	}

	/**
	 * The client makes one SETUP request per Stream Source (aka Sub-Stream).<br />
	 * See <a href="https://datatracker.ietf.org/doc/html/rfc7826">RFC-7826: Real Time Streaming Protocol 2.0</a>
	 * or <a href="https://datatracker.ietf.org/doc/html/rfc2326">RFC-2326: Real Time Streaming Protocol 1.0</a>
	 */
	private void buildRequest_setup(@NonNull RtspProtoHighMsgStructuredRequest output) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_setup()";

		// @TODO add example
		// @TODO check if we need and have KMD

		// @TODO set some headers
		logError(FNC_NAME, "SETUP is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": SETUP is not supported yet");
	}

	private void buildRequest_teardown(@NonNull RtspProtoHighMsgStructuredRequest output) {
		// @TODO add example
		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void addCommonHeaders(@NonNull RtspProtoHighMsgStructuredRequest output) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addCommonHeaders()";

		// CSeq
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			try {
				hdEntry.hdValCseq.setCseqNr32bit(++rtspSessionInfo.seqNr_respRem_expected);
			} catch (RtspNumberRangeException e) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Setting CSeq failed: " + e.getMessage());
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Date
		{
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.DATE);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Session
		if (! rtspSessionInfo.rtspSessionId.isBlank()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.SESSION);
			hdEntry.hdValSession.sessionIdStr = rtspSessionInfo.rtspSessionId;
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Auth(Client)
		if (! rtspSessionInfo.authInfo.authNonceServer.isBlank()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.AUTH_CLIENT);
			hdEntry.hdValAuthClient.authUri = output.resourceUrl;
			hdEntry.hdValAuthClient.authRealm = rtspSessionInfo.authInfo.authRealmClient;
			hdEntry.hdValAuthClient.authNonce = rtspSessionInfo.authInfo.authNonceServer;
			try {
				hdEntry.hdValAuthClient.authResp = RtspProtoAuthDigest.computeAuthResponse(
						rtspSessionInfo.authInfo.authUser,
						rtspSessionInfo.authInfo.authPlainPassword,
						hdEntry.hdValAuthClient.authUri,
						output.messageType,
						hdEntry.hdValAuthClient.authRealm,
						hdEntry.hdValAuthClient.authNonce
					);
			} catch (IllegalArgumentException e) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Computing authentication response failed: " +
						e.getMessage());
			}
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
	}

	private void addContentTypeHeader(@NonNull RtspProtoHighMsgStructuredRequest output) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentTypeHeader()";

		if (output.messageType != RtspMessageType.ANNOUNCE &&
				output.messageType != RtspMessageType.GET_PARAMETER && output.messageType != RtspMessageType.SET_PARAMETER) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Content-Type header only allowed for " +
					"DESCRIBE/GET_PARAMETER/SET_PARAMETER messages");
		}
		RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_TYPE);
		hdEntry.hdValContType.contentType = (output.messageType == RtspMessageType.ANNOUNCE ?
				RtspMimeType.SDP : RtspMimeType.PARAMETERS);
		output.headers.put(hdEntry.getHdKey(), hdEntry);
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}

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
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataRequest;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;

import java.util.Optional;

public final class RtspProtoHighRequestProducer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull RtspSessionInfo rtspSessionInfo;

	public RtspProtoHighRequestProducer(
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
				@NonNull RtspProtoDataRequest inputDataRequ,
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest()";

		inputDataRequ.writeProtect();

		//
		RtspProtoHighMsgStructuredRequest resObj = new RtspProtoHighMsgStructuredRequest();

		resObj.rtspProtoVersion = rtspSessionInfo.rtspProtoVersionToUse;
		resObj.statusCode = RtspStatusCode.OK;
		resObj.messageType = requestMessageType;

		//
		resObj.resourceUrl = resourceUrl;

		//
		addCommonHeaders(inputDataRequ, resObj);

		//
		switch (requestMessageType) {
			case ANNOUNCE -> buildRequest_announce(inputDataRequ, kmdsOutbound, resObj);
			case DESCRIBE -> buildRequest_describe(resObj);
			case GET_PARAMETER -> buildRequest_getParameter(inputDataRequ, resObj);
			case OPTIONS -> buildRequest_options(resObj);
			case PAUSE -> buildRequest_pause(resObj);
			case PLAY -> buildRequest_play(resObj);
			case RECORD -> buildRequest_record(resObj);
			case REDIRECT -> buildRequest_redirect(resObj);
			case SET_PARAMETER -> buildRequest_setParameter(inputDataRequ, kmdsOutbound, subStreamId, resObj);
			case SETUP -> buildRequest_setup(resObj);
			case TEARDOWN -> buildRequest_teardown();
			default -> throw new RtspInvalidRequestException(FNC_NAME + ": Unsupported message type: " +
					requestMessageType);
		}

		//System.out.println(">>>>>>>>> >>>>>>>>> " + resObj);

		return resObj;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void buildRequest_announce(
				@NonNull RtspProtoDataRequest inputDataRequ,
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

		if (inputDataRequ.requAnnouncedSdp.getContentBase().isBlank()) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Content-Base is required for ANNOUNCE");
		}
		if (inputDataRequ.requAnnouncedSdp.isSdpLinesAllRawEmpty()) {
			throw new RtspInvalidRequestException(FNC_NAME + ": SDP content is required for ANNOUNCE");
		}

		// @TODO build complete SDP with optional KMDs if SRTxP encryption is enabled
		// @TODO fetch Content-Language from SDP-Producer

		// Content-Type (we don't add the Content-Length - this will be done by the low-level request builder)
		addContentTypeHeader(output);
		// Content-Language
		addContentLangHeader(inputDataRequ.requAnnouncedSdp.getContentLang(), output);

		//
		output.bodyAnnounceSdp.copyFrom(inputDataRequ.requAnnouncedSdp);

		logError(FNC_NAME, "ANNOUNCE is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": ANNOUNCE is not supported yet");

		/*
		 * Re-keying legacy SDES key: @TODO
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
		/*
		 * Example:
		 *   "DESCRIBE rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "CSeq: 4"
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"rtsp://example.com/fizzle/foo\", response=\"...\""
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 *   "Accept: application/sdp"
		 */

		// nothing to do
	}

	private void buildRequest_getParameter(
				@NonNull RtspProtoDataRequest inputDataRequ,
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

		if (inputDataRequ.requGetParamNames.isParamNamesEmpty()) {
			return;
		}
		output.bodyGetParamNames.copyFrom(inputDataRequ.requGetParamNames);
		// Content-Type (we don't add the Content-Length - this will be done by the low-level request builder)
		addContentTypeHeader(output);
	}

	private void buildRequest_options(@NonNull RtspProtoHighMsgStructuredRequest output) {
		/*
		 * Example:
		 *   "OPTIONS rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "CSeq: 2"
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 */

		// nothing to do
	}

	private void buildRequest_pause(@NonNull RtspProtoHighMsgStructuredRequest output) {
		/*
		 * Example:
		 *   "PAUSE rtsp://example.com/fizzle/foo/ RTSP/1.0"
		 *   "CSeq: 96"
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"rtsp://example.com/fizzle/foo/\", response=\"...\""
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 *   "Session: 126437FE"
		 */

		// nothing to do
	}

	/**
	 * The client makes one PLAY request per Input Source
	 */
	private void buildRequest_play(@NonNull RtspProtoHighMsgStructuredRequest output) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest_play()";

		/*
		 * Example:
		 *   "PLAY rtsp://example.com/fizzle/foo/ RTSP/1.0"
		 *   "CSeq: 7"
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"rtsp://example.com/fizzle/foo/\", response=\"...\""
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 *   "Session: 126437FE"
		 *   "Range: npt=0.000-"
		 */

		// @TODO set some headers

		logError(FNC_NAME, "PLAY is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": PLAY is not supported yet");
	}

	private void buildRequest_record(@NonNull RtspProtoHighMsgStructuredRequest output) {
		// nothing to do
	}

	private void buildRequest_redirect(@NonNull RtspProtoHighMsgStructuredRequest output) {
		// nothing to do
	}

	private void buildRequest_setParameter(
				@NonNull RtspProtoDataRequest inputDataRequ,
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound,
				@Nullable String subStreamId,
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
		if (! inputDataRequ.requSetParamValues.isParamKvsEmpty()) {
			output.bodySetParamKv.copyFrom(inputDataRequ.requSetParamValues);
			// Content-Type (we don't add the Content-Length - this will be done by the low-level request builder)
			addContentTypeHeader(output);
			// Content-Language
			addContentLangHeader(inputDataRequ.requSetParamValues.getContentLang(), output);
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

		/*
		 * Example:
		 *   "SETUP rtsp://example.com/fizzle/foo/substreamidf528764d_93b6207a RTSP/1.0"
		 *   "CSeq: 6"
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"rtsp://example.com/fizzle/foo/\", response=\"...\""
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 *   "Transport: RTP/SAVP;unicast;client_port=43704-43705"
		 *   "Session: 126437FE"
		 *   "KeyMgmt: prot=mikey; uri=\"rtsp://example.com/fizzle/foo/substreamidf528764d_93b6207a\"; data=\"...\""
		 */

		// @TODO check if we need and have KMD

		// @TODO set some headers
		logError(FNC_NAME, "SETUP is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": SETUP is not supported yet");
	}

	private void buildRequest_teardown() {
		/*
		 * Example:
		 *   "TEARDOWN rtsp://example.com/fizzle/foo/ RTSP/1.0"
		 *   "CSeq: 8"
		 *   "Authorization: Digest username=\"...\", realm=\"...\", nonce=\"...\", uri=\"rtsp://example.com/fizzle/foo/\", response=\"...\""
		 *   "User-Agent: LibVLC/3.0.23 (LIVE555 Streaming Media v2020.11.05)"
		 */

		// nothing to do
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void addCommonHeaders(
				@NonNull RtspProtoDataRequest inputDataRequ,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspInvalidRequestException {
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
			hdEntry.hdValSession.idSession.copyFrom(inputDataRequ.requIdSession);
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Auth(Client)
		if (! inputDataRequ.requAuthClient.getAuthNonce().isBlank()) {
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.AUTH_CLIENT);
			hdEntry.hdValAuthClient.authUri = output.resourceUrl;
			hdEntry.hdValAuthClient.authRealm = inputDataRequ.requAuthClient.getAuthRealm();
			hdEntry.hdValAuthClient.authNonce = inputDataRequ.requAuthClient.getAuthNonce();
			try {
				hdEntry.hdValAuthClient.authResp = RtspProtoAuthDigest.computeAuthResponse(
						inputDataRequ.requAuthClient.getAuthUser(),
						inputDataRequ.requAuthClient.getAuthPlainPassword(),
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

	private void addContentLangHeader(
				@NonNull String contLang,
				@NonNull RtspProtoHighMsgStructuredRequest output
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".addContentLangHeader()";

		if (output.messageType != RtspMessageType.ANNOUNCE && output.messageType != RtspMessageType.SET_PARAMETER) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Content-Language header only allowed for " +
					"ANNOUNCE/SET_PARAMETER messages");
		}
		if (contLang.isBlank()) {
			return;
		}
		RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_LANG);
		hdEntry.hdValContLang.contentLangStr = contLang;
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

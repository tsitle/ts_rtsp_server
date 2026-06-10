package org.tsitle.rtsp.threads.rtsp.proto.highlevel.request;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.rtsp.exceptions.SrtxpSecurityException;
import org.tsitle.rtsp.security.MikeyGenerator;
import org.tsitle.rtsp.security.SrtxpKmd;
import org.tsitle.rtsp.threads.LogMsgInterface;
import org.tsitle.rtsp.threads.logging.RtxpLogLevel;
import org.tsitle.rtsp.threads.rtsp.proto.RtspProtoAuthDigest;
import org.tsitle.rtsp.threads.rtsp.proto.data_rr.RtspProtoDataRequest;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspInvalidRequestException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspNumberRangeException;
import org.tsitle.rtsp.threads.rtsp.proto.exceptions.RtspSdpException;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.RtspProtoHighMsgStructuredRequest;
import org.tsitle.rtsp.threads.rtsp.proto.highlevel.msg.header.RtspProtoHeaderEntryRequest;
import org.tsitle.rtsp.threads.rtsp.proto.lowlevel.*;
import org.tsitle.rtsp.threads.rtsp.proto.sdp.SdpProducerInterface;

import java.util.Optional;

public final class RtspProtoHighRequestProducer {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final boolean cfgIsDebugPrintRtspSdpSent;
	private final @NonNull SdpProducerInterface sdpProducerInterface;

	public RtspProtoHighRequestProducer(
				@NonNull LogMsgInterface logMsgInterface,
				boolean cfgIsDebugPrintRtspSdpSent,
				@NonNull SdpProducerInterface sdpProducerInterface
			) {
		this.logMsgInterface = logMsgInterface;
		this.cfgIsDebugPrintRtspSdpSent = cfgIsDebugPrintRtspSdpSent;
		this.sdpProducerInterface = sdpProducerInterface;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public @NonNull RtspProtoHighMsgStructuredRequest buildRequest(
				@NonNull RtspMessageType requestMessageType,
				@Nullable String subStreamId,
				@NonNull RtspProtoDataRequest inputDataRequ,
				@Nullable RtspKeymgmtKmdsOutbound kmdsOutbound
			) throws RtspInvalidRequestException {
		final String FNC_NAME = getClass().getSimpleName() + ".buildRequest()";

		if (inputDataRequ.getResourceUrl().isBlank()) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Resource URL must be set");
		}

		inputDataRequ.writeProtect();

		//
		RtspProtoHighMsgStructuredRequest resObj = new RtspProtoHighMsgStructuredRequest();

		resObj.rtspProtoVersion = inputDataRequ.getRtspProtoVersionToUse();
		resObj.statusCode = RtspStatusCode.OK;
		resObj.messageType = requestMessageType;

		//
		resObj.resourceUrl = inputDataRequ.getResourceUrl();

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

		try {
			sdpProducerInterface.buildUpdatedSdpForAnnounce(
					inputDataRequ.getIdInputSource(),
					inputDataRequ.getServerIpFromRscUrl(),
					kmdsOutbound,
					output.bodyAnnounceSdp
				);
		} catch (RtspSdpException e) {
			throw new RtspInvalidRequestException(FNC_NAME + ": Building SDP failed: " + e.getMessage());
		}

		if (cfgIsDebugPrintRtspSdpSent) {
			logDebug(FNC_NAME, "-------- SDP:");
			for (String tmpSingleSdpLine : output.bodyAnnounceSdp.getSdpLinesAllRaw()) {
				logDebug(FNC_NAME, "---------------- " + tmpSingleSdpLine);
			}
		}

		// Content-Base
		{
			String tmpRscUrl = inputDataRequ.getResourceUrl();
			if (tmpRscUrl.isBlank()) {
				throw new RtspInvalidRequestException(FNC_NAME + ": Resource URL must be set");
			}
			//
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CONTENT_BASE);
			hdEntry.hdValContBase.contentBaseStr = tmpRscUrl + (tmpRscUrl.endsWith("/") ? "" : "/");
			output.headers.put(hdEntry.getHdKey(), hdEntry);
		}
		// Content-Type (we don't add the Content-Length - this will be done by the low-level response builder)
		addContentTypeHeader(output);
		// Content-Language
		addContentLangHeader(output.bodyAnnounceSdp.getContentLang(), output);
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
		 *   ...
		 *   "Range: npt=0.000-"
		 */

		// @TODO set some headers

		logError(FNC_NAME, "PLAY is not supported yet");
		throw new RtspInvalidRequestException(FNC_NAME + ": PLAY is not supported yet");
	}

	private void buildRequest_redirect(@NonNull RtspProtoHighMsgStructuredRequest output) {
		/*
		 * A redirect request informs the client that it must connect to another
		 * server location. It contains the mandatory header Location, which
		 * indicates that the client should issue requests for that URL. It may
		 * contain the parameter Range, which indicates when the redirection
		 * takes effect. If the client wants to continue to send or receive
		 * media for this URI, the client MUST issue a TEARDOWN request for the
		 * current session and a SETUP for the new session at the designated
		 * host.
		 *
		 * This example request redirects traffic for this URI to the new server
		 * at the given playtime:
		 *   "REDIRECT rtsp://example.com/fizzle/foo RTSP/1.0"
		 *   "CSeq: 732"
		 *   "Location: rtsp://bigserver.com:8001"
		 *   "Range: clock=19960213T143205Z-"
		 */

		// @TODO set some headers
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
			if (inputDataRequ.getCseqNrToSend() < 1L) {
				throw new RtspInvalidRequestException(FNC_NAME + ": CSeq to send must be >= 1");
			}
			RtspProtoHeaderEntryRequest hdEntry = new RtspProtoHeaderEntryRequest(RtspHeaderKey.CSEQ);
			try {
				hdEntry.hdValCseq.setCseqNr32bit(inputDataRequ.getCseqNrToSend());
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
		if (! inputDataRequ.requIdSession.isEmpty()) {
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

	private void logDebug(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.DEBUG, fncName, msg);
	}
	private void logError(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.ERROR, fncName, msg);
	}
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(),
				fncName + ": " + msg);
	}

}
